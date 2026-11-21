package com.delivery.webui.controller;

import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.client.dto.AvailabilityRequest;
import com.delivery.webui.controller.dto.CourierDashboardView;
import com.delivery.webui.controller.support.LocalDevOnly;
import com.delivery.webui.security.AuthenticatedUser;
import com.delivery.webui.service.CourierDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courier/dashboard")
@ConditionalOnProperty(prefix = "dispatch.courier-dashboard", name = "enabled", havingValue = "true")
@LocalDevOnly("Courier dashboard API")
public class CourierDashboardApiController {

    private final CourierDashboardService dashboardService;
    private final long courierId;

    public CourierDashboardApiController(CourierDashboardService dashboardService,
                                         CourierDashboardProperties properties) {
        this.dashboardService = dashboardService;
        this.courierId = properties.courierId();
    }

    @GetMapping
    public CourierDashboardView dashboard() {
        return dashboardService.view(AuthenticatedUser.currentUserId(courierId));
    }

    @PutMapping("/availability")
    public CourierDashboardView setAvailability(@RequestBody AvailabilityRequest request) {
        return dashboardService.setAvailability(AuthenticatedUser.currentUserId(courierId), request.online());
    }

    @PostMapping("/offers/{offerId}/accept")
    public CourierDashboardView accept(@PathVariable long offerId) {
        return dashboardService.acceptOffer(AuthenticatedUser.currentUserId(courierId), offerId);
    }

    @PostMapping("/offers/{offerId}/decline")
    public CourierDashboardView decline(@PathVariable long offerId) {
        return dashboardService.declineOffer(AuthenticatedUser.currentUserId(courierId), offerId);
    }

}
