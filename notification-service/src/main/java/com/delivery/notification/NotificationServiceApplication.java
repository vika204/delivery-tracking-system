package com.delivery.notification;

import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpcclient.TrackingGrpcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(
        target = TrackingGrpcProperties.CHANNEL,
        types = {
                ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class,
                ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub.class
        }
)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
