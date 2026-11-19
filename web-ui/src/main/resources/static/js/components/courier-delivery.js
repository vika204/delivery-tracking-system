import {formatDistance} from './format-distance.js';

/** TO PICK UP — підпис посилки зі статусом ASSIGNED (і TO_PICKUP, якщо кур'єр уже виїхав). */
const LABELS = Object.freeze({
    ASSIGNED: 'TO PICK UP',
    TO_PICKUP: 'TO PICK UP',
    IN_TRANSIT: 'IN TRANSIT',
    DELIVERED: 'DELIVERED',
    CANCELLED: 'CANCELLED',
    WAITING_FOR_COURIER: 'NOT ASSIGNED'
});

export const GPS_MIN_INTERVAL_MS = 5000;

export function statusLabel(status) {
    return LABELS[status] ?? '—';
}

export function statusClass(status) {
    if (status === 'IN_TRANSIT') return 'in-transit';
    if (status === 'DELIVERED') return 'delivered';
    if (status === 'CANCELLED') return 'withdrawn';
    return '';
}

/** Наступна дія кур'єра: забрати посилку, завершити доставку або нічого. */
export function nextAction(status) {
    if (status === 'ASSIGNED' || status === 'TO_PICKUP') {
        return {action: 'pickup', label: 'CONFIRM PICKUP'};
    }
    if (status === 'IN_TRANSIT') {
        return {action: 'deliver', label: 'MARK AS DELIVERED'};
    }
    return null;
}

/** GPS передається лише поки посилка в дорозі. */
export function sharesGps(status) {
    return status === 'IN_TRANSIT';
}

/** Не частіше за одну точку на інтервал: браузер може віддавати позицію щосекунди. */
export function shouldSendPosition(lastSentAtMs, nowMs, minIntervalMs = GPS_MIN_INTERVAL_MS) {
    return lastSentAtMs === null || nowMs - lastSentAtMs >= minIntervalMs;
}

/** GeolocationPosition → тіло запиту tracking-service; pointId робить повтор безпечним. */
export function locationPayload(position, pointId = crypto.randomUUID()) {
    return {
        pointId,
        latitude: Number(position.coords.latitude.toFixed(6)),
        longitude: Number(position.coords.longitude.toFixed(6)),
        recordedAt: new Date(position.timestamp).toISOString()
    };
}

/**
 * Після збою мережі чи 5xx ту саму точку надсилаємо ще раз з тим самим pointId (tracking-service
 * не створить дубля). Відхилену точку (4xx) повторювати немає сенсу.
 */
export function isRetriableFailure(status) {
    return status === null || status >= 500;
}

export function formatValue(value, unit) {
    if (unit === 'km') return formatDistance(value);
    return value == null ? '—' : `${Number(value)} ${unit}`;
}
