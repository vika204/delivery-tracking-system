package com.delivery.auth.controller;

import com.delivery.auth.entity.User;
import com.delivery.auth.entity.UserRole;
import com.delivery.auth.exception.UserExceptionHandler;
import com.delivery.auth.repository.UserRepository;
import com.delivery.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UserProfileTest {

    private static final String UPDATE = """
            {"firstName":"Vika","lastName":"Roman","email":"vika@example.com","phone":"+380971234567"}
            """;
    private final UserRepository repository = mock(UserRepository.class);
    private final MockMvc mvc = standaloneSetup(new UserController(new UserService(repository)))
            .setControllerAdvice(new UserExceptionHandler()).build();

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void registersClientAndCourierWithoutReturningPassword(UserRole role) throws Exception {
        when(repository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            saved.setUserId(42L);
            return saved;
        });

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com",
                         "phone":"+380671234567","password":"test-password","role":"%s"}
                        """.formatted(role.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(new BCryptPasswordEncoder().matches("test-password", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsRemovedAdminRoleBeforeSavingUser() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com",
                         "phone":"+380671234567","password":"test-password","role":"ADMIN"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateEmailStillReturnsConflict() throws Exception {
        when(repository.existsByEmailIgnoreCase("olena@example.com")).thenReturn(true);

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Olena","lastName":"Kovalenko","email":"olena@example.com",
                         "phone":"+380671234567","password":"test-password","role":"CLIENT"}
                        """))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void logsInWithCorrectPasswordWithoutReturningHash() throws Exception {
        User user = user();
        user.setPasswordHash(new BCryptPasswordEncoder().encode("test-password"));
        when(repository.findByEmailIgnoreCase("olena@example.com")).thenReturn(Optional.of(user));

        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"olena@example.com","password":"test-password"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsWrongPasswordAndUnknownEmail(boolean existingUser) throws Exception {
        if (existingUser) {
            User user = user();
            user.setPasswordHash(new BCryptPasswordEncoder().encode("test-password"));
            when(repository.findByEmailIgnoreCase("olena@example.com")).thenReturn(Optional.of(user));
        }

        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"olena@example.com","password":"wrong-password"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsOnlyProfileFieldsForUserFromHeader() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.of(user()));

        mvc.perform(get("/users/profile").header("X-User-Id", "42").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Olena"))
                .andExpect(jsonPath("$.email").value("olena@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
        verify(repository).findById(42L);
    }

    @Test
    void savesProfileAndPreservesPasswordRoleAndIdentity() throws Exception {
        User user = user();
        when(repository.findById(42L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(put("/users/profile").header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Vika"))
                .andExpect(jsonPath("$.email").value("vika@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(repository).saveAndFlush(user);
        assertThat(user.getUserId()).isEqualTo(42);
        assertThat(user.getPasswordHash()).isEqualTo("unchanged-private-hash");
        assertThat(user.getRole()).isEqualTo(UserRole.CLIENT);
        assertThat(user.getPhone()).isEqualTo("+380971234567");

        mvc.perform(get("/users/profile").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Vika"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"firstName\":\" \",\"lastName\":\"Roman\",\"email\":\"vika@example.com\",\"phone\":\"+380971234567\"}",
            "{\"firstName\":\"Vika\",\"lastName\":\"Roman\",\"email\":\"wrong-email\",\"phone\":\"+380971234567\"}",
            "{\"firstName\":\"Vika\",\"lastName\":\"Roman\",\"email\":\"vika@example.com\",\"phone\":\"abc\"}",
            "{"
    })
    void invalidInputDoesNotTouchDatabase(String body) throws Exception {
        mvc.perform(put("/users/profile").header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingOrInvalidUserId() throws Exception {
        mvc.perform(get("/users/profile")).andExpect(status().isBadRequest());
        mvc.perform(get("/users/profile").header("X-User-Id", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void missingUserReturns404() throws Exception {
        mvc.perform(get("/users/profile").header("X-User-Id", "42"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/users/profile").header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isNotFound());
    }

    @Test
    void conflictingEmailReturns409WithoutDatabaseDetails() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.of(user()));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("private-sql-details"));

        mvc.perform(put("/users/profile").header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("email address")))
                .andExpect(content().string(not(containsString("private-sql-details"))));
    }

    private static User user() {
        return User.builder().userId(42L).firstName("Olena").lastName("Kovalenko")
                .email("olena@example.com").phone("+380671234567")
                .passwordHash("unchanged-private-hash").role(UserRole.CLIENT).build();
    }
}
