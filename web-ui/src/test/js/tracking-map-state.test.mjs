import test from "node:test";
import assert from "node:assert/strict";

import {
    TrackingLocationStore,
    normalizeTrackingLocation
} from "../../main/resources/static/js/components/tracking-map-state.js";

test("appends newer GPS points and extends route coordinates", () => {
    const store = new TrackingLocationStore(15, [
        location(10, 15, 50.45, 30.52)
    ]);

    const appended = store.append(location(11, 15, 50.46, 30.54));

    assert.equal(appended.locationId, 11);
    assert.deepEqual(store.coordinates(), [
        [30.52, 50.45],
        [30.54, 50.46]
    ]);
});

test("ignores duplicate, stale, foreign and invalid GPS points", () => {
    const store = new TrackingLocationStore(15, [
        location(10, 15, 50.45, 30.52)
    ]);

    assert.equal(store.append(location(10, 15, 50.46, 30.54)), null);
    assert.equal(store.append(location(9, 15, 50.46, 30.54)), null);
    assert.equal(store.append(location(11, 99, 50.46, 30.54)), null);
    assert.equal(store.append(location(11, 15, 91, 30.54)), null);
    assert.equal(store.append(location(11, 15, 50.46, 181)), null);
    assert.equal(store.size(), 1);
});

test("uses MapLibre longitude-latitude coordinate order", () => {
    const normalized = normalizeTrackingLocation(
        location(1, 15, 50.455, 30.53),
        15
    );

    assert.deepEqual(normalized.coordinate, [30.53, 50.455]);
});

function location(locationId, shipmentId, latitude, longitude) {
    return {locationId, shipmentId, latitude, longitude};
}
