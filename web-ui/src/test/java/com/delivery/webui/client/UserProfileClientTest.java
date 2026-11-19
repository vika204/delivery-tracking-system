package com.delivery.webui.client;

import com.delivery.webui.client.dto.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserProfileClientTest {

    private static final String JSON = """
            {"firstName":"Vika","lastName":"Roman","email":"vika@example.com","phone":"+380971234567"}
            """;
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final UserProfileClient client = HttpServiceProxyFactory.builderFor(
            RestClientAdapter.create(builder.baseUrl("http://auth.test").build()))
            .build().createClient(UserProfileClient.class);

    @Test
    void readsCurrentUserWithConfiguredIdentityHeader() {
        server.expect(requestTo("http://auth.test/users/profile"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        assertThat(client.profile(42)).isEqualTo(new UserProfile("Vika", "Roman", "vika@example.com", "+380971234567"));
        server.verify();
    }

    @Test
    void putsOnlyEditableProfileFields() {
        server.expect(requestTo("http://auth.test/users/profile"))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("X-User-Id", "42"))
                .andExpect(content().json(JSON)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        UserProfile profile = new UserProfile("Vika", "Roman", "vika@example.com", "+380971234567");
        assertThat(client.updateProfile(42, profile)).isEqualTo(profile);
        server.verify();
    }
}
