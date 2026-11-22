package com.delivery.tracking.service;

import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.event.LocationSavedEvent;
import com.delivery.tracking.repository.TrackingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class LocationService {

    private final TrackingRepository trackingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LocationService(TrackingRepository trackingRepository, ApplicationEventPublisher eventPublisher) {
        this.trackingRepository = trackingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Location> getLocations() {
        return trackingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Location getLocation(Long id) {
        return trackingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Location createLocation(Location location) {
        Location saved = trackingRepository.save(location);

        eventPublisher.publishEvent(new LocationSavedEvent(
                saved.getLocationId(),
                saved.getShipmentId(),
                saved.getLatitude(),
                saved.getLongitude(),
                saved.getRecordedAt()
        ));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ShipmentLocationResponse> getShipmentLocations(
            long shipmentId,
            long afterLocationId,
            int limit
    ) {
        return trackingRepository.findByShipmentIdAndLocationIdGreaterThanOrderByLocationIdAsc(
                        shipmentId,
                        afterLocationId,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(ShipmentLocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Location> findLatestLocation(long shipmentId) {
        return trackingRepository.findFirstByShipmentIdOrderByRecordedAtDescLocationIdDesc(shipmentId);
    }
}
