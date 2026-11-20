import {formatDistance} from './format-distance.js';

const LABELS = Object.freeze({
    ASSIGNED: 'TO PICK UP',
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

export function nextAction(status) {
    if (status === 'ASSIGNED') {
        return {action: 'pickup', label: 'CONFIRM PICKUP'};
    }
    if (status === 'IN_TRANSIT') {
        return {action: 'deliver', label: 'MARK AS DELIVERED'};
    }
    return null;
}

export function canWithdraw(status) {
    return status === 'ASSIGNED';
}

export function sharesGps(status) {
    return status === 'IN_TRANSIT';
}

export function shouldSendPosition(lastSentAtMs, nowMs, minIntervalMs = GPS_MIN_INTERVAL_MS) {
    return lastSentAtMs === null || nowMs - lastSentAtMs >= minIntervalMs;
}

export function locationPayload(position, pointId = crypto.randomUUID()) {
    return {
        pointId,
        latitude: Number(position.coords.latitude.toFixed(6)),
        longitude: Number(position.coords.longitude.toFixed(6)),
        recordedAt: new Date(position.timestamp).toISOString()
    };
}

export function isRetriableFailure(status) {
    return status === null || status >= 500;
}

export function formatValue(value, unit) {
    if (unit === 'km') return formatDistance(value);
    return value == null ? '—' : `${Number(value)} ${unit}`;
}
