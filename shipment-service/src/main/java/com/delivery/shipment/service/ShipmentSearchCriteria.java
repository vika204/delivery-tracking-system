package com.delivery.shipment.service;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.entity.StatusGroup;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Фільтри списку доставок (US-1.5). Усі поля необов'язкові. */
public record ShipmentSearchCriteria(
        Long senderId,
        Long courierId,
        ShipmentStatus status,
        StatusGroup statusGroup,
        LocalDate createdFrom,
        LocalDate createdTo
) {

    Specification<Shipment> toSpecification() {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (senderId != null) {
                predicates.add(cb.equal(root.get("userId"), senderId));
            }
            if (courierId != null) {
                predicates.add(cb.equal(root.get("assignedCourierId"), courierId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (statusGroup != null) {
                predicates.add(root.get("status").in(statusGroup.statuses()));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom.atStartOfDay()));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdTo.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
