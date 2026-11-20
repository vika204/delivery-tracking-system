package com.delivery.webui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shipment.creation")
public record ShipmentCreationProperties(long senderId) {
}
