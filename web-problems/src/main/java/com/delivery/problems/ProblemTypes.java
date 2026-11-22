package com.delivery.problems;

import java.net.URI;

/** Problem type URIs shared by every service: {@code urn:delivery:problem:<slug>}. */
public final class ProblemTypes {

    private static final String PREFIX = "urn:delivery:problem:";

    public static final URI VALIDATION_FAILED = of("validation-failed");
    public static final URI INTERNAL_ERROR = of("internal-error");
    public static final URI DATA_CONFLICT = of("data-conflict");
    public static final URI CONCURRENT_UPDATE = of("concurrent-update");
    public static final URI DATABASE_UNAVAILABLE = of("database-unavailable");

    private ProblemTypes() {
    }

    public static URI of(String slug) {
        return URI.create(PREFIX + slug);
    }
}
