package com.delivery.webui.controller.dto;

import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;

import java.util.List;

public record CourierDashboardView(
        long courierId,
        String courierName,
        boolean online,
        String state,
        DispatchCourierOffer offer,
        List<DispatchCourierAssignment> deliveries
) {
}
