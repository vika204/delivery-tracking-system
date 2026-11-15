package com.delivery.dispatch.entity;

public enum DispatchRequestStatus {
    // Потрібно знайти кур'єра зараз.
    SEARCHING,
    // Пропозицію надіслано, чекаємо відповіді кур'єра.
    OFFERED,
    // Підходящих кур'єрів немає; пошук повториться за розкладом або коли кур'єр звільниться.
    WAITING_FOR_COURIERS,
    ASSIGNED,
    // Посилку забрано: відмова кур'єра більше недоступна.
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
