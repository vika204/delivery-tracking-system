package com.delivery.tracking.service;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.event.LocationSavedEvent;
import com.delivery.tracking.repository.TrackingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final TrackingRepository trackingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LocationService(TrackingRepository trackingRepository, ApplicationEventPublisher eventPublisher) {
        this.trackingRepository = trackingRepository;
        this.eventPublisher = eventPublisher;
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
}