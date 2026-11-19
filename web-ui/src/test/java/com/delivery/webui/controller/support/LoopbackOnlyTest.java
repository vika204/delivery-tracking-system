package com.delivery.webui.controller.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopbackOnlyTest {

    @Test
    void allowsIpv4AndIpv6Loopback() {
        assertThatCode(() -> LoopbackOnly.require(request("127.0.0.1"), "GPS history")).doesNotThrowAnyException();
        assertThatCode(() -> LoopbackOnly.require(request("::1"), "GPS history")).doesNotThrowAnyException();
    }

    @Test
    void rejectsOtherCallersWithFeatureName() {
        assertThatThrownBy(() -> LoopbackOnly.require(request("203.0.113.5"), "GPS history"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo("GPS history is restricted to local development");
                });
    }

    @Test
    void treatsMissingOrInvalidAddressAsRemote() {
        assertThat(LoopbackOnly.isLoopback(null)).isFalse();
        assertThat(LoopbackOnly.isLoopback("not an address")).isFalse();
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
