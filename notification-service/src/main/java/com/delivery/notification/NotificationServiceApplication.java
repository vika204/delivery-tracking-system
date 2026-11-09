package com.delivery.notification;

import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(
        target = "tracking",
        types = ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class
)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
