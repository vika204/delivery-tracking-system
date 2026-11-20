import test from 'node:test';
import assert from 'node:assert/strict';

import {
    createIdempotencyKeys,
    createRequestFrom,
    formatDistance,
    formatPrice,
    isQuoteProblem,
    isQuoteUsable,
    problemMessage,
    quoteRequestFrom,
    sameRequest
} from '../../main/resources/static/js/components/shipment-creation.js';

const route = {
    pickupAddress: ' Київ, Khreshchatyk 1 ',
    pickupCityCode: 'UA80000000000093317',
    deliveryAddress: 'Львів, Svobody 10',
    deliveryCityCode: 'UA46060250010015970',
    weight: '2.5'
};

test('builds quote request only for a complete route and positive weight', () => {
    assert.deepEqual(quoteRequestFrom(route), {...route, pickupAddress: 'Київ, Khreshchatyk 1', weight: 2.5});
    assert.equal(quoteRequestFrom({...route, deliveryCityCode: ''}), null);
    assert.equal(quoteRequestFrom({...route, weight: '0'}), null);
    assert.equal(quoteRequestFrom({...route, weight: 'abc'}), null);
    assert.equal(Object.hasOwn(quoteRequestFrom(route), 'deliveryType'), false);
});

test('builds create request from the quote and contact details', () => {
    assert.deepEqual(createRequestFrom('q-1', {
        senderName: ' Olena ', senderPhone: '+380501234567',
        recipientName: 'Marta', recipientPhone: '+380671234567',
        length: '30', width: '20', height: '15', weight: 'ignored'
    }), {
        quoteId: 'q-1', senderName: 'Olena', senderPhone: '+380501234567',
        recipientName: 'Marta', recipientPhone: '+380671234567', length: 30, width: 20, height: 15
    });
});

test('reuses idempotency key for a retried request and rotates it when data changes', () => {
    let counter = 0;
    const keys = createIdempotencyKeys(() => `key-${++counter}`);
    const request = createRequestFrom('q-1', {senderName: 'Olena'});

    assert.equal(keys.keyFor(request), 'key-1');
    assert.equal(keys.keyFor(structuredClone(request)), 'key-1');
    assert.equal(keys.keyFor({...request, quoteId: 'q-2'}), 'key-2');
    assert.equal(keys.keyFor({...request, quoteId: 'q-2'}), 'key-2');
});

test('keeps key stable even if the caller mutates the request afterwards', () => {
    let counter = 0;
    const keys = createIdempotencyKeys(() => `key-${++counter}`);
    const request = {quoteId: 'q-1'};
    keys.keyFor(request);
    request.quoteId = 'q-2';

    assert.equal(keys.keyFor({quoteId: 'q-1'}), 'key-1');
    assert.equal(sameRequest({quoteId: 'q-1'}, {quoteId: 'q-2'}), false);
});

test('treats quote as usable only before expiry minus margin', () => {
    const quote = {expiresAt: '2026-11-19T12:00:00Z'};
    const expires = Date.parse(quote.expiresAt);
    assert.equal(isQuoteUsable(quote, expires - 60_000), true);
    assert.equal(isQuoteUsable(quote, expires - 10_000), false);
    assert.equal(isQuoteUsable(null, 0), false);
});

test('recognises problems that require a new quote', () => {
    assert.equal(isQuoteProblem({type: 'urn:delivery:problem:quote-expired'}), true);
    assert.equal(isQuoteProblem({type: 'urn:delivery:problem:quote-already-used'}), true);
    assert.equal(isQuoteProblem({type: 'urn:delivery:problem:validation-failed'}), false);
    assert.equal(isQuoteProblem(null), false);
});

test('formats price, distance and server problems', () => {
    assert.equal(formatPrice(142), '142.00 UAH');
    assert.equal(formatPrice(null), '—');
    assert.equal(formatDistance(7.3), '7.3 km');
    assert.equal(formatDistance(0.02), '20 m');
    assert.equal(formatDistance(0), '0 km');
    assert.equal(formatDistance(null), '—');
    assert.equal(problemMessage({detail: 'Request validation failed',
        errors: [{field: 'recipientPhone', message: 'must be a valid phone number'}]}, 'fallback'),
        'Request validation failed: recipientPhone must be a valid phone number');
    assert.equal(problemMessage({detail: 'Price quote expired'}, 'fallback'), 'Price quote expired');
    assert.equal(problemMessage(null, 'fallback'), 'fallback');
});
