package com.delivery.webui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local-development courier: acts as this courier until real authentication exists. */
@ConfigurationProperties(prefix = "dispatch.courier-dashboard")
public record CourierDashboardProperties(long courierId) {
}
