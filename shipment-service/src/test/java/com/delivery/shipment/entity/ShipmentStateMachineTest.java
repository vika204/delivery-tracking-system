package com.delivery.shipment.entity;

import com.delivery.shipment.exception.ShipmentAccessDeniedException;
import com.delivery.shipment.exception.ShipmentStatusConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentStateMachineTest {

    static final long SENDER = 42;
    static final long COURIER = 7;
    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void happyPathFromAssignmentToDelivery() {
        Shipment shipment = waiting();

        assertThat(shipment.assignCourier(COURIER, "Andrii", NOW)).isTrue();
        assertThat(shipment.confirmPickup(COURIER, NOW)).isTrue();
        assertThat(shipment.completeDelivery(COURIER, NOW)).isTrue();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getPickedUpAt()).isEqualTo(NOW);
        assertThat(shipment.getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void pickupMovesAssignedShipmentStraightToInTransit() {
        Shipment shipment = assigned();

        shipment.confirmPickup(COURIER, NOW);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void pickupRequiresAssignment() {
        var subject = waiting();
        subject.setAssignedCourierId(COURIER);

        assertThatThrownBy(() -> subject.confirmPickup(COURIER, NOW))
                .isInstanceOf(ShipmentStatusConflictException.class);
    }

    @Test
    void repeatedCourierCommandIsNoOp() {
        Shipment shipment = assigned();
        shipment.confirmPickup(COURIER, NOW);

        assertThat(shipment.confirmPickup(COURIER, NOW.plusSeconds(60))).isFalse();
        assertThat(shipment.getPickedUpAt()).isEqualTo(NOW);
    }

    @Test
    void deliveryRequiresPickup() {
        var subject1 = assigned();
        assertThatThrownBy(() -> subject1.completeDelivery(COURIER, NOW))
                .isInstanceOf(ShipmentStatusConflictException.class);
    }

    @Test
    void onlyAssignedCourierCanAct() {
        var subject2 = assigned();
        assertThatThrownBy(() -> subject2.confirmPickup(99, NOW))
                .isInstanceOf(ShipmentAccessDeniedException.class);
        var subject3 = waiting();
        assertThatThrownBy(() -> subject3.confirmPickup(COURIER, NOW))
                .isInstanceOf(ShipmentAccessDeniedException.class);
    }

    @Test
    void senderCanCancelBeforePickup() {
        for (Shipment shipment : new Shipment[]{waiting(), assigned()}) {
            shipment.cancelBySender(SENDER, "changed my mind", NOW);
            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(shipment.getCancelledAt()).isEqualTo(NOW);
        }
    }

    @Test
    void cancellationAfterPickupIsRejected() {
        Shipment shipment = assigned();
        shipment.confirmPickup(COURIER, NOW);

        assertThatThrownBy(() -> shipment.cancelBySender(SENDER, null, NOW))
                .isInstanceOf(ShipmentStatusConflictException.class)
                .hasMessageContaining("picked up");
    }

    @Test
    void onlySenderCanCancel() {
        var subject4 = waiting();
        assertThatThrownBy(() -> subject4.cancelBySender(1, null, NOW))
                .isInstanceOf(ShipmentAccessDeniedException.class);
    }

    @Test
    void courierCannotActOnCancelledShipment() {
        Shipment shipment = assigned();
        shipment.cancelBySender(SENDER, null, NOW);

        assertThatThrownBy(() -> shipment.confirmPickup(COURIER, NOW))
                .isInstanceOf(ShipmentStatusConflictException.class);
    }

    @Test
    void assignmentOfCancelledShipmentIsIgnored() {
        Shipment shipment = waiting();
        shipment.cancelBySender(SENDER, null, NOW);

        assertThat(shipment.assignCourier(COURIER, "Andrii", NOW)).isFalse();
        assertThat(shipment.getAssignedCourierId()).isNull();
    }

    @Test
    void unassignReturnsShipmentToWaiting() {
        Shipment shipment = assigned();

        assertThat(shipment.unassignCourier(COURIER)).isTrue();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.WAITING_FOR_COURIER);
        assertThat(shipment.getAssignedCourierId()).isNull();
    }

    @Test
    void unassignAfterPickupOrForOtherCourierIsIgnored() {
        Shipment inTransit = assigned();
        inTransit.confirmPickup(COURIER, NOW);

        assertThat(inTransit.unassignCourier(COURIER)).isFalse();
        assertThat(assigned().unassignCourier(99)).isFalse();
    }

    @Test
    void editingIsAllowedOnlyBeforeAssignment() {
        waiting().requireEditableBy(SENDER);

        var subject5 = assigned();
        assertThatThrownBy(() -> subject5.requireEditableBy(SENDER))
                .isInstanceOf(ShipmentStatusConflictException.class);
        var subject6 = waiting();
        assertThatThrownBy(() -> subject6.requireEditableBy(1))
                .isInstanceOf(ShipmentAccessDeniedException.class);
    }

    private static Shipment waiting() {
        return Shipment.builder().shipmentId(1L).userId(SENDER).status(ShipmentStatus.WAITING_FOR_COURIER).build();
    }

    private static Shipment assigned() {
        Shipment shipment = waiting();
        shipment.assignCourier(COURIER, "Andrii", NOW);
        return shipment;
    }
}
