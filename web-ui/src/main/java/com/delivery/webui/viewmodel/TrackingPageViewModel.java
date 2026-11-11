package com.delivery.webui.viewmodel;

import java.util.List;

public record TrackingPageViewModel(
        String trackingNumber,
        String etaText,
        String statusText,
        String courierInitials,
        String courierName,
        String courierDescription,
        String destinationCity,
        String destinationAddress,
        List<TrackingTimelineStepViewModel> timeline
) {
}