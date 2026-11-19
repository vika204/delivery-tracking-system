package com.delivery.webui.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationClientTest {

    private MockRestServiceServer server;
    private NotificationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://notification.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build().createClient(NotificationClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void requestsClientNotificationsWithHeaderAndLimit() {
        server.expect(requestTo("http://notification.test/notifications/mine?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("""
                        [{"notificationId":101, "shipmentId":15, "type":"DELIVERED",
                          "message":"Your package has been delivered.", "createdAt":"2026-11-19T12:30:00", "read":true}]
                        """, MediaType.APPLICATION_JSON));

        var result = client.getMyNotifications(42, 50);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo("DELIVERED");
        assertThat(result.getFirst().shipmentId()).isEqualTo(15);
        assertThat(result.getFirst().createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 12, 30));
        assertThat(result.getFirst().read()).isTrue();
    }

    @Test
    void readsEmptyHistory() {
        server.expect(requestTo("http://notification.test/notifications/mine?limit=50"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.getMyNotifications(42, 50)).isEmpty();
    }

    @Test
    void readsUnreadIndicatorAsCurrentClient() {
        server.expect(requestTo("http://notification.test/notifications/mine/unread"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        assertThat(client.hasUnreadNotifications(42)).isTrue();
    }

    @Test
    void sendsReadBoundaryAndPreservesUnreadNotificationsAfterIt() {
        server.expect(requestTo("http://notification.test/notifications/mine/read?throughId=103"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        assertThat(client.markNotificationsRead(42, 103)).isTrue();
    }

    @Test
    void propagatesFailureToPageErrorHandler() {
        server.expect(requestTo("http://notification.test/notifications/mine?limit=50"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getMyNotifications(42, 50)).isInstanceOf(HttpServerErrorException.class);
    }
}
