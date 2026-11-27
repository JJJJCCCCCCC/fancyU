package com.sysudate.api.identity;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.common.security.JwtService;
import com.sysudate.api.identity.AccountRepository.Account;
import com.sysudate.api.identity.AccountRepository.Preferences;
import com.sysudate.api.identity.AccountRepository.SurveyBinding;
import com.sysudate.api.identity.verification.VerificationCodeStore;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty({
    "spring.datasource.url",
    "spring.data.redis.host"
})
public class AccountService {
    private static final Set<String> ALLOWED_EMAIL_DOMAINS = Set.of(
        "mail2.sysu.edu.cn",
        "sysu.edu.cn",
        "mail2.scut.edu.cn",
        "scut.edu.cn",
        "jnu.edu.cn",
        "m.scnu.edu.cn",
        "stu.scau.edu.cn",
        "gzhmu.edu.cn",
        "gzucm.edu.cn",
        "mail2.gdut.edu.cn",
        "gdut.edu.cn",
        "oamail.gdufs.edu.cn",
        "e.gzhu.edu.cn",
        "stu.gdpu.edu.cn",
        "xhcom.edu.cn",
        "mail2.gzarts.edu.cn",
        "gzarts.edu.cn");
    private static final Pattern USERNAME =
        Pattern.compile("^[\\p{L}\\p{N}_]{2,20}$");

    private final AccountRepository accounts;
    private final VerificationCodeStore verificationCodes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(
        AccountRepository accounts,
        VerificationCodeStore verificationCodes,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.accounts = accounts;
        this.verificationCodes = verificationCodes;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(RegisterCommand command) {
        String email = normalizeAndValidateEmail(command.email());
        validatePassword(command.password());
        consumeCode(email, command.code());
        if (accounts.findByLogin(email).isPresent()) {
            throw conflict("该邮箱已注册");
        }
        String username = command.username() == null
            || command.username().isBlank()
            ? email.substring(0, email.indexOf('@'))
            : command.username().trim();
        validateUsername(username);
        if (accounts.usernameExists(username)) {
            throw conflict("该用户名已被使用");
        }
        try {
            Account account = accounts.create(
                email,
                username,
                passwordEncoder.encode(command.password()));
            accounts.updateLastLogin(account.id());
            return authenticated(account);
        } catch (DuplicateKeyException exception) {
            throw conflict("邮箱或用户名已被使用");
        }
    }

    @Transactional
    public AuthResult bind(BindCommand command) {
        String email = normalizeAndValidateEmail(command.email());
        validatePassword(command.password());
        consumeCode(email, command.code());
        SurveyBinding binding = accounts.findSurveyBinding(
            command.serialNumber()).orElseThrow(() -> new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "序列号不存在"));
        if (!email.equalsIgnoreCase(binding.email())
            && !email.equalsIgnoreCase(binding.contactEmail())) {
            throw new LegacyApiException(
                HttpStatus.FORBIDDEN,
                403,
                "序列号与邮箱不匹配");
        }
        if (binding.accountId() != null) {
            throw conflict("该序列号已绑定账号");
        }
        if (accounts.findByLogin(email).isPresent()) {
            throw conflict("该邮箱已注册，请直接登录");
        }
        String username = command.username() == null
            || command.username().isBlank()
            ? email.substring(0, email.indexOf('@'))
            : command.username().trim();
        validateUsername(username);
        if (accounts.usernameExists(username)) {
            throw conflict("该用户名已被使用");
        }
        Account account = accounts.create(
            email,
            username,
            passwordEncoder.encode(command.password()));
        accounts.bindSurvey(account.id(), binding);
        accounts.updateLastLogin(account.id());
        return authenticated(account);
    }

