// Чисті функції форми «Create Shipment»: без DOM і мережі, тож тестуються в node.

const QUOTE_PROBLEM = 'urn:delivery:problem:quote-';

const text = value => String(value ?? '').trim();

function positiveNumber(value) {
    const number = Number(text(value));
    return text(value) !== '' && Number.isFinite(number) && number > 0 ? number : null;
}

/** Тіло запиту вартості або null, поки маршрут чи вага неповні. */
export function quoteRequestFrom(fields) {
    const request = {
        pickupAddress: text(fields.pickupAddress),
        pickupCityCode: text(fields.pickupCityCode),
        deliveryAddress: text(fields.deliveryAddress),
        deliveryCityCode: text(fields.deliveryCityCode),
        weight: positiveNumber(fields.weight),
        deliveryType: text(fields.deliveryType) || 'STANDARD'
    };
    const complete = request.pickupAddress && request.pickupCityCode
        && request.deliveryAddress && request.deliveryCityCode && request.weight !== null;
    return complete ? request : null;
}

/** Підтвердження за розрахованою пропозицією: маршрут і ціну shipment-service бере з quoteId. */
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

/** Пропозиція ще дійсна з запасом, щоб не підтверджувати ту, що спливе дорогою. */
export function isQuoteUsable(quote, nowMs, marginMs = 30_000) {
    return Boolean(quote?.expiresAt) && Date.parse(quote.expiresAt) - marginMs > nowMs;
}

/**
 * Idempotency-Key на одне підтвердження: повтор того самого запиту (після збою мережі чи
 * подвійного кліку) іде з тим самим ключем, тож shipment-service поверне вже створену посилку.
 * Змінені дані — новий ключ.
 */
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

export function formatDistance(km) {
    return km == null ? '—' : `${Number(km)} km`;
}

/** Пропозицію треба розрахувати заново: прострочена, використана або не знайдена. */
export function isQuoteProblem(problem) {
    return typeof problem?.type === 'string' && problem.type.startsWith(QUOTE_PROBLEM);
}

/** Причина з ProblemDetail разом з помилками полів, якщо вони є. */
export function problemMessage(problem, fallback) {
    const detail = text(problem?.detail);
    if (!detail) return fallback;
    const fields = Array.isArray(problem.errors)
        ? problem.errors.map(error => `${error.field} ${error.message}`).join('; ')
        : '';
    return fields ? `${detail}: ${fields}` : detail;
}
