package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordLocationRequest(
        UUID pointId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
