package com.delivery.webui.service;

import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.dto.AvailabilityRequest;
import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import com.delivery.webui.controller.dto.CourierDashboardView;
import org.springframework.stereotype.Service;

import java.util.List;

/** Збирає Dashboard зі стану dispatch-service; після кожної дії повертає оновлений вигляд. */
@Service
public class CourierDashboardService {

    private final DispatchCourierDashboardClient client;

    public CourierDashboardService(DispatchCourierDashboardClient client) {
        this.client = client;
    }

    public CourierDashboardView view(long courierId) {
        DispatchCourierProfile profile = client.profile(courierId);
        DispatchCourierStatus status = client.status(courierId, courierId);
        DispatchCourierOffer offer = client.currentOffer(courierId, courierId);
        List<DispatchCourierAssignment> assignments = client.activeAssignments(courierId, courierId);
        return new CourierDashboardView(
                courierId,
                profile == null ? null : profile.displayName(),
                status.online(),
                status.state(),
                offer,
                assignments == null ? List.of() : assignments
        );
    }

    public CourierDashboardView setAvailability(long courierId, boolean online) {
        client.setAvailability(courierId, courierId, new AvailabilityRequest(online));
        return view(courierId);
    }

    public CourierDashboardView acceptOffer(long courierId, long offerId) {
        client.acceptOffer(courierId, courierId, offerId);
        return view(courierId);
    }

    public CourierDashboardView declineOffer(long courierId, long offerId) {
        client.declineOffer(courierId, courierId, offerId);
        return view(courierId);
    }
}
