package com.delivery.webui.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/offline.html",
                                "/service-worker.js",
                                "/tracking",
                                "/tracking/**",
                                "/api/auth/**",
                                "/api/tracking/**",
                                "/data/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/videos/**",
                                "/favicon.ico",
                                "/error",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/courier/**", "/api/courier/**").hasRole("COURIER")
                        .requestMatchers(
                                "/ship",
                                "/ship/**",
                                "/my-parcels",
                                "/my-parcels/**",
                                "/notifications",
                                "/profile",
                                "/api/shipments/**",
                                "/api/notifications/**",
                                "/api/profile"
                        ).hasRole("CLIENT")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, _) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                );
        return http.build();
    }
}
