
const text = value => String(value ?? '').trim();

function positiveNumber(value) {
    const number = Number(text(value));
    return text(value) !== '' && Number.isFinite(number) && number > 0 ? number : null;
}

export function quoteRequestFrom(fields) {
    const request = {
        pickupAddress: text(fields.pickupAddress),
        pickupCityCode: text(fields.pickupCityCode),
        deliveryAddress: text(fields.deliveryAddress),
        deliveryCityCode: text(fields.deliveryCityCode),
        weight: positiveNumber(fields.weight),
    };
    const complete = request.pickupAddress && request.pickupCityCode
        && request.deliveryAddress && request.deliveryCityCode && request.weight !== null;
    return complete ? request : null;
}

export function createRequestFrom(quoteId, fields) {
    return {
        quoteId,
        senderName: text(fields.senderName),
        senderPhone: text(fields.senderPhone),
        recipientName: text(fields.recipientName),
        recipientPhone: text(fields.recipientPhone),
        length: positiveNumber(fields.length),
        width: positiveNumber(fields.width),
        height: positiveNumber(fields.height)
    };
}

export function sameRequest(a, b) {
    return Boolean(a && b) && JSON.stringify(a) === JSON.stringify(b);
}

export function createIdempotencyKeys(newKey = () => crypto.randomUUID()) {
    let last = null;
    return {
        keyFor(request) {
            if (!last || !sameRequest(last.request, request)) {
                last = {request: structuredClone(request), key: newKey()};
            }
            return last.key;
        }
    };
}

export function formatPrice(uah) {
    return uah == null ? '—' : `${Number(uah).toFixed(2)} UAH`;
}

export {formatDistance} from './format-distance.js';

export function problemMessage(problem, fallback) {
    const detail = text(problem?.detail);
    if (!detail) return fallback;
    const fields = Array.isArray(problem.errors)
        ? problem.errors.map(error => `${error.field} ${error.message}`).join('; ')
        : '';
    return fields ? `${detail}: ${fields}` : detail;
}
