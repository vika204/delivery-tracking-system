package com.delivery.dispatch.tracking.dto;

import java.util.List;

public record RouteUploadRequest(
        List<RoutePointRequest> points
) {
}
