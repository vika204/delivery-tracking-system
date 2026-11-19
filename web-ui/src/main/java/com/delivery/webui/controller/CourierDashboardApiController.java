package com.delivery.webui.controller;

import com.delivery.webui.controller.dto.AvailabilityRequest;
import com.delivery.webui.controller.dto.CourierDashboardView;
import com.delivery.webui.controller.support.LoopbackOnly;
import com.delivery.webui.service.CourierDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF для Courier Dashboard. Лише для локальної розробки: кур'єр задається властивістю
 * dispatch.courier-dashboard.courier-id, доки немає автентифікації (Keycloak).
 */
@RestController
@RequestMapping("/api/courier/dashboard")
@ConditionalOnProperty(prefix = "dispatch.courier-dashboard", name = "enabled", havingValue = "true")
public class CourierDashboardApiController {

    private final CourierDashboardService dashboardService;
    private final long courierId;

    public CourierDashboardApiController(CourierDashboardService dashboardService,
                                         @Value("${dispatch.courier-dashboard.courier-id}") long courierId) {
        this.dashboardService = dashboardService;
        this.courierId = courierId;
    }

    @ModelAttribute
    void requireLoopback(HttpServletRequest request) {
        LoopbackOnly.require(request, "Courier dashboard API");
    }

    @GetMapping
    public CourierDashboardView dashboard() {
        return dashboardService.view(courierId);
    }

    @PutMapping("/availability")
    public CourierDashboardView setAvailability(@RequestBody AvailabilityRequest request) {
        return dashboardService.setAvailability(courierId, request.online());
    }

    @PostMapping("/offers/{offerId}/accept")
    public CourierDashboardView accept(@PathVariable long offerId) {
        return dashboardService.acceptOffer(courierId, offerId);
    }

    @PostMapping("/offers/{offerId}/decline")
    public CourierDashboardView decline(@PathVariable long offerId) {
        return dashboardService.declineOffer(courierId, offerId);
    }

}
