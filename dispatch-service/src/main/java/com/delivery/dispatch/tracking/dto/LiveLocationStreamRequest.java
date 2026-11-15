package com.delivery.dispatch.tracking.dto;

import java.util.List;

public record LiveLocationStreamRequest(
        List<LiveLocationPointRequest> points
) {
}
