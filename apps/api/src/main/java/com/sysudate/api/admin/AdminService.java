package com.sysudate.api.admin;

import com.sysudate.api.admin.AdminRepository.UserPage;
import com.sysudate.api.admin.AdminRepository.UserQuery;
import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.common.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("spring.datasource.url")
public class AdminService {
    private final AdminRepository repository;
    private final JwtService jwtService;
    private final String configuredUsername;
    private final byte[] configuredPassword;

    public AdminService(
        AdminRepository repository,
        JwtService jwtService,
        @Value("${sysudate.admin.username}") String username,
        @Value("${sysudate.admin.password}") String password
    ) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.configuredUsername = username;
        this.configuredPassword =
            password.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String login(String username, String password) {
        boolean usernameMatches = MessageDigest.isEqual(
            configuredUsername.getBytes(StandardCharsets.UTF_8),
            value(username));
        boolean passwordMatches = MessageDigest.isEqual(
            configuredPassword,
            value(password));
        if (!usernameMatches || !passwordMatches) {
            throw new LegacyApiException(
                HttpStatus.UNAUTHORIZED,
                401,
                "用户名或密码错误");
        }
        return jwtService.issueAdmin(configuredUsername);
    }

    public UserPage users(UserQuery query) {
        return repository.users(query);
    }

    public Map<String, Object> user(String serialNumber) {
        return repository.user(serialNumber).orElseThrow(
            () -> new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "未找到该用户"));
    }

    public Map<String, Object> statistics() {
        return repository.statistics();
    }

    public void deleteSurvey(
        CurrentAccount administrator,
        String serialNumber
    ) {
        if (!repository.deleteSurvey(serialNumber)) {
            throw new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "未找到该用户");
        }
        repository.audit(
            administrator.username(),
            "DELETE_SURVEY",
            "SURVEY",
            serialNumber,
            requestId());
    }

    public List<Map<String, Object>> chatList() {
        return repository.chatList();
    }

    public Map<String, Object> chat(String id) {
        return repository.chat(id).orElseThrow(
            () -> new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "对话不存在"));
    }

    public void saveChat(
        String id,
        String title,
        List<Map<String, Object>> messages
    ) {
        String resolvedTitle = title;
        if (resolvedTitle == null || resolvedTitle.isBlank()) {
            resolvedTitle = messages.isEmpty()
                ? "新对话"
                : String.valueOf(messages.getFirst().getOrDefault(
                    "content",
                    "新对话"));
        }
        repository.saveChat(
            id,
            resolvedTitle.substring(
                0,
                Math.min(255, resolvedTitle.length())),
            messages);
    }

    public void deleteChat(String id) {
        repository.deleteChat(id);
    }

    private byte[] value(String input) {
        return (input == null ? "" : input)
            .getBytes(StandardCharsets.UTF_8);
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }
}
