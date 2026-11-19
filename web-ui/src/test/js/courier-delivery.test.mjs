import test from 'node:test';
import assert from 'node:assert/strict';

import {
    formatValue,
    isRetriableFailure,
    locationPayload,
    nextAction,
    sharesGps,
    shouldSendPosition,
    statusClass,
    statusLabel
} from '../../main/resources/static/js/components/courier-delivery.js';

test('labels ASSIGNED shipment as TO PICK UP', () => {
    assert.equal(statusLabel('ASSIGNED'), 'TO PICK UP');
    assert.equal(statusLabel('TO_PICKUP'), 'TO PICK UP');
    assert.equal(statusLabel('IN_TRANSIT'), 'IN TRANSIT');
    assert.equal(statusLabel('UNKNOWN'), '—');
    assert.equal(statusClass('IN_TRANSIT'), 'in-transit');
    assert.equal(statusClass('ASSIGNED'), '');
});

test('offers Confirm pickup before pickup and delivery afterwards', () => {
    assert.deepEqual(nextAction('ASSIGNED'), {action: 'pickup', label: 'CONFIRM PICKUP'});
    assert.deepEqual(nextAction('TO_PICKUP'), {action: 'pickup', label: 'CONFIRM PICKUP'});
    assert.deepEqual(nextAction('IN_TRANSIT'), {action: 'deliver', label: 'MARK AS DELIVERED'});
    assert.equal(nextAction('DELIVERED'), null);
    assert.equal(nextAction('CANCELLED'), null);
});

test('shares GPS only while the parcel is in transit', () => {
    assert.equal(sharesGps('IN_TRANSIT'), true);
    assert.equal(sharesGps('ASSIGNED'), false);
    assert.equal(sharesGps('DELIVERED'), false);
});

test('throttles GPS points', () => {
    assert.equal(shouldSendPosition(null, 1000), true);
    assert.equal(shouldSendPosition(1000, 5999), false);
    assert.equal(shouldSendPosition(1000, 6000), true);
});

test('converts browser position to tracking payload with a point id', () => {
    assert.deepEqual(locationPayload({
        coords: {latitude: 50.4501234567, longitude: 30.5234987654},
        timestamp: Date.parse('2026-11-19T10:00:00Z')
    }, 'point-1'), {pointId: 'point-1', latitude: 50.450123, longitude: 30.523499, recordedAt: '2026-11-19T10:00:00.000Z'});
    const generated = locationPayload({coords: {latitude: 1, longitude: 2}, timestamp: 0});
    assert.match(generated.pointId, /^[0-9a-f-]{36}$/);
});

test('retries the same point only after network or server failures', () => {
    assert.equal(isRetriableFailure(null), true);
    assert.equal(isRetriableFailure(503), true);
    assert.equal(isRetriableFailure(400), false);
    assert.equal(isRetriableFailure(409), false);
});

test('formats detail values', () => {
    assert.equal(formatValue(2.4, 'kg'), '2.4 kg');
    assert.equal(formatValue(null, 'kg'), '—');
});
