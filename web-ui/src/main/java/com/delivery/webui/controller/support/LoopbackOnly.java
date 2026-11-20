package com.delivery.webui.controller.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Dev-only API без автентифікації приймає лише запити з цієї машини. */
public final class LoopbackOnly {

    private LoopbackOnly() {
    }

    public static void require(HttpServletRequest request, String feature) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, feature + " is restricted to local development");
        }
    }

    static boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }
}
