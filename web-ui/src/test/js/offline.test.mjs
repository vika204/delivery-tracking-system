import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const staticRoot = new URL('../../main/resources/static/', import.meta.url);
const source = await readFile(new URL('service-worker.js', staticRoot), 'utf8');
const registration = await readFile(new URL('js/components/offline.js', staticRoot), 'utf8');
const origin = 'https://web-express.test';
const cacheName = 'web-express-offline-v2';

test('installation precaches the standalone game and its local resources', async () => {
    const worker = createWorker();
    await worker.install();
    assert.deepEqual([...worker.stored.get(cacheName).keys()], [
        `${origin}/offline.html`, `${origin}/js/components/offline.js`, `${origin}/images/spider-icon.svg`
    ]);
});

test('a failed precache rejects installation', async () => {
    const worker = createWorker({failInstall: true});
    await assert.rejects(worker.install(), /Asset unavailable/);
});

test('activation removes only outdated offline caches and controls the first visit', async () => {
    const worker = createWorker();
    worker.stored.set('web-express-offline-v0', new Map());
    worker.stored.set('another-app-cache', new Map());
    await worker.install();
    await worker.activate();
    assert.equal(worker.stored.has('web-express-offline-v0'), false);
    assert.equal(worker.stored.has('another-app-cache'), true);
    assert.equal(worker.claimed(), true);
});

test('healthy pages come from the network and personal content is not cached', async () => {
    const worker = createWorker({response: () => new Response('private shipment details')});
    await worker.install();
    const response = await worker.request('/my-parcels');
    assert.equal(await response.text(), 'private shipment details');
    assert.equal(worker.stored.get(cacheName).size, 3);
    assert.equal(worker.stored.get(cacheName).has(`${origin}/my-parcels`), false);
});

test('a navigation network failure serves the cached game with status 503', async () => {
    const worker = createWorker({offline: true});
    await worker.install();
    const response = await worker.request('/tracking?shipmentId=15');
    assert.equal(response.status, 503);
    assert.match(response.headers.get('Content-Type'), /text\/html/);
    const html = await response.text();
    assert.match(html, /SERVER TEMPORARILY UNAVAILABLE/);
    assert.match(html, />SPIN<\/button>/);
    assert.doesNotMatch(html, /КРУТИТИ|(?:src|href)=["']https?:\/\/|(?:@import|url\()\s*["']?https?:\/\//);
});

for (const status of [502, 503, 504]) {
    test(`HTTP ${status} navigation serves the offline game`, async () => {
        const worker = createWorker({response: () => new Response('upstream error', {status})});
        await worker.install();
        const response = await worker.request('/tracking');
        assert.equal(response.status, 503);
        assert.match(await response.text(), /id="machine"/);
    });
}

for (const status of [400, 401, 403, 404, 500]) {
    test(`HTTP ${status} is preserved without replacing its page`, async () => {
        const worker = createWorker({response: () => new Response('original page', {status})});
        await worker.install();
        const response = await worker.request('/original-page');
        assert.equal(response.status, status);
        assert.equal(await response.text(), 'original page');
    });
}

test('API, SSE, health checks, mutations and other origins bypass the worker', async () => {
    const worker = createWorker({offline: true});
    for (const [url, options] of [
        ['/api/tracking/shipments/15/status', {}],
        ['/api/tracking/shipments/15/events', {mode: 'cors'}],
        ['/actuator/health', {}],
        ['/ship', {method: 'POST'}],
        ['https://other.test/page', {}]
    ]) {
        assert.equal(await worker.request(url, options), undefined);
    }
    assert.equal(worker.fetches.length, 0);
    assert.equal(worker.stored.size, 0);
});

test('the game registration script and favicon remain available offline', async () => {
    const worker = createWorker({offline: true});
    await worker.install();
    for (const path of ['/js/components/offline.js', '/images/spider-icon.svg']) {
        const response = await worker.request(path, {mode: 'same-origin'});
        assert.equal(response.status, 200);
        assert.ok((await response.text()).length > 0);
    }
    assert.equal(worker.fetches.length, 0);
});

test('an evicted cache still produces a neutral unavailable response', async () => {
    const worker = createWorker({offline: true});
    const response = await worker.request('/profile');
    assert.equal(response.status, 503);
    assert.match(response.headers.get('Content-Type'), /text\/plain/);
    assert.equal(await response.text(), 'SERVER TEMPORARILY UNAVAILABLE');
});

test('registration starts immediately and unsupported browsers keep working', () => {
    const paths = [];
    vm.runInNewContext(registration, {navigator: {serviceWorker: {
        register(path) { paths.push(path); return Promise.resolve(); }
    }}, console});
    assert.deepEqual(paths, ['/service-worker.js']);
    assert.doesNotThrow(() => vm.runInNewContext(registration, {navigator: {}, console}));
});

function createWorker(options = {}) {
    const handlers = new Map();
    const stored = new Map();
    const fetches = [];
    let claimed = false;
    const key = request => new URL(typeof request === 'string' ? request : request.url, origin).href;
    const caches = {
        async keys() { return [...stored.keys()]; },
        async delete(name) { return stored.delete(name); },
        async open(name) {
            if (!stored.has(name)) stored.set(name, new Map());
            const entries = stored.get(name);
            return {
                async addAll(paths) {
                    if (options.failInstall) throw new Error('Asset unavailable');
                    const assets = await Promise.all(paths.map(async path => [key(path),
                        new Response(await readFile(new URL(path.slice(1), staticRoot), 'utf8'))]));
                    for (const [url, response] of assets) entries.set(url, response);
                },
                async match(request) { return entries.get(key(request))?.clone(); }
            };
        }
    };
    vm.runInNewContext(source, {
        URL, Response, caches,
        self: {location: {origin}, clients: {async claim() { claimed = true; }},
            addEventListener(name, handler) { handlers.set(name, handler); }},
        async fetch(request) {
            fetches.push(request);
            if (options.offline) throw new TypeError('Network unavailable');
            return options.response?.() ?? new Response('network response');
        }
    });
    async function lifecycle(name) {
        let task;
        handlers.get(name)({waitUntil(promise) { task = promise; }});
        await task;
    }
    return {
        stored, fetches, claimed: () => claimed,
        install: () => lifecycle('install'),
        activate: () => lifecycle('activate'),
        async request(path, options = {}) {
            let response;
            const request = {url: new URL(path, origin).href, method: 'GET', mode: 'navigate', ...options};
            handlers.get('fetch')({request, respondWith(promise) { response = promise; }});
            return response;
        }
    };
}
