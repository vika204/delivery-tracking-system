package com.delivery.webui.controller;

import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.UserProfileClient;
import com.delivery.webui.client.dto.UpdateCourierProfileRequest;
import com.delivery.webui.client.dto.UserProfile;
import com.delivery.webui.controller.dto.CourierProfile;
import com.delivery.webui.controller.support.LoopbackOnly;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileApiController {

    private final UserProfileClient userClient;
    private final DispatchCourierDashboardClient courierClient;
    private final long clientId;
    private final long courierId;

    public ProfileApiController(UserProfileClient userClient, DispatchCourierDashboardClient courierClient,
                                @Value("${shipment.creation.sender-id}") long clientId,
                                @Value("${dispatch.courier-dashboard.courier-id}") long courierId) {
        this.userClient = userClient;
        this.courierClient = courierClient;
        this.clientId = clientId;
        this.courierId = courierId;
    }

    @ModelAttribute
    void requireLocalAccess(HttpServletRequest request, HttpServletResponse response) {
        LoopbackOnly.require(request, "Profile API");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/profile")
    public UserProfile clientProfile() {
        return userClient.profile(clientId);
    }

    @PutMapping("/profile")
    public UserProfile updateClientProfile(@RequestBody UserProfile profile) {
        return userClient.updateProfile(clientId, profile);
    }

    @GetMapping("/courier/profile")
    public CourierProfile courierProfile() {
        return CourierProfile.from(userClient.profile(courierId), courierClient.profile(courierId));
    }

    @PutMapping("/courier/profile")
    public CourierProfile updateCourierProfile(@RequestBody CourierProfile profile) {
        UserProfile user = userClient.updateProfile(courierId,
                new UserProfile(profile.firstName(), profile.lastName(), profile.email(), profile.phone()));
        var courier = courierClient.updateProfile(courierId, courierId,
                new UpdateCourierProfileRequest(user.firstName() + " " + user.lastName(), user.phone(),
                        profile.workZone(), profile.workZoneCode(), profile.maxWeight()));
        return CourierProfile.from(user, courier);
    }
}
