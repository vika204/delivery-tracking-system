package com.delivery.webui.controller.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Rejects non-local callers of controllers annotated with {@link LocalDevOnly}. */
public class LoopbackInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            LocalDevOnly restriction = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), LocalDevOnly.class);
            if (restriction != null && !isLoopback(request.getRemoteAddr())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        restriction.value() + " is restricted to local development");
            }
        }
        return true;
    }

    static boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }
}
