package com.delivery.webui.viewmodel;

public record TrackingTimelineStepViewModel(
        String title,
        String description,
        boolean done,
        boolean current
) {
}