package com.delivery.webui.controller;

import com.delivery.webui.client.AuthUserClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.dto.LoginRequest;
import com.delivery.webui.client.dto.RegisterCourierRequest;
import com.delivery.webui.client.dto.UserRequest;
import com.delivery.webui.client.dto.UserResponse;
import com.delivery.webui.client.dto.UserRole;
import com.delivery.webui.controller.dto.AuthRedirectResponse;
import com.delivery.webui.controller.dto.ClientRegistrationRequest;
import com.delivery.webui.controller.dto.CourierRegistrationRequest;
import com.delivery.webui.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthUserClient authUserClient;
    private final DispatchCourierDashboardClient courierClient;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthApiController(AuthUserClient authUserClient,
                             DispatchCourierDashboardClient courierClient) {
        this.authUserClient = authUserClient;
        this.courierClient = courierClient;
    }

    @PostMapping("/register/client")
    public AuthRedirectResponse registerClient(@Valid @RequestBody ClientRegistrationRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        UserResponse user = authUserClient.createUser(new UserRequest(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().trim(),
                request.phone().trim(),
                request.password(),
                UserRole.CLIENT
        ));
        authenticateSession(user, httpRequest, httpResponse);
        return new AuthRedirectResponse("/profile");
    }

    @PostMapping("/register/courier")
    public AuthRedirectResponse registerCourier(@Valid @RequestBody CourierRegistrationRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String phone = request.phone().trim();
        UserResponse user = authUserClient.createUser(new UserRequest(
                firstName,
                lastName,
                request.email().trim(),
                phone,
                request.password(),
                UserRole.COURIER
        ));
        courierClient.register(new RegisterCourierRequest(
                user.userId(),
                firstName + " " + lastName,
                phone,
                request.workZone().trim(),
                request.workZoneCode().trim(),
                request.maxWeight(),
                true
        ));
        authenticateSession(user, httpRequest, httpResponse);
        return new AuthRedirectResponse("/courier/profile");
    }

    @PostMapping("/login")
    public AuthRedirectResponse login(@Valid @RequestBody LoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        UserResponse user = authUserClient.login(request);
        authenticateSession(user, httpRequest, httpResponse);
        return new AuthRedirectResponse(user.role() == UserRole.COURIER ? "/courier/profile" : "/profile");
    }

    @PostMapping("/logout")
    public AuthRedirectResponse logout(HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        logoutHandler.logout(httpRequest, httpResponse, SecurityContextHolder.getContext().getAuthentication());
        return new AuthRedirectResponse("/login");
    }

    private void authenticateSession(UserResponse user,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        AuthenticatedUser principal = new AuthenticatedUser(user.userId(), user.email(), user.role());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }
}
