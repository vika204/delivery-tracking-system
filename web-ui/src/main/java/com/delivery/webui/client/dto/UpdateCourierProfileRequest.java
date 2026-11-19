package com.delivery.webui.client.dto;

import java.math.BigDecimal;

public record UpdateCourierProfileRequest(
        String displayName,
        String phone,
        String workZone,
        String workZoneCode,
        BigDecimal maxPackageWeight
) {
}
