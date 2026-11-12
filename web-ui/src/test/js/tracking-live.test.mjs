import test from "node:test";
import assert from "node:assert/strict";

import {
    startLiveTracking
} from "../../main/resources/static/js/components/tracking-live.js";

test("opens the first EventSource and receives GPS updates", () => {
    const harness = createHarness();

    assert.equal(harness.result, harness.sources[0]);
    assert.equal(
        harness.sources[0].url,
        "/api/tracking/shipments/15/events"
    );
    assert.equal(harness.status.text.textContent, "CONNECTING");

    harness.sources[0].emit("open");
    assert.equal(harness.status.text.textContent, "LIVE");

    harness.sources[0].emit("location", locationEvent(11));

    assert.deepEqual(
        harness.received.map(location => location.locationId),
        [11]
    );
    assert.equal(harness.timers.size, 0);
});

test("temporary SSE failure closes the source and enters RECONNECTING", async () => {
    const harness = createHarness();
    const source = harness.sources[0];

    source.emit("open");
    await source.emit("error");

    assert.equal(source.closed, true);
    assert.equal(harness.status.text.textContent, "RECONNECTING");
    assert.deepEqual(harness.timers.delays(), [1000]);
});

test("reconnects after the retry delay with only one active EventSource", async () => {
    const harness = createHarness();
    const first = harness.sources[0];

    await first.emit("error");
    harness.timers.runNext();

    assert.equal(harness.sources.length, 2);
    assert.equal(first.closed, true);
    assert.equal(activeSourceCount(harness.sources), 1);
    assert.equal(harness.status.text.textContent, "RECONNECTING");

    harness.sources[1].emit("open");
    assert.equal(harness.status.text.textContent, "LIVE");
});

test("uses exponential backoff capped at thirty seconds", async () => {
    const harness = createHarness();
    const expectedDelays = [1000, 2000, 4000, 8000, 16000, 30000, 30000];

    for (const expectedDelay of expectedDelays) {
        const current = harness.sources.at(-1);

        await current.emit("error");
        assert.deepEqual(harness.timers.delays(), [expectedDelay]);
        harness.timers.runNext();
    }

    assert.equal(activeSourceCount(harness.sources), 1);
});

test("adds bounded jitter to retry delays", async () => {
    const shorter = createHarness({random: () => 0});
    const longer = createHarness({random: () => 1});

    await shorter.sources[0].emit("error");
    await longer.sources[0].emit("error");

    assert.deepEqual(shorter.timers.delays(), [900]);
    assert.deepEqual(longer.timers.delays(), [1100]);
});

test("a short onopen does not reset the backoff", async () => {
    const harness = createHarness();

    await harness.sources[0].emit("error");
    harness.timers.runNext();
    harness.sources[1].emit("open");
    await harness.sources[1].emit("error");

    assert.deepEqual(harness.timers.delays(), [2000]);
});

test("a successfully appended GPS point resets the backoff", async () => {
    const harness = createHarness();

    await harness.sources[0].emit("error");
    harness.timers.runNext();
    harness.sources[1].emit("open");
    harness.sources[1].emit("location", locationEvent(12));
    await harness.sources[1].emit("error");

    assert.deepEqual(harness.timers.delays(), [1000]);
});

test("a stable open connection resets the backoff", async () => {
    const harness = createHarness();

    await harness.sources[0].emit("error");
    harness.timers.runNext();
    harness.sources[1].emit("open");
    assert.deepEqual(harness.timers.delays(), [10000]);

    harness.timers.runNext();
    await harness.sources[1].emit("error");

    assert.deepEqual(harness.timers.delays(), [1000]);
});

test("repeated errors create one status check and one retry timer", async () => {
    let statusRequests = 0;
    const harness = createHarness({
        fetchStatus: async () => {
            statusRequests += 1;
            return activeShipment();
        }
    });
    const source = harness.sources[0];

    await Promise.all([
        source.emit("error"),
        source.emit("error"),
        source.emit("stream-error")
    ]);

    assert.equal(statusRequests, 1);
    assert.equal(harness.timers.size, 1);
    assert.deepEqual(harness.timers.delays(), [1000]);
});

test("GPS delivery resumes after reconnect without clearing the route", async () => {
    const route = [[30.52, 50.45], [30.53, 50.46]];
    const received = [];
    const harness = createHarness({
        mapApi: {
            route,
            appendLocation(location) {
                received.push(location);
                this.route.push([location.longitude, location.latitude]);
                return true;
            }
        },
        received
    });

    await harness.sources[0].emit("stream-error");
    assert.deepEqual(route, [[30.52, 50.45], [30.53, 50.46]]);

    harness.timers.runNext();
    harness.sources[1].emit("open");
    harness.sources[1].emit("location", locationEvent(12));

    assert.equal(received.length, 1);
    assert.deepEqual(route, [
        [30.52, 50.45],
        [30.53, 50.46],
        [30.53, 50.455]
    ]);
});

test("does not start for a terminal shipment or a disabled feature flag", () => {
    let created = 0;
    const common = baseOptions({
        createEventSource: () => {
            created += 1;
            return new FakeEventSource();
        }
    });

    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: 15, streamEnabled: false}
    }), null);
    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: 15, streamEnabled: true, terminal: true}
    }), null);
    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: "invalid", streamEnabled: true}
    }), null);
    assert.equal(created, 0);
});

test("leaving the page cancels retry and prevents another connection", async () => {
    const harness = createHarness();

    await harness.sources[0].emit("error");
    assert.equal(harness.timers.size, 1);

    harness.page.emit("pagehide");

    assert.equal(harness.timers.size, 0);
    assert.equal(harness.sources.length, 1);
});

