package com.taskmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanager.dto.LoginRequest;
import com.taskmanager.dto.RegisterRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.exception.DuplicateAccountException;
import com.taskmanager.exception.InvalidCredentialsException;
import com.taskmanager.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registrationReturnsCreatedWithoutLocationOrPasswordData() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "correct horse battery"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void registrationRejectsInvalidInputsBeforeCallingService() throws Exception {
        for (String requestBody : new String[] {
            """
            {"username":"ab","email":"alice@example.com","password":"correct horse"}
            """,
            """
            {"username":"alice","email":"not-an-email","password":"correct horse"}
            """,
            """
            {"username":"alice","email":"alice@example.com","password":"short"}
            """,
            """
            {"username":"alice","email":"alice@example.com","password":"%s"}
            """.formatted("é".repeat(37))
        }) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        verifyNoInteractions(authService);
    }

    @Test
    void registrationAcceptsEightCharactersAndSeventyTwoUtf8Bytes() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"12345678"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"%s"}
                                """.formatted("é".repeat(36))))
                .andExpect(status().isCreated());

        org.mockito.Mockito.verify(authService, org.mockito.Mockito.times(2))
                .register(any(RegisterRequest.class));
    }

    @Test
    void registrationAcceptsUsernameAndEmailAtMaximumLengths() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(1L, "u".repeat(50), "a".repeat(64) + "@"
                        + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61)));
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"12345678"}
                                """.formatted("u".repeat(50), email)))
                .andExpect(status().isCreated());
    }

    @Test
    void loginReturnsOkAndNeverIncludesCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"correct horse"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void loginValidatesInputBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":" ","password":"password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"%s"}
                                """.formatted("é".repeat(37))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void duplicateUsernameReturnsConflictWithSafeFieldError() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateAccountException("username"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"correct horse"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username is already taken."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("This username is already taken."));
    }

    @Test
    void duplicateEmailReturnsConflictWithSafeFieldError() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateAccountException("email"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"correct horse"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void missingAndIncorrectCredentialsHaveIdenticalUnauthorizedResponses() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        String missingUserResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing","password":"incorrect password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String incorrectPasswordResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"incorrect password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(missingUserResponse)
                .isEqualTo(incorrectPasswordResponse)
                .doesNotContain("incorrect password");
    }

    @Test
    void unclassifiedDatabaseIntegrityFailureIsNotReportedAsDuplicateOrLeaked() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DataIntegrityViolationException("internal-password-hash-detail"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"correct horse"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal-password-hash-detail");
    }
}
