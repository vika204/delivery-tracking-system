package com.delivery.shipment.controller.support;

public final class RequestHeaders {

    /**
     * Ідентифікатор користувача, від імені якого виконується запит (відправник або кур'єр).
     * Тимчасова заміна автентифікації: після підключення Keycloak значення братиметься з токена.
     */
    public static final String USER_ID = "X-User-Id";

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private RequestHeaders() {
    }
}
