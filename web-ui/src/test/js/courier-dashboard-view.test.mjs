import test from 'node:test';
import assert from 'node:assert/strict';

import {
    availabilityLabel,
    deliveryStatusLabel,
    formatDistance,
    formatPrice,
    formatWeight,
    problemMessage,
    secondsUntil
} from '../../main/resources/static/js/components/courier-dashboard-view.js';

test('shows availability state reported by dispatch-service', () => {
    assert.equal(availabilityLabel('AVAILABLE'), 'AVAILABLE');
    assert.equal(availabilityLabel('BUSY'), 'BUSY');
    assert.equal(availabilityLabel('UNAVAILABLE'), 'UNAVAILABLE');
    assert.equal(availabilityLabel(undefined), '—');
    assert.equal(availabilityLabel('SOMETHING_ELSE'), '—');
});

test('labels delivery by pickup progress', () => {
    assert.equal(deliveryStatusLabel({pickedUp: false}), 'TO PICK UP');
    assert.equal(deliveryStatusLabel({pickedUp: true}), 'IN TRANSIT');
});

test('formats offer details and tolerates missing values', () => {
    assert.equal(formatWeight(4.2), '4.2 kg');
    assert.equal(formatDistance(7.25), '7.25 km');
    assert.equal(formatDistance(0.02), '20 m');
    assert.equal(formatDistance(0.99), '990 m');
    assert.equal(formatDistance(1), '1 km');
    assert.equal(formatPrice(142), '₴142');
    assert.equal(formatWeight(null), '—');
    assert.equal(formatDistance(undefined), '—');
    assert.equal(formatPrice(null), '—');
});

test('counts down offer time without going negative', () => {
    assert.equal(secondsUntil(60_000, 0), 60);
    assert.equal(secondsUntil(1_500, 1_000), 1);
    assert.equal(secondsUntil(1_000, 5_000), 0);
});

test('uses problem detail from the server when present', () => {
    assert.equal(problemMessage({detail: 'Offer 5 has expired'}, 'fallback'), 'Offer 5 has expired');
    assert.equal(problemMessage({detail: '  '}, 'fallback'), 'fallback');
    assert.equal(problemMessage(null, 'fallback'), 'fallback');
});
