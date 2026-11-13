import test from 'node:test';
import assert from 'node:assert/strict';

import {
    buildFullAddress,
    shipmentCreationErrorMessage
} from '../../main/resources/static/js/pages/create-shipment.js';

test('builds the existing shipment address from selected settlement and street', () => {
    assert.equal(
        buildFullAddress('Київ', 'Khreshchatyk St, 1'),
        'Київ, Khreshchatyk St, 1'
    );
});

test('does not build a partial address', () => {
    assert.equal(buildFullAddress('Київ', ''), '');
    assert.equal(buildFullAddress('', 'Khreshchatyk St, 1'), '');
});

test('provides a safe shipment creation failure message', () => {
    assert.equal(
        shipmentCreationErrorMessage(),
        'We could not create your shipment. Check your connection and try again. Your form data is still here.'
    );
});
