// Чисті функції відображення Courier Dashboard: без DOM, тож тестуються в node.

const STATES = new Set(['AVAILABLE', 'BUSY', 'UNAVAILABLE']);

/** Напис стану з dispatch-service: AVAILABLE / BUSY / UNAVAILABLE. */
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

/** Залишок часу на відповідь; не від'ємний. */
export function secondsUntil(deadlineMs, nowMs) {
    return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
}

/** Повідомлення з ProblemDetail (detail) або запасний текст. */
export function problemMessage(problem, fallback) {
    return problem && typeof problem.detail === 'string' && problem.detail.trim() ? problem.detail : fallback;
}
