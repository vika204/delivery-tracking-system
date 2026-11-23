import {humanizeStatuses} from './courier-delivery.js';

const STATES = new Set(['AVAILABLE', 'BUSY', 'UNAVAILABLE']);

export function availabilityLabel(state) {
    return STATES.has(state) ? state : '—';
}

export function deliveryStatusLabel(delivery) {
    return delivery.pickedUp ? 'IN TRANSIT' : 'TO PICK UP';
}

export function formatWeight(kg) {
    return kg == null ? '—' : `${Number(kg)} kg`;
}

export {formatDistance} from './format-distance.js';

export function formatPrice(uah) {
    return uah == null ? '—' : `₴${Number(uah)}`;
}

export function secondsUntil(deadlineMs, nowMs) {
    return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
}

export function problemMessage(problem, fallback) {
    return problem && typeof problem.detail === 'string' && problem.detail.trim() ? humanizeStatuses(problem.detail) : fallback;
}
