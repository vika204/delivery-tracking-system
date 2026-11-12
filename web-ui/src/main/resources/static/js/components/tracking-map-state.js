function positiveSafeInteger(value) {
    const number = Number(value);

    return Number.isSafeInteger(number) && number > 0
        ? number
        : null;
}

export function normalizeTrackingLocation(location, expectedShipmentId) {
    if (!location || typeof location !== "object") {
        return null;
    }

    const locationId = positiveSafeInteger(location.locationId);
    const shipmentId = positiveSafeInteger(location.shipmentId);
    const latitude = Number(location.latitude);
    const longitude = Number(location.longitude);

    if (locationId === null ||
        shipmentId === null ||
        shipmentId !== expectedShipmentId ||
        !Number.isFinite(latitude) ||
        latitude < -90 ||
        latitude > 90 ||
        !Number.isFinite(longitude) ||
        longitude < -180 ||
        longitude > 180) {
        return null;
    }

    return Object.freeze({
        locationId,
        shipmentId,
        latitude,
        longitude,
        recordedAt: location.recordedAt ?? null,
        coordinate: Object.freeze([longitude, latitude])
    });
}

export class TrackingLocationStore {

    #expectedShipmentId;
    #locations = [];
    #lastLocationId = 0;

    constructor(expectedShipmentId, initialLocations = []) {
        this.#expectedShipmentId = positiveSafeInteger(expectedShipmentId);

        if (Array.isArray(initialLocations)) {
            for (const location of initialLocations) {
                this.append(location);
            }
        }
    }

    append(location) {
        if (this.#expectedShipmentId === null) {
            return null;
        }

        const normalized = normalizeTrackingLocation(
            location,
            this.#expectedShipmentId
        );

        if (normalized === null ||
            normalized.locationId <= this.#lastLocationId) {
            return null;
        }

        this.#locations.push(normalized);
        this.#lastLocationId = normalized.locationId;
        return normalized;
    }

    coordinates() {
        return this.#locations.map(location => [...location.coordinate]);
    }

    lastLocation() {
        return this.#locations.at(-1) ?? null;
    }

    size() {
        return this.#locations.length;
    }
}
