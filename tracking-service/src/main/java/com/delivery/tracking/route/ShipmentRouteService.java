package com.delivery.tracking.route;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.entity.RouteUpload;
import com.delivery.tracking.repository.RouteUploadRepository;
import com.delivery.tracking.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Атомарне збереження маршруту, отриманого через Client Streaming.
 * <p>
 * Усі точки зберігаються в одній транзакції через {@link LocationService#createLocation(Location)},
 * тому активні Server Streaming-підписники отримують кожну точку після коміту.
 * Якщо upload_id уже зафіксовано, повертається збережений підсумок без повторного запису —
 * це робить безпечними gRPC retries, коли сервер зберіг дані, але відповідь не дійшла до клієнта.
 */
@Service
public class ShipmentRouteService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentRouteService.class);

    private final LocationService locationService;
    private final RouteUploadRepository routeUploadRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ShipmentRouteService(
            LocationService locationService,
            RouteUploadRepository routeUploadRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.locationService = locationService;
        this.routeUploadRepository = routeUploadRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = Clock.systemUTC();
    }

    public RouteUploadResult recordRoute(String uploadId, long shipmentId, List<RoutePoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("route must contain at least one point");
        }

        Optional<RouteUploadResult> existing = findExisting(uploadId, shipmentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            RouteUploadResult result = transactionTemplate.execute(status -> save(uploadId, shipmentId, points));
            log.info("Route upload saved: uploadId={}, shipmentId={}, points={}", uploadId, shipmentId, points.size());
            return result;
        } catch (DataIntegrityViolationException ex) {
            // Паралельна спроба з тим самим upload_id встигла закомітитися першою.
            return findExisting(uploadId, shipmentId).orElseThrow(() -> ex);
        }
    }

    private RouteUploadResult save(String uploadId, long shipmentId, List<RoutePoint> points) {
        Long firstLocationId = null;
        Long lastLocationId = null;

        for (RoutePoint point : points) {
            Location saved = locationService.createLocation(new Location(
                    null,
                    shipmentId,
                    point.latitude(),
                    point.longitude(),
                    point.recordedAt()
            ));

            if (firstLocationId == null) {
                firstLocationId = saved.getLocationId();
            }
            lastLocationId = saved.getLocationId();
        }

        routeUploadRepository.saveAndFlush(new RouteUpload(
                uploadId,
                shipmentId,
                points.size(),
                firstLocationId,
                lastLocationId,
                Instant.now(clock)
        ));

        return new RouteUploadResult(uploadId, shipmentId, points.size(), firstLocationId, lastLocationId, false);
    }

    private Optional<RouteUploadResult> findExisting(String uploadId, long shipmentId) {
        return routeUploadRepository.findById(uploadId).map(upload -> {
            if (upload.getShipmentId() != shipmentId) {
                throw new UploadIdConflictException(uploadId);
            }

            log.info("Duplicate route upload ignored: uploadId={}, shipmentId={}", uploadId, shipmentId);
            return new RouteUploadResult(
                    upload.getUploadId(),
                    upload.getShipmentId(),
                    upload.getAcceptedCount(),
                    upload.getFirstLocationId(),
                    upload.getLastLocationId(),
                    true
            );
        });
    }

    public static class UploadIdConflictException extends RuntimeException {
        public UploadIdConflictException(String uploadId) {
            super("uploadId " + uploadId + " is already used for another shipment");
        }
    }
}
