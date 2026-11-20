package com.delivery.webui.controller.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopbackInterceptorTest {

    private final LoopbackInterceptor interceptor = new LoopbackInterceptor();

    @LocalDevOnly("GPS history")
    static class Restricted {
        public void handle() {
            // handler body is irrelevant: only the class annotation is inspected
        }
    }

    static class Open {
        public void handle() {
            // handler body is irrelevant: only the class annotation is inspected
        }
    }

    @Test
    void allowsIpv4AndIpv6Loopback() throws Exception {
        assertThat(interceptor.preHandle(request("127.0.0.1"), new MockHttpServletResponse(), handler(new Restricted()))).isTrue();
        assertThat(interceptor.preHandle(request("::1"), new MockHttpServletResponse(), handler(new Restricted()))).isTrue();
    }

    @Test
    void rejectsOtherCallersWithFeatureName() throws Exception {
        HandlerMethod handler = handler(new Restricted());

        assertThatThrownBy(() -> interceptor.preHandle(request("203.0.113.5"), new MockHttpServletResponse(), handler))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo("GPS history is restricted to local development");
                });
    }

    @Test
    void ignoresControllersWithoutTheAnnotationAndNonControllerHandlers() throws Exception {
        assertThat(interceptor.preHandle(request("203.0.113.5"), new MockHttpServletResponse(), handler(new Open()))).isTrue();
        assertThat(interceptor.preHandle(request("203.0.113.5"), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void treatsMissingOrInvalidAddressAsRemote() {
        assertThat(LoopbackInterceptor.isLoopback(null)).isFalse();
        assertThat(LoopbackInterceptor.isLoopback("not an address")).isFalse();
    }

    private static HandlerMethod handler(Object bean) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod("handle"));
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
