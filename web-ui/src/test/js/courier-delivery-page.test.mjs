import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import * as delivery from '../../main/resources/static/js/components/courier-delivery.js';

const pageScript = readFileSync(new URL(
    '../../main/resources/static/js/pages/courier-delivery-details.js', import.meta.url
), 'utf8').replace(/^import\s*\{[\s\S]*?\}\s*from\s*'[^']+';/, '');

test('retries the initial read while Kafka assignment is not visible yet', async () => {
    const page = await openPage(response(null, 403), response('ASSIGNED'));
    assert.match(page.element('delivery-notice').textContent, /temporarily unavailable/);
    await page.refresh();
    assert.equal(page.element('shipment-status').textContent, 'TO PICK UP');
    assert.equal(page.element('delivery-notice').textContent, '');
    assert.equal(page.requests.length, 2);
    assert.equal(page.requests[1].options.cache, 'no-store');
});

test('refreshes pickup and delivery, then stops GPS and polling', async () => {
    const page = await openPage(response('ASSIGNED'), response('IN_TRANSIT'), response('DELIVERED'));
    await page.refresh();
    assert.equal(page.element('shipment-status').textContent, 'IN TRANSIT');
    assert.equal(page.gps.started, 1);
    await page.refresh();
    assert.equal(page.element('shipment-status').textContent, 'DELIVERED');
    assert.equal(page.gps.stopped, 1);
    assert.equal(page.element('delivery-actions').hidden, true);
    assert.equal(page.timers.size, 0);
});

test('a failed refresh preserves the confirmed status and schedules another read', async () => {
    const page = await openPage(response('ASSIGNED'), new Error('Network unavailable'));
    await page.refresh();
    assert.equal(page.element('shipment-status').textContent, 'TO PICK UP');
    assert.equal(page.timers.size, 1);
    assert.equal(page.gps.started, 0);
});

test('pickup and delivery buttons show server responses and stop polling on completion', async () => {
    const page = await openPage(response('ASSIGNED'), response('IN_TRANSIT'), response('DELIVERED'));
    await page.element('advance-delivery').listeners.get('click')();
    assert.equal(page.requests[1].url, '/api/courier/shipments/15/pickup');
    assert.equal(page.requests[1].options.method, 'POST');
    assert.equal(page.element('advance-label').textContent, 'MARK AS DELIVERED');
    await page.element('advance-delivery').listeners.get('click')();
    assert.equal(page.requests[2].url, '/api/courier/shipments/15/deliver');
    assert.equal(page.element('shipment-status').textContent, 'DELIVERED');
    assert.equal(page.timers.size, 0);
});

test('leaving the page clears GPS and scheduled reads', async () => {
    const page = await openPage(response('IN_TRANSIT'));
    page.events.get('pagehide')();
    assert.equal(page.timers.size, 0);
    assert.equal(page.gps.stopped, 1);
    await page.gps.onPosition({coords: {latitude: 50.45, longitude: 30.52}, timestamp: 0});
    assert.equal(page.requests.length, 1);
});

test('a late GPS response cannot restore the GPS message after delivery', async () => {
    let resolvePoint;
    const pendingPoint = new Promise(resolve => { resolvePoint = resolve; });
    const page = await openPage(response('IN_TRANSIT'), pendingPoint, response('DELIVERED'));
    const sending = page.gps.onPosition({coords: {latitude: 50.45, longitude: 30.52}, timestamp: 0});
    await page.refresh();
    resolvePoint(response('IN_TRANSIT'));
    await sending;
    assert.equal(page.element('gps-status').hidden, true);
    assert.equal(page.element('shipment-status').textContent, 'DELIVERED');
});

test('a pending pickup prevents duplicate actions and overlapping refreshes', async () => {
    let resolvePickup;
    const pendingPickup = new Promise(resolve => { resolvePickup = resolve; });
    const page = await openPage(response('ASSIGNED'), pendingPickup);
    const click = page.element('advance-delivery').listeners.get('click');
    const pickup = click();
    await click();
    await page.refresh();
    assert.equal(page.requests.length, 2);
    resolvePickup(response('IN_TRANSIT'));
    await pickup;
    assert.equal(page.element('shipment-status').textContent, 'IN TRANSIT');
    assert.equal(page.timers.size, 1);
});

test('a late read after leaving the page cannot restart GPS or polling', async () => {
    let resolveShipment;
    const pendingShipment = new Promise(resolve => { resolveShipment = resolve; });
    const page = await openPage(pendingShipment);
    page.events.get('pagehide')();
    resolveShipment(response('IN_TRANSIT'));
    await new Promise(setImmediate);
    assert.equal(page.gps.started, 0);
    assert.equal(page.timers.size, 0);
});

function response(status, httpStatus = 200) {
    return {
        ok: httpStatus < 400,
        status: httpStatus,
        json: async () => httpStatus < 400
            ? {shipmentId: 15, status}
            : {detail: 'Assignment temporarily unavailable'}
    };
}

async function openPage(...responses) {
    const elements = new Map();
    const events = new Map();
    const timers = new Map();
    const requests = [];
    const gps = {started: 0, stopped: 0};
    let timerId = 0;
    const element = id => {
        if (!elements.has(id)) elements.set(id, {
            textContent: '', hidden: false, disabled: false,
            listeners: new Map(), classList: {toggle() {}},
            addEventListener(name, listener) { this.listeners.set(name, listener); }
        });
        return elements.get(id);
    };

    runInNewContext(pageScript, {
        ...delivery,
        document: {
            querySelector: () => ({dataset: {shipmentId: '15'}}),
            getElementById: element
        },
        window: {addEventListener: (name, listener) => events.set(name, listener)},
        navigator: {geolocation: {
            watchPosition(onPosition) { gps.started++; gps.onPosition = onPosition; return 1; },
            clearWatch() { gps.stopped++; }
        }},
        fetch: async (url, options) => {
            requests.push({url, options});
            assert.ok(responses.length, 'Unexpected HTTP request');
            const next = responses.shift();
            if (next instanceof Error) throw next;
            return await next;
        },
        setTimeout: (callback, delay) => {
            assert.equal(delay, 5000);
            timers.set(++timerId, callback);
            return timerId;
        },
        clearTimeout: id => timers.delete(id)
    });
    await new Promise(setImmediate);

    return {
        element, events, timers, requests, gps,
        async refresh() {
            const [id, callback] = timers.entries().next().value;
            timers.delete(id);
            await callback();
        }
    };
}
