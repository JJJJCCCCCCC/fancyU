package com.sysudate.api.admin;

import com.sysudate.api.admin.AdminRepository.UserPage;
import com.sysudate.api.admin.AdminRepository.UserQuery;
import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    private final ObjectProvider<AdminService> adminService;

    public AdminController(ObjectProvider<AdminService> adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/admin/login")
    public Map<String, Object> login(
        @Valid @RequestBody LoginRequest request
    ) {
        return Map.of(
            "code", 200,
            "msg", "登录成功",
            "token", service().login(
                request.username(),
                request.password()));
    }

    @GetMapping("/admin/users")
    public Map<String, Object> users(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(defaultValue = "created_at") String sort,
        @RequestParam(defaultValue = "DESC") String order,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String sex,
        @RequestParam(required = false) String campus,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) Boolean join_matching
    ) {
        UserPage result = service().users(new UserQuery(
            Math.max(1, page),
            Math.clamp(pageSize, 1, 100),
            sort,
            order,
            search,
            sex,
            campus,
            grade,
            join_matching));
        return Map.of(
            "code", 200,
            "data", result.rows(),
            "total", result.total(),
            "page", result.page(),
            "pageSize", result.pageSize());
    }

    @GetMapping("/admin/user/{serial}")
    public Map<String, Object> user(@PathVariable String serial) {
        return Map.of("code", 200, "data", service().user(serial));
    }

    @GetMapping("/admin/stats")
    public Map<String, Object> statistics() {
        return Map.of("code", 200, "data", service().statistics());
    }

    @DeleteMapping("/admin/user/{serial}")
    public Map<String, Object> deleteUser(
        @AuthenticationPrincipal CurrentAccount administrator,
        @PathVariable String serial
    ) {
        service().deleteSurvey(requireAdmin(administrator), serial);
        return Map.of("code", 200, "msg", "已删除");
    }

    @GetMapping("/admin/chat/list")
    public Map<String, Object> chatList() {
        return Map.of("code", 200, "data", service().chatList());
    }

    @GetMapping("/admin/chat/{id}")
    public Map<String, Object> chat(@PathVariable String id) {
        return Map.of("code", 200, "data", service().chat(id));
    }

    @PostMapping("/admin/chat/save")
    public Map<String, Object> saveChat(
        @Valid @RequestBody SaveChatRequest request
    ) {
        service().saveChat(
            request.id(),
            request.title(),
            request.messages());
        return Map.of("code", 200, "msg", "保存成功");
    }

    @DeleteMapping("/admin/chat/{id}")
    public Map<String, Object> deleteChat(@PathVariable String id) {
        service().deleteChat(id);
        return Map.of("code", 200, "msg", "已删除");
    }

    private AdminService service() {
        AdminService service = adminService.getIfAvailable();
        if (service == null) {
            throw new LegacyApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                503,
                "管理服务暂不可用");
        }
        return service;
    }

    private CurrentAccount requireAdmin(CurrentAccount principal) {
        if (principal == null || !"ADMIN".equals(principal.role())) {
            throw new LegacyApiException(
                HttpStatus.FORBIDDEN,
                403,
                "无管理员权限");
        }
        return principal;
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {
    }

    public record SaveChatRequest(
        @NotBlank String id,
        String title,
        @NotNull List<Map<String, Object>> messages
    ) {
    }
}
