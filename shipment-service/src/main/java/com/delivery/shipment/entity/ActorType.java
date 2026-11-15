package com.delivery.shipment.entity;

/** Хто ініціював зміну стану доставки. */
public enum ActorType {
    SENDER,
    COURIER,
    // Реакція на події інших контекстів (Dispatch).
    SYSTEM
}
