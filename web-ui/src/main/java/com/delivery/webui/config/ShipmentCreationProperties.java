package com.delivery.webui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local-development sender: acts as this user until real authentication exists. */
@ConfigurationProperties(prefix = "shipment.creation")
public record ShipmentCreationProperties(long senderId) {
}
