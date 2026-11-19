import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../../main/resources/static/js/components/header.js', import.meta.url), 'utf8');

test('client header checks unread once and updates desktop and mobile bells', async () => {
    const result = await openHeader({unread: true});
    assert.deepEqual(result.requests.map(item => item.url), ['/api/notifications/unread']);
    assert.equal(result.requests[0].options.cache, 'no-store');
    assert.ok(result.bells.every(bell => !bell.hidden));
});

test('header hides bells when there are no unread notifications', async () => {
    const result = await openHeader({unread: false});
    assert.ok(result.bells.every(bell => bell.hidden));
});

test('opening notifications marks the loaded boundary once and uses the returned unread status', async () => {
    const result = await openHeader({throughId: '103', unread: false});
    assert.equal(result.requests.length, 1);
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=103');
    assert.equal(result.requests[0].options.method, 'POST');
    assert.ok(result.bells.every(bell => bell.hidden));
});

test('an empty or failed notifications page does not mark anything as viewed', async () => {
    for (const throughId of ['0', undefined]) {
        const result = await openHeader({throughId, unread: true});
        assert.deepEqual(result.requests.map(item => item.url), ['/api/notifications/unread']);
    }
});

test('notifications newer than the loaded boundary keep the header bell visible', async () => {
    const result = await openHeader({throughId: '103', unread: true});
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=103');
    assert.ok(result.bells.every(bell => !bell.hidden));
});

test('failed mark-read request still checks the actual unread status', async () => {
    const result = await openHeader({throughId: '103', unread: true, markFails: true});
    assert.equal(result.requests.length, 2);
    assert.equal(result.requests[1].url, '/api/notifications/unread');
    assert.ok(result.bells.every(bell => !bell.hidden));
});

test('guest and courier headers do not make notification requests', async () => {
    const result = await openHeader({client: false});
    assert.equal(result.requests.length, 0);
});

test('invalid or unavailable unread response does not invent notification state', async () => {
    for (const options of [{statusFails: true}, {unread: {unexpected: true}}]) {
        const result = await openHeader(options);
        assert.ok(result.bells.every(bell => bell.hidden));
        assert.equal(result.warnings.length, 1);
    }
});

test('large notification IDs are sent without JavaScript numeric rounding', async () => {
    const result = await openHeader({throughId: '9007199254740993', unread: false});
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=9007199254740993');
});

async function openHeader({client = true, throughId, unread = false, markFails = false, statusFails = false} = {}) {
    const requests = [];
    const warnings = [];
    const bells = [{hidden: true}, {hidden: true}];
    const link = {dataset: {notificationStatusUrl: '/api/notifications/unread', notificationReadUrl: '/api/notifications/read'}};
    const page = throughId === undefined ? null : {dataset: {notificationReadThrough: throughId}};
    const document = {
        querySelector: selector => selector === '[data-notification-status-url]' ? (client ? link : null) : page,
        querySelectorAll: selector => selector === '[data-notification-bell]' ? bells : []
    };
    const fetch = async (url, options) => {
        requests.push({url, options});
        return {ok: options.method === 'POST' ? !markFails : !statusFails, json: async () => unread};
    };

    await vm.runInNewContext(source, {document, fetch, URLSearchParams, console: {warn: message => warnings.push(message)}});
    return {requests, warnings, bells};
}
