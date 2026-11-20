import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../../main/resources/static/js/components/header.js', import.meta.url), 'utf8');

test('client header checks unread once and updates desktop and mobile indicators', async () => {
    const result = await openHeader({unread: true});
    assert.deepEqual(result.requests.map(item => item.url), ['/api/notifications/unread']);
    assert.equal(result.requests[0].options.cache, 'no-store');
    assert.ok(result.indicators.every(indicator => !indicator.hidden));
});

test('header hides indicators when there are no unread notifications', async () => {
    const result = await openHeader({unread: false});
    assert.ok(result.indicators.every(indicator => indicator.hidden));
});

test('opening notifications marks the loaded boundary once and uses the returned unread status', async () => {
    const result = await openHeader({throughId: '103', unread: false});
    assert.equal(result.requests.length, 1);
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=103');
    assert.equal(result.requests[0].options.method, 'POST');
    assert.ok(result.indicators.every(indicator => indicator.hidden));
});

test('an empty or failed notifications page does not mark anything as viewed', async () => {
    for (const throughId of ['0', undefined]) {
        const result = await openHeader({throughId, unread: true});
        assert.deepEqual(result.requests.map(item => item.url), ['/api/notifications/unread']);
    }
});

test('notifications newer than the loaded boundary keep the header indicator visible', async () => {
    const result = await openHeader({throughId: '103', unread: true});
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=103');
    assert.ok(result.indicators.every(indicator => !indicator.hidden));
});

test('failed mark-read request still checks the actual unread status', async () => {
    const result = await openHeader({throughId: '103', unread: true, markFails: true});
    assert.equal(result.requests.length, 2);
    assert.equal(result.requests[1].url, '/api/notifications/unread');
    assert.ok(result.indicators.every(indicator => !indicator.hidden));
});

test('guest and courier headers do not make notification requests', async () => {
    const result = await openHeader({client: false});
    assert.equal(result.requests.length, 0);
});

test('invalid or unavailable unread response does not invent notification state', async () => {
    for (const options of [{statusFails: true}, {unread: {unexpected: true}}]) {
        const result = await openHeader(options);
        assert.ok(result.indicators.every(indicator => indicator.hidden));
        assert.equal(result.warnings.length, 1);
    }
});

test('large notification IDs are sent without JavaScript numeric rounding', async () => {
    const result = await openHeader({throughId: '9007199254740993', unread: false});
    assert.equal(result.requests[0].url, '/api/notifications/read?throughId=9007199254740993');
});

test('client header discovers new unread notifications on the next five-second check', async () => {
    const result = await openHeader();
    assert.equal(result.timers[0].delay, 5000);
    result.state.unread = true;
    await result.tick();
    assert.equal(result.requests.length, 2);
    assert.ok(result.indicators.every(indicator => !indicator.hidden));
});

test('notification polling adds new cards once and preserves existing highlights', async () => {
    const result = await openHeader({throughId: '103'});
    const original = result.page.list.cards[0];
    original.highlighted = true;
    result.state.nextIds = ['104', '103'];
    result.state.nextThrough = '104';
    await result.tick();
    assert.deepEqual(result.page.list.cards.map(card => card.dataset.notificationId), ['104', '103']);
    assert.equal(result.page.list.cards[1], original);
    assert.equal(result.page.list.cards[1].highlighted, true);
    assert.ok(result.requests.some(request => request.url === '/api/notifications/read?throughId=104'));

    await result.tick();
    assert.equal(result.page.list.cards.length, 2);
    assert.equal(result.requests.filter(request => request.options.method === 'POST').length, 2);
});

test('notification refresh failure keeps the current list and recovers on the next check', async () => {
    const result = await openHeader({throughId: '103'});
    const original = result.page.list.cards[0];
    result.state.refreshFails = true;
    await result.tick();
    assert.equal(result.page.list.cards[0], original);
    assert.equal(result.page.error.hidden, false);
    result.state.refreshFails = false;
    await result.tick();
    assert.equal(result.page.error.hidden, true);
});

test('hidden pages do not fetch or mark notifications until they are visible', async () => {
    const result = await openHeader({throughId: '103', hidden: true});
    assert.equal(result.requests.length, 0);
    await result.tick();
    assert.equal(result.requests.length, 0);
    result.document.hidden = false;
    await result.tick();
    assert.ok(result.requests.some(request => request.url === '/api/notifications/read?throughId=103'));
});

test('polling uses only the bounded latest list returned by the server', async () => {
    const result = await openHeader({throughId: '1'});
    result.state.nextIds = Array.from({length: 50}, (_, index) => String(51 - index));
    result.state.nextThrough = '51';
    await result.tick();
    assert.equal(result.page.list.cards.length, 50);
    assert.equal(result.page.list.cards.some(card => card.dataset.notificationId === '1'), false);
    assert.equal(result.page.limit.hidden, false);
});

async function openHeader({client = true, throughId, unread = false, markFails = false, statusFails = false, hidden = false} = {}) {
    const requests = [];
    const warnings = [];
    const timers = [];
    const indicators = [{hidden: true}, {hidden: true}];
    const link = {dataset: {notificationStatusUrl: '/api/notifications/unread', notificationReadUrl: '/api/notifications/read'}};
    const ids = throughId && throughId !== '0' ? [throughId] : [];
    const state = {unread, markFails, statusFails, refreshFails: false, nextIds: ids, nextThrough: throughId ?? '0'};
    function makePage(cardIds, boundary) {
        const list = {
            cards: cardIds.map(id => ({dataset: {notificationId: id}})),
            get children() { return this.cards; },
            querySelectorAll() { return this.cards; },
            replaceChildren(...cards) { this.cards = cards; }
        };
        const error = {hidden: true};
        const limit = {hidden: true};
        return {
            dataset: {notificationReadThrough: boundary, notificationRefreshUrl: '/notifications?fragment=true'},
            list, error, limit,
            querySelector: selector => selector === '.parcel-list' ? list : selector === '.notifications-limit' ? limit : error
        };
    }
    const page = throughId === undefined ? null : makePage(ids, throughId);
    const document = {
        hidden,
        querySelector: selector => selector === '[data-notification-status-url]' ? (client ? link : null) : page,
        querySelectorAll: selector => selector === '[data-notification-indicator]' ? indicators : []
    };
    const fetch = async (url, options = {}) => {
        requests.push({url, options});
        if (url === '/notifications?fragment=true') {
            return {ok: !state.refreshFails, text: async () => ''};
        }
        return {ok: options.method === 'POST' ? !state.markFails : !state.statusFails, json: async () => state.unread};
    };

    await vm.runInNewContext(source, {
        document, fetch, URLSearchParams,
        DOMParser: class { parseFromString() { return {querySelector: () => makePage(state.nextIds, state.nextThrough)}; } },
        setTimeout: (callback, delay) => { timers.push({callback, delay}); },
        console: {warn: message => warnings.push(message)}
    });
    return {requests, warnings, indicators, timers, page, state, document,
        tick: async () => timers.shift().callback()};
}
