import test from "node:test";
import assert from "node:assert/strict";

import {
    startLiveTracking
} from "../../main/resources/static/js/components/tracking-live.js";

test("opens same-origin EventSource and forwards location events", () => {
    const source = new FakeEventSource();
    const status = fakeStatusElement();
    const received = [];

    const result = startLiveTracking({
        config: {shipmentId: 15, streamEnabled: true},
        mapApi: {appendLocation: location => received.push(location)},
        statusElement: status.element,
        createEventSource: url => {
            source.url = url;
            return source;
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

    source.emit("complete");
    assert.equal(source.closed, true);
    assert.equal(status.text.textContent, "CONNECTION LOST");
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
        createEventSource,
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
        createEventSource: () => source,
        pageTarget: new FakePageTarget()
    });

    source.emit("stream-error");

    assert.equal(source.closed, true);
    assert.deepEqual(existingRoute, [[30.52, 50.45], [30.53, 50.46]]);
});

class FakeEventSource {

    listeners = new Map();
    closed = false;
    url = null;

    addEventListener(name, listener) {
        this.listeners.set(name, listener);
    }

    emit(name, event = {}) {
        this.listeners.get(name)?.(event);
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
        querySelector: () => text
    };

    return {element, text};
}
