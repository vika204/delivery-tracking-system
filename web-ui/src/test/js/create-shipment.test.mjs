import test from 'node:test';
import assert from 'node:assert/strict';

import {buildFullAddress} from '../../main/resources/static/js/pages/create-shipment.js';

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
