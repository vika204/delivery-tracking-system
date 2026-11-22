package com.delivery.webui.controller;

import com.delivery.webui.client.AuthUserClient;
import com.delivery.webui.client.dto.UserResponse;
import com.delivery.webui.client.dto.UserRole;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ErrorPageRenderingTest.FailureConfiguration.class)
class ErrorPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @MockitoBean
    private AuthUserClient authClient;

    @ParameterizedTest
    @CsvSource({"CLIENT, 404", "CLIENT, 500", "COURIER, 404", "COURIER, 500"})
    void realHttpErrorsKeepTheAuthenticatedHeader(UserRole role, int errorStatus) throws Exception {
        when(authClient.login(any())).thenReturn(new UserResponse(
                42L, "Test", "User", "test@example.test", "+380000000001", role));
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(3))
                .build()) {
            HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"test@example.test\",\"password\":\"test-password\"}"))
                    .build();
            assertThat(client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(cookies.getCookieStore().getCookies()).isNotEmpty();

            String path = errorStatus == 404 ? "/missing-header-page" : "/test-header-failure";
            HttpRequest error = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "text/html")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = client.send(error, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(errorStatus);
            assertThat(response.body()).contains(errorStatus == 404 ? "PAGE NOT FOUND" : "INTERNAL SERVER ERROR")
                    .contains("data-logout-button")
                    .contains(role == UserRole.CLIENT ? "href=\"/profile\"" : "href=\"/courier/profile\"")
                    .doesNotContain("href=\"/login\"", "href=\"/register\"", "test-only internal failure");
        }
    }

    @TestConfiguration
    static class FailureConfiguration {

        @Bean(name = "/test-header-failure")
        org.springframework.web.servlet.mvc.Controller failureController() {
            return (request, response) -> {
                throw new IllegalStateException("test-only internal failure");
            };
        }
    }

    @Test
    void rendersNotFoundPageThroughSpringBootErrorHandling() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(
                                RequestDispatcher.ERROR_REQUEST_URI,
                                "/missing-page"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("PAGE NOT FOUND")))
                .andExpect(content().string(containsString(
                        "src=\"/videos/error-courier.webm\""
                )))
                .andExpect(content().string(not(containsString(
                        "BACK TO HOME"
                ))))
                .andExpect(content().string(not(containsString(
                        "TRACK A PACKAGE"
                ))));
    }

    @Test
    void bootJsonErrorHidesExceptionAndMessageEvenWhenRequested() throws Exception {
        mockMvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                        .param("trace", "true").param("message", "true")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/failed-page")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "private-internal-message")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("private-exception")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(content().string(not(containsString("private-internal-message"))))
                .andExpect(content().string(not(containsString("private-exception"))));
    }

    @Test
    void rendersServerErrorWithoutExposingInternalDetails() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(
                                RequestDispatcher.ERROR_REQUEST_URI,
                                "/failed-page"
                        )
                        .requestAttr(
                                RequestDispatcher.ERROR_MESSAGE,
                                "sensitive internal details"
                        ))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString(
                        "INTERNAL SERVER ERROR"
                )))
                .andExpect(content().string(not(containsString(
                        "BACK TO HOME"
                ))))
                .andExpect(content().string(not(containsString(
                        "TRACK A PACKAGE"
                ))))
                .andExpect(content().string(not(containsString(
                        "sensitive internal details"
                ))));
    }
}
