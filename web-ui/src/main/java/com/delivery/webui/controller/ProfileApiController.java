package com.delivery.webui.controller;

import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.UserProfileClient;
import com.delivery.webui.client.dto.UpdateCourierProfileRequest;
import com.delivery.webui.client.dto.UserProfile;
import com.delivery.webui.controller.dto.CourierProfile;
import com.delivery.webui.controller.support.LocalDevOnly;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@LocalDevOnly("Profile API")
public class ProfileApiController {

    private final UserProfileClient userClient;
    private final DispatchCourierDashboardClient courierClient;
    private final long clientId;
    private final long courierId;

    public ProfileApiController(UserProfileClient userClient, DispatchCourierDashboardClient courierClient,
                                ShipmentCreationProperties creation,
                                CourierDashboardProperties dashboard) {
        this.userClient = userClient;
        this.courierClient = courierClient;
        this.clientId = creation.senderId();
        this.courierId = dashboard.courierId();
    }

    @ModelAttribute
    void noStore(HttpServletResponse response) {
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
