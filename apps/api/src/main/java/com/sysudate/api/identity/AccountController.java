package com.sysudate.api.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.identity.AccountService.AccountView;
import com.sysudate.api.identity.AccountService.AuthResult;
import com.sysudate.api.identity.AccountService.BindCommand;
import com.sysudate.api.identity.AccountService.ChangePasswordCommand;
import com.sysudate.api.identity.AccountService.LoginCommand;
import com.sysudate.api.identity.AccountService.RegisterCommand;
import com.sysudate.api.identity.AccountService.ResetPasswordCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final ObjectProvider<AccountService> accountService;

    public AccountController(
        ObjectProvider<AccountService> accountService
    ) {
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
        @Valid @RequestBody LoginRequest request
    ) {
        return authResponse(accounts().login(
            new LoginCommand(request.login(), request.password())));
    }

    @PostMapping("/register")
    public Map<String, Object> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        return authResponse(accounts().register(new RegisterCommand(
            request.email(),
            request.code(),
            request.password(),
            request.username())));
    }

    @PostMapping("/bind")
    public Map<String, Object> bind(
        @Valid @RequestBody BindRequest request
    ) {
        return authResponse(accounts().bind(new BindCommand(
            request.serialNumber(),
            request.email(),
            request.code(),
            request.password(),
            request.username())));
    }

    @GetMapping("/me")
    public Map<String, Object> me(
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        return Map.of(
            "code", 200,
            "user", frontendUser(accounts().current(require(principal))));
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        accounts().changePassword(
            require(principal),
            new ChangePasswordCommand(
                request.oldPassword(),
                request.newPassword()));
        return success("密码修改成功，请重新登录");
    }

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        accounts().resetPassword(new ResetPasswordCommand(
            request.email(),
            request.code(),
            request.newPassword()));
        return success("密码重置成功");
    }

    @PostMapping("/change-username")
    public Map<String, Object> changeUsername(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody ChangeUsernameRequest request
    ) {
        AuthResult result = accounts().changeUsername(
            require(principal),
            request.newUsername());
        return Map.of(
            "code", 200,
            "msg", "用户名修改成功",
            "token", result.token(),
            "username", result.user().username());
    }

    @PostMapping("/change-contact-email")
    public Map<String, Object> changeContactEmail(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody ContactEmailRequest request
    ) {
        accounts().changeContactEmail(
            require(principal),
            request.contactEmail());
        return success("联系邮箱修改成功");
    }

    @PostMapping("/delete")
    public Map<String, Object> delete(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody PasswordRequest request
    ) {
        accounts().delete(require(principal), request.password());
        return success("账号已注销");
    }

    @GetMapping("/check-username")
    public Map<String, Object> checkUsername(
        @RequestParam String username
    ) {
        boolean available = accounts().usernameAvailable(username);
        return Map.of(
            "code", 200,
            "available", available,
            "msg", available ? "用户名可用" : "用户名不可用");
    }

    @PostMapping("/toggle-matching")
    public Map<String, Object> toggleMatching(
        @AuthenticationPrincipal CurrentAccount principal,
        @RequestBody Map<String, Object> body
    ) {
        boolean value = Boolean.TRUE.equals(body.get("join_matching"));
        accounts().setPreference(
            require(principal),
            "join_matching",
            value);
        return Map.of(
            "code", 200,
            "msg", value ? "已加入恋爱匹配" : "已退出恋爱匹配",
            "join_matching", value);
    }

    @PostMapping("/toggle-friend-matching")
    public Map<String, Object> toggleFriendMatching(
        @AuthenticationPrincipal CurrentAccount principal,
        @RequestBody Map<String, Object> body
    ) {
        boolean value = Boolean.TRUE.equals(
            body.get("join_friend_matching"));
        accounts().setPreference(
            require(principal),
            "join_friend_matching",
            value);
        return Map.of(
            "code", 200,
            "msg", value ? "已加入交友匹配" : "已退出交友匹配",
            "join_friend_matching", value);
    }

    private Map<String, Object> authResponse(AuthResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("msg", "操作成功");
        response.put("token", result.token());
        response.put("user", frontendUser(result.user()));
        return response;
    }

    private Map<String, Object> frontendUser(AccountView account) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("account_id", account.accountId());
        user.put("email", account.email());
        user.put("username", account.username());
        user.put("serials", account.serials());
        user.put("join_matching", account.joinMatching());
        user.put("join_friend_matching", account.joinFriendMatching());
        user.put("contact_email", account.contactEmail());
        user.put("created_at", account.createdAt());
        return user;
    }

    private Map<String, Object> success(String message) {
        return Map.of("code", 200, "msg", message);
    }

    private AccountService accounts() {
        AccountService service = accountService.getIfAvailable();
        if (service == null) {
            throw new LegacyApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                503,
                "账号服务暂不可用");
        }
        return service;
    }

    private CurrentAccount require(CurrentAccount principal) {
        if (principal == null) {
            throw new LegacyApiException(
                HttpStatus.UNAUTHORIZED,
                401,
                "未登录或登录已过期");
        }
        return principal;
    }

    public record LoginRequest(
        @NotBlank String login,
        @NotBlank String password
    ) {
    }

    public record RegisterRequest(
        @NotBlank String email,
        @NotBlank String code,
        @NotBlank String password,
        String username
    ) {
    }

    public record BindRequest(
        @JsonProperty("serial_number") @NotBlank String serialNumber,
        @NotBlank String email,
        @NotBlank String code,
        @NotBlank String password,
        String username
    ) {
    }

    public record ChangePasswordRequest(
        @JsonProperty("old_password") @NotBlank String oldPassword,
        @JsonProperty("new_password") @NotBlank String newPassword
    ) {
    }

    public record ResetPasswordRequest(
        @NotBlank String email,
        @NotBlank String code,
        @JsonProperty("new_password") @NotBlank String newPassword
    ) {
    }

    public record ChangeUsernameRequest(
        @JsonProperty("new_username") @NotBlank String newUsername
    ) {
    }

    public record ContactEmailRequest(
        @JsonProperty("contact_email") @NotBlank String contactEmail
    ) {
    }

    public record PasswordRequest(@NotBlank String password) {
    }
}
