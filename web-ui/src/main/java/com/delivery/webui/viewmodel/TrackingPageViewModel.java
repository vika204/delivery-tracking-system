package com.delivery.webui.viewmodel;

import com.delivery.webui.client.dto.TrackingLocationResponse;

import java.util.List;

public record TrackingPageViewModel(
        String trackingNumber,
        String statusText,
        String destinationText,
        String courierText,
        List<TrackingLocationResponse> locations,
        boolean locationHistoryComplete,
        String locationMessage,
        boolean terminal
) {
}
