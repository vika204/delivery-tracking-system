package com.delivery.webui.controller.dto;

import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;

import java.util.List;

/**
 * Усе, що показує Courier Dashboard, одним об'єктом: його повертають і читання, і кожна дія,
 * тож сторінка завжди відображає стан dispatch-service після операції.
 *
 * @param online положення перемикача
 * @param state  AVAILABLE / BUSY / UNAVAILABLE
 * @param offer  пропозиція на розгляді або null
 */
public record CourierDashboardView(
        long courierId,
        String courierName,
        boolean online,
        String state,
        DispatchCourierOffer offer,
        List<DispatchCourierAssignment> deliveries
) {
}
