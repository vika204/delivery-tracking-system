package com.delivery.webui.controller;

import com.delivery.webui.service.TrackingStreamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/tracking/shipments")
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingSseController {

    private final TrackingStreamService streamService;

    public TrackingSseController(TrackingStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(
            path = "/{shipmentId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @PathVariable long shipmentId,
            HttpServletRequest request
    ) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "shipmentId must be positive"
            );
        }

        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "GPS streaming is restricted to local development"
            );
        }

        return streamService.subscribe(shipmentId);
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
