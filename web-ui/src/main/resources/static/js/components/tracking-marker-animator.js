export class TrackingMarkerAnimator {

    #requestFrame;
    #cancelFrame;
    #reducedMotion;
    #duration;
    #activeFrame = null;

    constructor({
        requestFrame,
        cancelFrame,
        reducedMotion,
        duration = 1000
    }) {
        this.#requestFrame = requestFrame;
        this.#cancelFrame = cancelFrame;
        this.#reducedMotion = reducedMotion;
        this.#duration = duration;
    }

    move(marker, coordinate) {
        if (this.#activeFrame !== null) {
            this.#cancelFrame(this.#activeFrame);
            this.#activeFrame = null;
        }

        const current = marker.getLngLat();
        const start = [current.lng, current.lat];

        if (this.#reducedMotion() ||
            (start[0] === coordinate[0] && start[1] === coordinate[1])) {
            marker.setLngLat(coordinate);
            return;
        }

        let startedAt = null;

        const animate = timestamp => {
            startedAt ??= timestamp;
            const progress = Math.min(
                (timestamp - startedAt) / this.#duration,
                1
            );
            const eased = 1 - Math.pow(1 - progress, 3);

            marker.setLngLat([
                start[0] + (coordinate[0] - start[0]) * eased,
                start[1] + (coordinate[1] - start[1]) * eased
            ]);

            if (progress < 1) {
                this.#activeFrame = this.#requestFrame(animate);
            } else {
                this.#activeFrame = null;
                marker.setLngLat(coordinate);
            }
        };

        this.#activeFrame = this.#requestFrame(animate);
    }
}
