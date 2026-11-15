package com.delivery.dispatch;

import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(
        target = "tracking",
        types = ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class
)
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
