package com.delivery.webui.controller;

import com.delivery.webui.client.AuthUserClient;
import com.delivery.webui.client.ClientShipmentClient;
import com.delivery.webui.client.dto.UserResponse;
import com.delivery.webui.client.dto.UserRole;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HeaderRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUserClient authClient;

    @MockitoBean
    private ClientShipmentClient shipmentClient;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tracking", "/login", "/register"})
    void publicPagesShowTheGuestHeader(String path) throws Exception {
        String html = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "GUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tracking", "/login", "/register", "/ship", "/ship/success/15", "/profile"})
    @WithMockUser(roles = "CLIENT")
    void pagesKeepTheClientHeaderAfterLogin(String path) throws Exception {
        String html = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "CLIENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tracking", "/login", "/register", "/courier/dashboard",
            "/courier/profile", "/courier/deliveries/15"})
    @WithMockUser(roles = "COURIER")
    void pagesKeepTheCourierHeaderAfterLogin(String path) throws Exception {
        String html = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "COURIER");
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void errorPagesKeepTheGuestHeader(int errorStatus) throws Exception {
        String html = mockMvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, errorStatus)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-page"))
                .andExpect(status().is(errorStatus))
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "GUEST");
    }

    @ParameterizedTest
    @CsvSource({"CLIENT, 404", "CLIENT, 500", "COURIER, 404", "COURIER, 500"})
    void errorDispatchRestoresTheHeaderFromTheSavedLoginSession(UserRole role, int errorStatus) throws Exception {
        MockHttpSession session = login(role);

        String html = mockMvc.perform(get("/error")
                        .session(session)
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, errorStatus)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-page"))
                .andExpect(status().is(errorStatus))
                .andReturn().getResponse().getContentAsString();

        assertHeader(html, role.name());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void missingParcelKeepsTheClientHeaderWhenAnExceptionHandlerRenders404() throws Exception {
        when(shipmentClient.getShipment(99999L)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        String html = mockMvc.perform(get("/my-parcels/99999"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("PAGE NOT FOUND");
        assertHeader(html, "CLIENT");
    }

    @Test
    void queryParameterCannotMakeAGuestHeaderLookAuthenticated() throws Exception {
        String html = mockMvc.perform(get("/tracking").param("previewRole", "CLIENT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "GUEST");
    }

    @Test
    @WithMockUser(roles = "COURIER")
    void queryParameterCannotReplaceTheCourierHeaderWithTheClientHeader() throws Exception {
        String html = mockMvc.perform(get("/tracking").param("previewRole", "CLIENT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "COURIER");
    }

    @Test
    void logoutReturnsTheHeaderToTheGuestState() throws Exception {
        MockHttpSession session = login(UserRole.CLIENT);
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();

        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertHeader(html, "GUEST");
    }

    private MockHttpSession login(UserRole role) throws Exception {
        when(authClient.login(any())).thenReturn(new UserResponse(
                42L, "Test", "User", "test@example.test", "+380000000001", role));
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.test\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private void assertHeader(String html, String role) {
        String header = html.substring(html.indexOf("<header"), html.indexOf("</header>") + "</header>".length());
        if ("GUEST".equals(role)) {
            assertThat(header).contains("href=\"/login\"", "href=\"/register\"")
                    .doesNotContain("data-profile-menu", "data-logout-button");
        } else {
            assertThat(header).contains("data-profile-menu", "data-logout-button")
                    .doesNotContain("href=\"/login\"", "href=\"/register\"");
            if ("CLIENT".equals(role)) {
                assertThat(header).contains("href=\"/ship\"", "href=\"/my-parcels\"",
                                "href=\"/notifications\"", "href=\"/profile\"")
                        .doesNotContain("href=\"/courier/dashboard\"");
            } else {
                assertThat(header).contains("href=\"/courier/dashboard\"", "href=\"/courier/profile\"")
                        .doesNotContain("href=\"/ship\"", "href=\"/my-parcels\"");
            }
        }
    }
}
