package com.sysudate.api.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sysudate.api.identity.AccountService.AccountView;
import com.sysudate.api.identity.AccountService.AuthResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerContractTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void loginKeepsTheFrontendEnvelope() throws Exception {
        when(accountService.login(any()))
            .thenReturn(new AuthResult(
                "access-token",
                new AccountView(
                    42,
                    "student@sysu.edu.cn",
                    "student",
                    List.of("SYSU-550E8400-E29B-41D4-A716-446655440000"),
                    true,
                    true,
                    null,
                    null)));

        mvc.perform(post("/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"login":"student","password":"correct-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.user.account_id").value(42))
            .andExpect(jsonPath("$.user.serials[0]").value(
                "SYSU-550E8400-E29B-41D4-A716-446655440000"));
    }

    @Test
    void loginValidationKeepsAUsableErrorEnvelope() throws Exception {
        mvc.perform(post("/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"login":"","password":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").isNotEmpty());
    }

    @Test
    void registrationReturnsAnEmptySerialList() throws Exception {
        when(accountService.register(any()))
            .thenReturn(new AuthResult(
                "new-token",
                new AccountView(
                    7,
                    "new@sysu.edu.cn",
                    "new-user",
                    List.of(),
                    true,
                    true,
                    null,
                    null)));

        mvc.perform(post("/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email":"new@sysu.edu.cn",
                      "code":"123456",
                      "password":"correct-password",
                      "username":"new-user"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.token").value("new-token"))
            .andExpect(jsonPath("$.user.serials").isEmpty());
    }
}
