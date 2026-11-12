import test from "node:test";
import assert from "node:assert/strict";

import {
    TrackingMarkerAnimator
} from "../../main/resources/static/js/components/tracking-marker-animator.js";

test("new GPS point cancels an active marker animation", () => {
    const frames = new Map();
    const cancelled = [];
    let nextFrameId = 1;
    const marker = fakeMarker([30.5, 50.4]);
    const animator = new TrackingMarkerAnimator({
        requestFrame: callback => {
            const id = nextFrameId++;
            frames.set(id, callback);
            return id;
        },
        cancelFrame: id => {
            cancelled.push(id);
            frames.delete(id);
        },
        reducedMotion: () => false,
        duration: 1000
    });

    animator.move(marker, [30.6, 50.5]);
    runNextFrame(frames, 0);
    runNextFrame(frames, 500);
    const positionDuringAnimation = [...marker.position];

    animator.move(marker, [30.7, 50.6]);

    assert.equal(cancelled.length, 1);
    assert.deepEqual(marker.position, positionDuringAnimation);
    assert.equal(frames.size, 1);
});

test("reduced motion moves marker without animation frames", () => {
    let requestedFrames = 0;
    const marker = fakeMarker([30.5, 50.4]);
    const animator = new TrackingMarkerAnimator({
        requestFrame: () => {
            requestedFrames += 1;
            return requestedFrames;
        },
        cancelFrame() {},
        reducedMotion: () => true
    });

    animator.move(marker, [30.6, 50.5]);

    assert.deepEqual(marker.position, [30.6, 50.5]);
    assert.equal(requestedFrames, 0);
});

function fakeMarker(initialPosition) {
    return {
        position: [...initialPosition],
        getLngLat() {
            return {lng: this.position[0], lat: this.position[1]};
        },
        setLngLat(position) {
            this.position = [...position];
        }
    };
}

function runNextFrame(frames, timestamp) {
    const [id, callback] = frames.entries().next().value;
    frames.delete(id);
    callback(timestamp);
}
