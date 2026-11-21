package com.delivery.webui.security;

import com.delivery.webui.client.dto.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;

public record AuthenticatedUser(long userId, String email, UserRole role) implements Serializable {

    public static long currentUserId(long defaultUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        return defaultUserId;
    }

    public static String currentHeaderState() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "GUEST";
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.role() == UserRole.COURIER ? "COURIER" : "CLIENT";
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_COURIER".equals(authority.getAuthority())) {
                return "COURIER";
            }
            if ("ROLE_CLIENT".equals(authority.getAuthority())) {
                return "CLIENT";
            }
        }
        return "GUEST";
    }
}
