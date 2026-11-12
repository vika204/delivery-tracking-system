import test from "node:test";
import assert from "node:assert/strict";

import {
    startLiveTracking
} from "../../main/resources/static/js/components/tracking-live.js";

test("opens same-origin EventSource and forwards location events", async () => {
    const source = new FakeEventSource();
    const status = fakeStatusElement();
    const deliveryStatus = {textContent: "YOUR COURIER IS ON THE WAY"};
    const received = [];
    let statusRequests = 0;

    const result = startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation: location => received.push(location)},
        statusElement: status.element,
        deliveryStatusElement: deliveryStatus,
        createEventSource: url => {
            source.url = url;
            return source;
        },
        fetchStatus: async shipmentId => {
            statusRequests += 1;
            return {shipmentId, status: "DELIVERED"};
        },
        pageTarget: new FakePageTarget()
    });

    assert.equal(result, source);
    assert.equal(source.url, "/api/tracking/shipments/15/events");
    assert.equal(status.text.textContent, "CONNECTING");

    source.emit("open");
    assert.equal(status.text.textContent, "LIVE");

    source.emit("location", {
        data: JSON.stringify({
            locationId: 11,
            shipmentId: 15,
            latitude: 50.455,
            longitude: 30.53,
            recordedAt: "2026-11-12T13:00:00"
        })
    });

    assert.equal(received.length, 1);
    assert.equal(received[0].locationId, 11);

    await source.emit("complete");
    assert.equal(source.closed, true);
    assert.equal(statusRequests, 1);
    assert.equal(deliveryStatus.textContent, "DELIVERED");
    assert.equal(status.element.hidden, true);
});

test("does not create EventSource without shipment or feature flag", () => {
    let created = 0;
    const createEventSource = () => {
        created += 1;
        return new FakeEventSource();
    };
    const common = {
        mapApi: {appendLocation() {}},
        statusElement: null,
        deliveryStatusElement: null,
        createEventSource,
        fetchStatus: async () => ({shipmentId: 15, status: "DELIVERED"}),
        pageTarget: new FakePageTarget()
    };

    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: null, streamEnabled: true}
    }), null);
    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: 15, streamEnabled: false}
    }), null);
    assert.equal(startLiveTracking({
        ...common,
        config: {shipmentId: 15, streamEnabled: true, terminal: true}
    }), null);
    assert.equal(created, 0);
});

test("closes on stream error without removing existing route state", () => {
    const source = new FakeEventSource();
    const existingRoute = [[30.52, 50.45], [30.53, 50.46]];

    startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation() {
            throw new Error("No location expected");
        }},
        statusElement: null,
        deliveryStatusElement: null,
        createEventSource: () => source,
        fetchStatus: async () => {
            throw new Error("Status must not be requested for stream errors");
        },
        pageTarget: new FakePageTarget()
    });

    source.emit("stream-error");

    assert.equal(source.closed, true);
    assert.deepEqual(existingRoute, [[30.52, 50.45], [30.53, 50.46]]);
});

test("shows CANCELLED after the completion status check", async () => {
    const source = new FakeEventSource();
    const status = fakeStatusElement();
    const deliveryStatus = {textContent: "YOUR COURIER IS ON THE WAY"};

    startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation() {}},
        statusElement: status.element,
        deliveryStatusElement: deliveryStatus,
        createEventSource: () => source,
        fetchStatus: async () => ({shipmentId: 15, status: "cancelled"}),
        pageTarget: new FakePageTarget()
    });

    await source.emit("complete");

    assert.equal(deliveryStatus.textContent, "CANCELLED");
    assert.equal(status.element.hidden, true);
});

test("requests status only once when complete is received repeatedly", async () => {
    const source = new FakeEventSource();
    let statusRequests = 0;

    startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation() {}},
        statusElement: null,
        deliveryStatusElement: {textContent: "IN TRANSIT"},
        createEventSource: () => source,
        fetchStatus: async () => {
            statusRequests += 1;
            return {shipmentId: 15, status: "DELIVERED"};
        },
        pageTarget: new FakePageTarget()
    });

    await Promise.all([
        source.emit("complete"),
        source.emit("complete")
    ]);

    assert.equal(statusRequests, 1);
});

test("keeps the last known status when completion cannot be confirmed", async () => {
    const source = new FakeEventSource();
    const status = fakeStatusElement();
    const deliveryStatus = {textContent: "YOUR COURIER IS ON THE WAY"};
    const existingRoute = [[30.52, 50.45], [30.53, 50.46]];

    startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation() {}},
        statusElement: status.element,
        deliveryStatusElement: deliveryStatus,
        createEventSource: () => source,
        fetchStatus: async () => {
            throw new Error("Backend unavailable");
        },
        pageTarget: new FakePageTarget()
    });

    await source.emit("complete");

    assert.equal(source.closed, true);
    assert.equal(deliveryStatus.textContent, "YOUR COURIER IS ON THE WAY");
    assert.equal(status.text.textContent, "STATUS UPDATE UNAVAILABLE");
    assert.deepEqual(existingRoute, [[30.52, 50.45], [30.53, 50.46]]);
});

test("does not infer delivery from a complete event alone", async () => {
    const source = new FakeEventSource();
    const status = fakeStatusElement();
    const deliveryStatus = {textContent: "YOUR COURIER IS ON THE WAY"};

    startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation() {}},
        statusElement: status.element,
        deliveryStatusElement: deliveryStatus,
        createEventSource: () => source,
        fetchStatus: async () => ({shipmentId: 15, status: "IN_TRANSIT"}),
        pageTarget: new FakePageTarget()
    });

    await source.emit("complete");

    assert.equal(deliveryStatus.textContent, "YOUR COURIER IS ON THE WAY");
    assert.equal(status.text.textContent, "STATUS UPDATE UNAVAILABLE");
});

class FakeEventSource {

    listeners = new Map();
    closed = false;
    url = null;

    addEventListener(name, listener) {
        this.listeners.set(name, listener);
    }

    emit(name, event = {}) {
        return this.listeners.get(name)?.(event);
    }

    close() {
        this.closed = true;
    }
}

class FakePageTarget {

    addEventListener() {
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