test("complete stops tracking after DELIVERED is confirmed", async () => {
    let statusRequests = 0;
    const harness = createHarness({
        fetchStatus: async () => {
            statusRequests += 1;
            return {shipmentId: 15, status: "DELIVERED"};
        }
    });
    const source = harness.sources[0];

    await Promise.all([
        source.emit("complete"),
        source.emit("complete")
    ]);

    assert.equal(source.closed, true);
    assert.equal(statusRequests, 1);
    assert.equal(harness.deliveryStatus.textContent, "DELIVERED");
    assert.equal(harness.status.element.hidden, true);
    assert.equal(harness.timers.size, 0);
});

test("complete also stops tracking after CANCELLED is confirmed", async () => {
    const harness = createHarness({
        fetchStatus: async () => ({shipmentId: 15, status: "cancelled"})
    });

    await harness.sources[0].emit("complete");

    assert.equal(harness.deliveryStatus.textContent, "CANCELLED");
    assert.equal(harness.status.element.hidden, true);
    assert.equal(harness.timers.size, 0);
});

test("complete with a non-terminal status reconnects safely", async () => {
    const harness = createHarness();

    await harness.sources[0].emit("complete");

    assert.equal(
        harness.deliveryStatus.textContent,
        "YOUR COURIER IS ON THE WAY"
    );
    assert.equal(harness.status.text.textContent, "RECONNECTING");
    assert.deepEqual(harness.timers.delays(), [1000]);
});

test("temporary status failure reconnects but permanent rejection stops", async () => {
    const temporary = createHarness({
        fetchStatus: async () => {
            throw new Error("Network unavailable");
        }
    });

    await temporary.sources[0].emit("error");
    assert.deepEqual(temporary.timers.delays(), [1000]);

    const permanent = createHarness({
        fetchStatus: async () => {
            const error = new Error("Forbidden");
            error.httpStatus = 403;
            throw error;
        }
    });

    await permanent.sources[0].emit("error");
    assert.equal(permanent.timers.size, 0);
    assert.equal(permanent.status.text.textContent, "CONNECTION LOST");
});

test("callbacks from an old EventSource cannot affect a newer connection", async () => {
    const harness = createHarness();
    const oldSource = harness.sources[0];

    await oldSource.emit("error");
    harness.timers.runNext();
    const currentSource = harness.sources[1];

    oldSource.emit("open");
    oldSource.emit("location", locationEvent(99));
    await oldSource.emit("complete");

    assert.equal(harness.received.length, 0);
    assert.equal(currentSource.closed, false);
    assert.equal(harness.timers.size, 0);
    assert.equal(harness.status.text.textContent, "RECONNECTING");

    currentSource.emit("open");
    assert.equal(harness.status.text.textContent, "LIVE");
});

function createHarness(overrides = {}) {
    const sources = [];
    const timers = new FakeTimers();
    const page = new FakePageTarget();
    const status = fakeStatusElement();
    const deliveryStatus = {textContent: "YOUR COURIER IS ON THE WAY"};
    const received = overrides.received ?? [];
    const mapApi = overrides.mapApi ?? {
        appendLocation(location) {
            received.push(location);
            return true;
        }
    };
    const options = baseOptions({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi,
        statusElement: status.element,
        deliveryStatusElement: deliveryStatus,
        createEventSource: url => {
            const source = new FakeEventSource();
            source.url = url;
            sources.push(source);
            return source;
        },
        fetchStatus: async () => activeShipment(),
        pageTarget: page,
        setTimer: timers.setTimer,
        clearTimer: timers.clearTimer,
        random: () => 0.5,
        stableConnectionMs: 10000,
        ...overrides
    });

    return {
        result: startLiveTracking(options),
        sources,
        timers,
        page,
        status,
        deliveryStatus,
        received
    };
}

function baseOptions(overrides = {}) {
    return {
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation: () => true},
        statusElement: null,
        deliveryStatusElement: null,
        createEventSource: () => new FakeEventSource(),
        fetchStatus: async () => activeShipment(),
        pageTarget: new FakePageTarget(),
        ...overrides
    };
}

function activeShipment() {
    return {shipmentId: 15, status: "IN_TRANSIT"};
}

function locationEvent(locationId) {
    return {
        data: JSON.stringify({
            locationId,
            shipmentId: 15,
            latitude: 50.455,
            longitude: 30.53,
            recordedAt: "2026-11-12T13:00:00"
        })
    };
}

function activeSourceCount(sources) {
    return sources.filter(source => !source.closed).length;
}

class FakeEventSource {

    listeners = new Map();
    closed = false;
    closeCount = 0;
    url = null;

    addEventListener(name, listener) {
        this.listeners.set(name, listener);
    }

    emit(name, event = {}) {
        return this.listeners.get(name)?.(event);
    }

    close() {
        this.closed = true;
        this.closeCount += 1;
    }
}

class FakePageTarget {

    listeners = new Map();

    addEventListener(name, listener) {
        this.listeners.set(name, listener);
    }

    emit(name) {
        return this.listeners.get(name)?.();
    }
}

class FakeTimers {

    tasks = new Map();
    nextId = 1;

    setTimer = (callback, delay) => {
        const id = this.nextId++;
        this.tasks.set(id, {callback, delay});
        return id;
    };

    clearTimer = id => {
        this.tasks.delete(id);
    };

    get size() {
        return this.tasks.size;
    }

    delays() {
        return [...this.tasks.values()].map(task => task.delay);
    }

    runNext() {
        const next = this.tasks.entries().next().value;

        assert.ok(next, "Expected a scheduled timer");
        const [id, task] = next;
        this.tasks.delete(id);
        return task.callback();
    }
}

function fakeStatusElement() {
    const text = {textContent: ""};
    const element = {
        dataset: {},
        hidden: false,
        querySelector: () => text
    };

    return {element, text};
}
