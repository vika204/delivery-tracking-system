package com.delivery.webui.controller.dto;

import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.UserProfile;

import java.math.BigDecimal;

public record CourierProfile(
        String firstName,
        String lastName,
        String email,
        String phone,
        String workZone,
        String workZoneCode,
        BigDecimal maxWeight
) {
    public static CourierProfile from(UserProfile user, DispatchCourierProfile courier) {
        return new CourierProfile(user.firstName(), user.lastName(), user.email(), user.phone(),
                courier.workZone(), courier.workZoneCode(), courier.maxPackageWeight());
    }
}
