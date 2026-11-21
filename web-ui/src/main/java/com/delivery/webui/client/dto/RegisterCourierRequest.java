package com.delivery.webui.client.dto;

import java.math.BigDecimal;

public record RegisterCourierRequest(
        Long userId,
        String displayName,
        String phone,
        String workZone,
        String workZoneCode,
        BigDecimal maxPackageWeight,
        Boolean available
) {
}
