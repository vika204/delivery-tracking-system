package com.delivery.webui.controller.dto;

import java.math.BigDecimal;

public record CourierRegistrationRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String workZone,
        String workZoneCode,
        BigDecimal maxWeight,
        String password
) {
}
