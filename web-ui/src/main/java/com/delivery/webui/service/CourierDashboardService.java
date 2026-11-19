package com.delivery.webui.service;

import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import com.delivery.webui.controller.dto.CourierDashboardView;
import org.springframework.stereotype.Service;

/** Збирає Dashboard зі стану dispatch-service; після кожної дії повертає оновлений вигляд. */
@Service
public class CourierDashboardService {

    private final DispatchCourierDashboardClient client;

    public CourierDashboardService(DispatchCourierDashboardClient client) {
        this.client = client;
    }

    public CourierDashboardView view(long courierId) {
        DispatchCourierProfile profile = client.profile(courierId);
        DispatchCourierStatus status = client.status(courierId);
        return new CourierDashboardView(
                courierId,
                profile == null ? null : profile.displayName(),
                status.online(),
                status.state(),
                client.currentOffer(courierId),
                client.activeAssignments(courierId)
        );
    }

    public CourierDashboardView setAvailability(long courierId, boolean online) {
        client.setAvailability(courierId, online);
        return view(courierId);
    }

    public CourierDashboardView acceptOffer(long courierId, long offerId) {
        client.acceptOffer(courierId, offerId);
        return view(courierId);
    }

    public CourierDashboardView declineOffer(long courierId, long offerId) {
        client.declineOffer(courierId, offerId);
        return view(courierId);
    }
}
