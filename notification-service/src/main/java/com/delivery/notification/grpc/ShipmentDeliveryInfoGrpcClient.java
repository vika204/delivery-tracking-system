package com.delivery.notification.grpc;

import com.delivery.notification.grpc.dto.DeliveryInfoResponse;
import com.delivery.tracking.grpc.GetShipmentDeliveryInfoRequest;
import com.delivery.tracking.grpc.ShipmentDeliveryInfo;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * gRPC Unary-клієнт notification-service -> tracking-service:
 * поточний статус доставки та остання GPS-координата одним запитом.
 * <p>
 * Повтори (retries) при {@code UNAVAILABLE} виконує сам gRPC-канал за service config
 * з application.properties; цей клас бачить лише фінальний результат виклику.
 * Якщо всі спроби вичерпано, {@link StatusRuntimeException} перетворюється на
 * {@link TrackingGrpcException}, а HTTP-шар відповідає 503 Service Unavailable.
 */
@Component
public class ShipmentDeliveryInfoGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveryInfoGrpcClient.class);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub;

    public ShipmentDeliveryInfoGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub) {
        this.blockingStub = blockingStub;
    }

    public DeliveryInfoResponse getDeliveryInfo(long shipmentId) {
        GetShipmentDeliveryInfoRequest request = GetShipmentDeliveryInfoRequest.newBuilder()
                .setShipmentId(shipmentId)
                .build();

        try {
            ShipmentDeliveryInfo info = blockingStub.getShipmentDeliveryInfo(request);
            log.info("gRPC delivery info received: shipmentId={}, status={}, hasLocation={}",
                    info.getShipmentId(), info.getStatus(), info.getHasLocation());
            return toResponse(info);
        } catch (StatusRuntimeException ex) {
            Status status = ex.getStatus();

            if (status.getCode() == Status.Code.UNAVAILABLE) {
                log.error("gRPC GetShipmentDeliveryInfo failed: tracking-service unavailable after all retries: shipmentId={}, description={}",
                        shipmentId, status.getDescription());
            } else {
                log.warn("gRPC GetShipmentDeliveryInfo failed: shipmentId={}, status={}, description={}",
                        shipmentId, status.getCode(), status.getDescription());
            }

            throw new TrackingGrpcException(status, ex);
        }
    }

    private static DeliveryInfoResponse toResponse(ShipmentDeliveryInfo info) {
        if (!info.getHasLocation()) {
            return new DeliveryInfoResponse(info.getShipmentId(), info.getStatus(), null, null, null);
        }

        return new DeliveryInfoResponse(
                info.getShipmentId(),
                info.getStatus(),
                info.getLatitude(),
                info.getLongitude(),
                Instant.ofEpochSecond(info.getRecordedAt().getSeconds(), info.getRecordedAt().getNanos())
        );
    }
}
