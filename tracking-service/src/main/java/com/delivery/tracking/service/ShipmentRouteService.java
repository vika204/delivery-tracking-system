package com.delivery.tracking.service;

import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.dto.RouteUploadResult;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.entity.RouteUpload;
import com.delivery.tracking.exception.UploadIdConflictException;
import com.delivery.tracking.repository.RouteUploadRepository;
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
 * Зберігає маршрут, отриманий через Client Streaming. Повтор із тим самим upload_id
 * повертає збережений підсумок замість повторного запису точок.
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

    public RouteUploadResult recordRoute(String uploadId, long shipmentId, List<RoutePoint> validPoints, int receivedCount) {
        Optional<RouteUploadResult> existing = findExisting(uploadId, shipmentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            RouteUploadResult result = transactionTemplate.execute(status -> save(uploadId, shipmentId, validPoints, receivedCount));
            log.info("Route upload saved: uploadId={}, shipmentId={}, received={}, saved={}, rejected={}",
                    uploadId, shipmentId, result.receivedCount(), result.savedCount(), result.rejectedCount());
            return result;
        } catch (DataIntegrityViolationException ex) {
            return findExisting(uploadId, shipmentId).orElseThrow(() -> ex);
        }
    }

    private RouteUploadResult save(String uploadId, long shipmentId, List<RoutePoint> points, int receivedCount) {
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

        int rejectedCount = receivedCount - points.size();

        routeUploadRepository.saveAndFlush(new RouteUpload(
                uploadId,
                shipmentId,
                receivedCount,
                points.size(),
                rejectedCount,
                firstLocationId,
                lastLocationId,
                Instant.now(clock)
        ));

        return new RouteUploadResult(uploadId, shipmentId, receivedCount, points.size(), rejectedCount,
                firstLocationId, lastLocationId, false);
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
                    upload.getReceivedCount(),
                    upload.getSavedCount(),
                    upload.getRejectedCount(),
                    upload.getFirstLocationId(),
                    upload.getLastLocationId(),
                    true
            );
        });
    }
}
