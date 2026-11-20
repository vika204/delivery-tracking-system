package com.delivery.webui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dispatch.courier-dashboard")
public record CourierDashboardProperties(long courierId) {
}
