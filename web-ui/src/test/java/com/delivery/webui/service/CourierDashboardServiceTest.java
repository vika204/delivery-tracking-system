package com.delivery.webui.service;

import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import com.delivery.webui.controller.dto.CourierDashboardView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierDashboardServiceTest {

    @Mock
    private DispatchCourierDashboardClient client;

    @Test
    void buildsViewFromDispatchState() {
        stubView(new DispatchCourierStatus(7, true, "AVAILABLE", 0, null), List.of());

        CourierDashboardView view = new CourierDashboardService(client).view(7);

        assertThat(view).isEqualTo(new CourierDashboardView(7, "Olena", true, "AVAILABLE", null, List.of()));
    }

    @Test
    void returnsBusyViewWithActiveDeliveryAfterAccept() {
        DispatchCourierAssignment delivery = new DispatchCourierAssignment(3, 15, "ACTIVE", false, "A", "B");
        stubView(new DispatchCourierStatus(7, true, "BUSY", 1, null), List.of(delivery));

        CourierDashboardView view = new CourierDashboardService(client).acceptOffer(7, 55);

        InOrder order = inOrder(client);
        order.verify(client).acceptOffer(7, 7, 55);
        order.verify(client).status(7, 7);
        assertThat(view.state()).isEqualTo("BUSY");
        assertThat(view.online()).isTrue();
        assertThat(view.deliveries()).containsExactly(delivery);
    }

    @Test
    void doesNotRefreshWhenDispatchRejectsAction() {
        doThrow(HttpClientErrorException.create(org.springframework.http.HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(client).acceptOffer(7, 7, 55);

        assertThatThrownBy(() -> new CourierDashboardService(client).acceptOffer(7, 55))
                .isInstanceOf(HttpClientErrorException.class);
        verify(client, never()).status(7, 7);
    }

    @Test
    void treatsMissingAssignmentsBodyAsEmptyList() {
        stubView(new DispatchCourierStatus(7, false, "UNAVAILABLE", 0, null), null);

        assertThat(new CourierDashboardService(client).view(7).deliveries()).isEmpty();
    }

    private void stubView(DispatchCourierStatus status, List<DispatchCourierAssignment> deliveries) {
        when(client.profile(7)).thenReturn(new DispatchCourierProfile(7L, "Olena", null, null, null, null));
        when(client.status(7, 7)).thenReturn(status);
        when(client.currentOffer(7, 7)).thenReturn(null);
        when(client.activeAssignments(7, 7)).thenReturn(deliveries);
    }
}
