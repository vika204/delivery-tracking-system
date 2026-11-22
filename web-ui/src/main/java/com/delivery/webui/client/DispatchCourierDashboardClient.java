package com.delivery.webui.client;

import com.delivery.webui.client.dto.AvailabilityRequest;
import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import com.delivery.webui.client.dto.RegisterCourierRequest;
import com.delivery.webui.client.dto.UpdateCourierProfileRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

@HttpExchange("/couriers")
public interface DispatchCourierDashboardClient {

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    DispatchCourierProfile register(@RequestBody RegisterCourierRequest request);

    @GetExchange("/{id}")
    DispatchCourierProfile profile(@PathVariable("id") long courierId);

    @PatchExchange(value = "/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    DispatchCourierProfile updateProfile(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId,
            @RequestBody UpdateCourierProfileRequest profile
    );

    @GetExchange("/{id}/status")
    DispatchCourierStatus status(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId
    );

    @GetExchange("/{id}/offers/current")
    DispatchCourierOffer currentOffer(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId
    );

    @GetExchange("/{id}/assignments?active=true")
    List<DispatchCourierAssignment> activeAssignments(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId
    );

    @PutExchange(value = "/{id}/availability", contentType = MediaType.APPLICATION_JSON_VALUE)
    void setAvailability(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId,
            @RequestBody AvailabilityRequest request
    );

    @PostExchange("/{id}/offers/{offerId}/accept")
    void acceptOffer(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId,
            @PathVariable("offerId") long offerId
    );

    @PostExchange("/{id}/offers/{offerId}/decline")
    void declineOffer(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable("id") long courierId,
            @PathVariable("offerId") long offerId
    );

    @PostExchange("/shipments/{shipmentId}/withdraw")
    void withdrawShipment(
            @RequestHeader("X-User-Id") long courierId,
            @PathVariable("shipmentId") long shipmentId
    );
}
