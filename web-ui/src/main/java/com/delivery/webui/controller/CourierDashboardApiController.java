package com.delivery.webui.controller;

import com.delivery.webui.controller.dto.CourierDashboardView;
import com.delivery.webui.service.CourierDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Courier dashboard API is restricted to local development");
        }
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

    /** 4xx dispatch-service (прострочена пропозиція, кур'єр офлайн тощо) передаємо сторінці як є. */
    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail dispatchRejected(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || !status.is4xxClientError()) {
            return unavailable();
        }
        return ProblemDetail.forStatusAndDetail(status, upstreamDetail(ex, status.getReasonPhrase()));
    }

    private static String upstreamDetail(RestClientResponseException ex, String fallback) {
        try {
            ProblemDetail upstream = ex.getResponseBodyAs(ProblemDetail.class);
            return upstream != null && upstream.getDetail() != null ? upstream.getDetail() : fallback;
        } catch (RuntimeException unreadableBody) {
            return fallback;
        }
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail dispatchUnavailable(RestClientException ex) {
        return unavailable();
    }

    private static ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Dispatch service is temporarily unavailable");
    }

    private static boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    public record AvailabilityRequest(boolean online) {
    }
}
