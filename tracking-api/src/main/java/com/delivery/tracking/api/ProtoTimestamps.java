package com.delivery.tracking.api;

import com.google.protobuf.Timestamp;

import java.time.Instant;

public final class ProtoTimestamps {

    private ProtoTimestamps() {
    }

    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
