package com.delivery.webui.viewmodel;

import java.util.List;

public record TrackingPageViewModel(
        boolean shipmentLoaded,
        String trackingNumber,
        String statusText,
        List<TrackingTimelineStepViewModel> timeline
) {
}
