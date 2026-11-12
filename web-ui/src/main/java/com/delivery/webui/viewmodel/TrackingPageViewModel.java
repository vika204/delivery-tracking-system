package com.delivery.webui.viewmodel;

import com.delivery.webui.client.dto.TrackingLocationResponse;

import java.util.List;

public record TrackingPageViewModel(
        boolean shipmentLoaded,
        String trackingNumber,
        String statusText,
        String destinationText,
        String courierText,
        List<TrackingTimelineStepViewModel> timeline,
        List<TrackingLocationResponse> locations,
        boolean locationDataAvailable,
        boolean locationHistoryComplete,
        String locationMessage,
        String locationDescription,
        boolean terminal
) {
}