    public AuthResult login(LoginCommand command) {
        Account account = accounts.findByLogin(command.login().trim())
            .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(
            command.password(),
            account.passwordHash())) {
            throw invalidCredentials();
        }
        accounts.updateLastLogin(account.id());
        return authenticated(account);
    }

    public AccountView current(CurrentAccount principal) {
        Account account = requireAccount(principal.accountId());
        return view(account);
    }

    @Transactional
    public void changePassword(
        CurrentAccount principal,
        ChangePasswordCommand command
    ) {
        validatePassword(command.newPassword());
        Account account = requireAccount(principal.accountId());
        if (!passwordEncoder.matches(
            command.oldPassword(),
            account.passwordHash())) {
            throw badRequest("旧密码错误");
        }
        accounts.updatePasswordAndRevokeTokens(
            account.id(),
            passwordEncoder.encode(command.newPassword()));
    }

    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        String email = normalizeAndValidateEmail(command.email());
        validatePassword(command.newPassword());
        consumeCode(email, command.code());
        Account account = accounts.findByLogin(email)
            .orElseThrow(() -> new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "该邮箱未注册"));
        accounts.updatePasswordAndRevokeTokens(
            account.id(),
            passwordEncoder.encode(command.newPassword()));
    }

    @Transactional
    public AuthResult changeUsername(
        CurrentAccount principal,
        String username
    ) {
        String normalized = username == null ? "" : username.trim();
        validateUsername(normalized);
        Account account = requireAccount(principal.accountId());
        if (!normalized.equals(account.username())
            && accounts.usernameExists(normalized)) {
            throw conflict("该用户名已被使用");
        }
        accounts.updateUsername(account.id(), normalized);
        Account updated = requireAccount(account.id());
        return authenticated(updated);
    }

    @Transactional
    public void changeContactEmail(
        CurrentAccount principal,
        String contactEmail
    ) {
        if (contactEmail == null
            || !contactEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw badRequest("邮箱格式不正确");
        }
        accounts.updateContactEmail(
            principal.accountId(),
            contactEmail.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional
    public void delete(CurrentAccount principal, String password) {
        Account account = requireAccount(principal.accountId());
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw badRequest("密码错误");
        }
        accounts.deleteAccount(account.id());
    }

    public boolean usernameAvailable(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME.matcher(normalized).matches()) {
            return false;
        }
        return !accounts.usernameExists(normalized);
    }

    public Preferences setPreference(
        CurrentAccount principal,
        String field,
        boolean value
    ) {
        accounts.setPreference(principal.accountId(), field, value);
        return accounts.preferences(principal.accountId());
    }

    private AuthResult authenticated(Account account) {
        return new AuthResult(jwtService.issue(account), view(account));
    }

    private AccountView view(Account account) {
        Preferences preferences = accounts.preferences(account.id());
        return new AccountView(
            account.id(),
            account.email(),
            account.username(),
            accounts.serials(account.id()),
            preferences.joinMatching(),
            preferences.joinFriendMatching(),
            accounts.contactEmail(account.id()),
            account.createdAt());
    }

    private Account requireAccount(int accountId) {
        return accounts.findById(accountId).orElseThrow(
            () -> new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "账号不存在"));
    }

    public static String normalizeAndValidateEmail(String rawEmail) {
        String email = rawEmail == null
            ? ""
            : rawEmail.trim().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        if (at < 1 || !ALLOWED_EMAIL_DOMAINS.contains(
            email.substring(at + 1))) {
            throw new LegacyApiException(
                HttpStatus.BAD_REQUEST,
                400,
                "仅支持指定学校官方邮箱");
        }
        return email;
    }

    private void consumeCode(String email, String code) {
        if (!verificationCodes.consume(email, code)) {
            throw badRequest("验证码错误或已过期");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw badRequest("密码长度至少为 8 位");
        }
    }

    private void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw badRequest("用户名需为 2-20 位字母、数字、下划线或中文");
        }
    }

    private LegacyApiException invalidCredentials() {
        return new LegacyApiException(
            HttpStatus.UNAUTHORIZED,
            401,
            "账号或密码错误");
    }

    private LegacyApiException badRequest(String message) {
        return new LegacyApiException(
            HttpStatus.BAD_REQUEST,
            400,
            message);
    }

    private LegacyApiException conflict(String message) {
        return new LegacyApiException(
            HttpStatus.CONFLICT,
            400,
            message);
    }

    public record LoginCommand(String login, String password) {
    }

    public record RegisterCommand(
        String email,
        String code,
        String password,
        String username
    ) {
    }

    public record BindCommand(
        String serialNumber,
        String email,
        String code,
        String password,
        String username
    ) {
    }

    public record ChangePasswordCommand(
        String oldPassword,
        String newPassword
    ) {
    }

    public record ResetPasswordCommand(
        String email,
        String code,
        String newPassword
    ) {
    }

    public record AuthResult(String token, AccountView user) {
    }

    public record AccountView(
        int accountId,
        String email,
        String username,
        List<String> serials,
        boolean joinMatching,
        boolean joinFriendMatching,
        String contactEmail,
        Instant createdAt
    ) {
    }
}
