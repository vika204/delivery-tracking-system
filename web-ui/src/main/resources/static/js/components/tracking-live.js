const INITIAL_RETRY_DELAY_MS = 1000;
const MAX_RETRY_DELAY_MS = 30000;
const STABLE_CONNECTION_MS = 10000;
const JITTER_RATIO = 0.1;
const BACKFILL_PAGE_SIZE = 100;
const MAX_BACKFILL_LOCATIONS = 500;
const MAX_BUFFERED_SSE_LOCATIONS = 500;

const STATUS = Object.freeze({
    CONNECTING: Object.freeze({state: "connecting", text: "CONNECTING"}),
    LIVE: Object.freeze({state: "live", text: "LIVE"}),
    RECONNECTING: Object.freeze({
        state: "reconnecting",
        text: "RECONNECTING"
    }),
    LOST: Object.freeze({state: "lost", text: "CONNECTION LOST"})
});

function positiveSafeInteger(value) {
    const number = Number(value);

    return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function nonNegativeSafeInteger(value) {
    const number = Number(value);

    return Number.isSafeInteger(number) && number >= 0 ? number : null;
}

function validShipmentId(value) {
    return positiveSafeInteger(value);
}

function updateStatusElement(element, status) {
    if (!element) {
        return;
    }

    element.hidden = false;
    element.dataset.state = status.state;
    const textElement = element.querySelector("[data-stream-status-text]");

    if (textElement) {
        textElement.textContent = status.text;
    }
}

function shipmentStatus(response, shipmentId) {
    if (Number(response?.shipmentId) !== shipmentId ||
        typeof response?.status !== "string") {
        return null;
    }

    const status = response.status.trim().toUpperCase();
    return status.length > 0 ? status : null;
}

function isTerminalStatus(status) {
    return status === "DELIVERED" || status === "CANCELLED";
}

function isPermanentStatusError(error) {
    const status = Number(error?.httpStatus);
    return Number.isInteger(status) && status >= 400 && status < 500;
}

function retryDelay(attempt, random) {
    const exponent = Math.min(attempt, 30);
    const exponentialDelay = Math.min(
        MAX_RETRY_DELAY_MS,
        INITIAL_RETRY_DELAY_MS * (2 ** exponent)
    );
    const randomValue = Math.min(1, Math.max(0, Number(random()) || 0));
    const jitter = 1 - JITTER_RATIO + (2 * JITTER_RATIO * randomValue);

    return Math.min(
        MAX_RETRY_DELAY_MS,
        Math.round(exponentialDelay * jitter)
    );
}

function validateLocationPage(
    page,
    shipmentId,
    afterLocationId,
    requestedLimit
) {
    if (!Array.isArray(page) || page.length > requestedLimit) {
        throw new Error("GPS backfill returned an invalid page");
    }

    let previousLocationId = afterLocationId;

    for (const location of page) {
        const locationId = positiveSafeInteger(location?.locationId);
        const responseShipmentId = positiveSafeInteger(location?.shipmentId);

        if (locationId === null || responseShipmentId !== shipmentId ||
            locationId <= previousLocationId) {
            throw new Error("GPS backfill returned an invalid location");
        }

        previousLocationId = locationId;
    }

    return previousLocationId;
}

export async function loadLocationBackfill({
    shipmentId,
    afterLocationId,
    fetchPage,
    pageSize = BACKFILL_PAGE_SIZE,
    maxLocations = MAX_BACKFILL_LOCATIONS
}) {
    const validShipment = validShipmentId(shipmentId);
    const validAfterLocationId = nonNegativeSafeInteger(afterLocationId);

    if (validShipment === null || validAfterLocationId === null ||
        typeof fetchPage !== "function" ||
        !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 ||
        !Number.isInteger(maxLocations) || maxLocations < 1 ||
        maxLocations > MAX_BACKFILL_LOCATIONS) {
        throw new Error("Invalid GPS backfill configuration");
    }

    const locations = [];
    let cursor = validAfterLocationId;

    while (locations.length < maxLocations) {
        const limit = Math.min(pageSize, maxLocations - locations.length);
        const page = await fetchPage(validShipment, cursor, limit);

        if (page.length === 0) {
            return Object.freeze({
                locations: Object.freeze([...locations]),
                complete: true
            });
        }

        cursor = validateLocationPage(
            page,
            validShipment,
            cursor,
            limit
        );
        locations.push(...page);

        if (page.length < limit) {
            return Object.freeze({
                locations: Object.freeze([...locations]),
                complete: true
            });
        }
    }

    const probe = await fetchPage(validShipment, cursor, 1);
    validateLocationPage(probe, validShipment, cursor, 1);

    return Object.freeze({
        locations: Object.freeze([...locations]),
        complete: probe.length === 0
    });
}

function orderedUniqueLocations(locations, shipmentId) {
    const byLocationId = new Map();

    for (const location of locations) {
        const locationId = positiveSafeInteger(location?.locationId);
        const responseShipmentId = positiveSafeInteger(location?.shipmentId);

        if (locationId !== null && responseShipmentId === shipmentId &&
            !byLocationId.has(locationId)) {
            byLocationId.set(locationId, location);
        }
    }

    return [...byLocationId.values()].sort(
        (left, right) => Number(left.locationId) - Number(right.locationId)
    );
}

async function requestShipmentStatus(shipmentId) {
    const response = await fetch(
        `/api/tracking/shipments/${encodeURIComponent(shipmentId)}/status`,
        {
            headers: {Accept: "application/json"},
            cache: "no-store"
        }
    );

    if (!response.ok) {
        const error = new Error(
            `Shipment status request failed: ${response.status}`
        );
        error.httpStatus = response.status;
        throw error;
    }

    return response.json();
}

async function requestLocationPage(shipmentId, afterLocationId, limit) {
    const query = new URLSearchParams({
        afterLocationId: String(afterLocationId),
        limit: String(limit)
    });
    const response = await fetch(
        `/api/tracking/shipments/${encodeURIComponent(shipmentId)}` +
        `/locations?${query}`,
        {
            headers: {Accept: "application/json"},
            cache: "no-store"
        }
    );

    if (!response.ok) {
        const error = new Error(
            `GPS backfill request failed: ${response.status}`
        );
        error.httpStatus = response.status;
        throw error;
    }

    return response.json();
}

export function startLiveTracking({
    config,
    mapApi,
    statusElement,
    deliveryStatusElement,
    createEventSource,
    fetchStatus,
    fetchBackfill,
    pageTarget,
    setTimer = (callback, delay) => globalThis.setTimeout(callback, delay),
    clearTimer = timer => globalThis.clearTimeout(timer),
    random = Math.random,
    stableConnectionMs = STABLE_CONNECTION_MS
}) {
    const shipmentId = validShipmentId(config?.shipmentId);

    if (config?.streamEnabled !== true || config?.terminal === true ||
        shipmentId === null) {
        return null;
    }

    if (typeof mapApi?.appendLocation !== "function" ||
        typeof mapApi?.appendLocations !== "function" ||
        typeof mapApi?.getLastLocationId !== "function" ||
        typeof mapApi?.setLocationHistoryComplete !== "function" ||
        typeof createEventSource !== "function" ||
        typeof fetchStatus !== "function" ||
        typeof fetchBackfill !== "function" ||
        typeof setTimer !== "function" ||
        typeof clearTimer !== "function" ||
        typeof random !== "function") {
        updateStatusElement(statusElement, STATUS.LOST);
        return null;
    }

    const streamUrl =
        `/api/tracking/shipments/${encodeURIComponent(shipmentId)}/events`;
    let activeSource = null;
    let retryTimer = null;
    let stabilityTimer = null;
    let retryAttempt = 0;
    let stopped = false;
    let connectionVersion = 0;
    let recoveryVersion = 0;
    let recoveryRequest = null;

    const cancelRetryTimer = () => {
        if (retryTimer !== null) {
            clearTimer(retryTimer);
            retryTimer = null;
        }
    };

    const cancelStabilityTimer = () => {
        if (stabilityTimer !== null) {
            clearTimer(stabilityTimer);
            stabilityTimer = null;
        }
    };

    const closeSource = source => {
        if (source && typeof source.close === "function") {
            source.close();
        }
    };

    const closeActiveSource = source => {
        if (source && activeSource !== source) {
            closeSource(source);
            return;
        }

        const current = activeSource;
        activeSource = null;
        closeSource(current);
    };

    const isCurrent = (source, version) => !stopped &&
        activeSource === source && connectionVersion === version;

    const markConnectionStable = (source, version) => {
        if (!isCurrent(source, version)) {
            return;
        }

        retryAttempt = 0;
        cancelStabilityTimer();
    };

    const startStabilityTimer = (source, version) => {
        cancelStabilityTimer();
        stabilityTimer = setTimer(() => {
            stabilityTimer = null;
            markConnectionStable(source, version);
        }, stableConnectionMs);
    };

    const stop = ({terminalStatus = null, showLost = false} = {}) => {
        if (stopped) {
            return;
        }

        stopped = true;
        recoveryVersion += 1;
        recoveryRequest = null;
        cancelRetryTimer();
        cancelStabilityTimer();
        closeActiveSource();

        if (isTerminalStatus(terminalStatus)) {
            if (deliveryStatusElement) {
                deliveryStatusElement.textContent = terminalStatus;
            }

            if (statusElement) {
                statusElement.hidden = true;
            }
        } else if (showLost) {
            updateStatusElement(statusElement, STATUS.LOST);
        }
    };

    let connect;

    const scheduleReconnect = () => {
        if (stopped || retryTimer !== null || activeSource !== null) {
            return;
        }

        updateStatusElement(statusElement, STATUS.RECONNECTING);
        const delay = retryDelay(retryAttempt, random);
        retryAttempt += 1;
        retryTimer = setTimer(() => {
            retryTimer = null;

            if (!stopped) {
                connect(true);
            }
        }, delay);
    };

    const recover = (source, version) => {
        if (!isCurrent(source, version)) {
            return recoveryRequest ?? Promise.resolve(null);
        }

        closeActiveSource(source);
        cancelStabilityTimer();
        updateStatusElement(statusElement, STATUS.RECONNECTING);

        if (recoveryRequest !== null) {
            return recoveryRequest;
        }

        const currentRecovery = ++recoveryVersion;
        recoveryRequest = Promise.resolve()
            .then(() => fetchStatus(shipmentId))
            .then(response => {
                if (stopped || currentRecovery !== recoveryVersion) {
                    return null;
                }

                const status = shipmentStatus(response, shipmentId);

                if (isTerminalStatus(status)) {
                    stop({terminalStatus: status});
                    return status;
                }

                scheduleReconnect();
                return null;
            })
            .catch(error => {
                if (stopped || currentRecovery !== recoveryVersion) {
                    return null;
                }

                if (isPermanentStatusError(error)) {
                    stop({showLost: true});
                } else {
                    scheduleReconnect();
                }

                return null;
            })
            .finally(() => {
                if (currentRecovery === recoveryVersion) {
                    recoveryRequest = null;
                }
            });

        return recoveryRequest;
    };

    connect = reconnecting => {
        if (stopped) {
            return null;
        }

        cancelRetryTimer();
        cancelStabilityTimer();
        closeActiveSource();
        updateStatusElement(
            statusElement,
            reconnecting ? STATUS.RECONNECTING : STATUS.CONNECTING
        );

        let source;

        try {
            source = createEventSource(streamUrl);
        } catch (error) {
            console.warn("Could not open live tracking stream", error);
            scheduleReconnect();
            return null;
        }

        activeSource = source;
        const version = ++connectionVersion;
        const bufferedLocations = [];
        const bufferedLocationIds = new Set();
        let bufferOverflow = false;
        let synchronizing = true;
        let synchronizationRequest = null;

        const synchronize = () => {
            if (!isCurrent(source, version)) {
                return Promise.resolve(null);
            }

            if (synchronizationRequest !== null) {
                return synchronizationRequest;
            }

            const afterLocationId = nonNegativeSafeInteger(
                mapApi.getLastLocationId()
            );

            if (afterLocationId === null) {
                mapApi.setLocationHistoryComplete(false);
                return recover(source, version);
            }

            synchronizationRequest = Promise.resolve()
                .then(() => fetchBackfill(shipmentId, afterLocationId))
                .then(result => {
                    if (!isCurrent(source, version)) {
                        return null;
                    }

                    if (!result || !Array.isArray(result.locations) ||
                        typeof result.complete !== "boolean") {
                        throw new Error("GPS backfill returned an invalid result");
                    }

                    if (!result.complete) {
                        mapApi.appendLocations(result.locations, {
                            updateCurrentPosition: false
                        });
                        mapApi.setLocationHistoryComplete(false);
                        return recover(source, version);
                    }

                    const merged = orderedUniqueLocations(
                        [...result.locations, ...bufferedLocations],
                        shipmentId
                    );

                    if (bufferOverflow) {
                        mapApi.appendLocations(merged, {
                            updateCurrentPosition: false
                        });
                        mapApi.setLocationHistoryComplete(false);
                        return recover(source, version);
                    }

                    synchronizing = false;
                    const appendedCount = mapApi.appendLocations(merged, {
                        updateCurrentPosition: false
                    });
                    mapApi.setLocationHistoryComplete(true);
                    updateStatusElement(statusElement, STATUS.LIVE);

                    if (appendedCount > 0) {
                        markConnectionStable(source, version);
                    } else {
                        startStabilityTimer(source, version);
                    }

                    return appendedCount;
                })
                .catch(error => {
                    if (!isCurrent(source, version)) {
                        return null;
                    }

                    console.warn("Could not restore GPS history", error);
                    mapApi.setLocationHistoryComplete(false);
                    return recover(source, version);
                });

            return synchronizationRequest;
        };

        source.addEventListener("open", synchronize);

        source.addEventListener("location", event => {
            if (!isCurrent(source, version)) {
                return;
            }

            try {
                const location = JSON.parse(event.data);

                if (synchronizing) {
                    const locationId = positiveSafeInteger(
                        location?.locationId
                    );
                    const responseShipmentId = positiveSafeInteger(
                        location?.shipmentId
                    );

                    if (locationId === null ||
                        responseShipmentId !== shipmentId ||
                        bufferedLocationIds.has(locationId)) {
                        return;
                    }

                    if (bufferedLocations.length <
                        MAX_BUFFERED_SSE_LOCATIONS) {
                        bufferedLocations.push(location);
                        bufferedLocationIds.add(locationId);
                    } else {
                        bufferOverflow = true;
                    }

                    return;
                }

                if (mapApi.appendLocation(location) === true) {
                    markConnectionStable(source, version);
                }
            } catch (error) {
                console.warn("Ignored invalid live tracking event", error);
            }
        });

        source.addEventListener(
            "complete",
            () => recover(source, version)
        );
        source.addEventListener(
            "stream-error",
            () => recover(source, version)
        );
        source.addEventListener(
            "error",
            () => recover(source, version)
        );

        return source;
    };

    const leavePage = () => stop();
    pageTarget?.addEventListener("pagehide", leavePage, {once: true});
    pageTarget?.addEventListener("beforeunload", leavePage, {once: true});

    return connect(false);
}

if (typeof window !== "undefined" && typeof document !== "undefined") {
    startLiveTracking({
        config: window.webExpressTracking,
        mapApi: window.webExpressTrackingMap,
        statusElement: document.getElementById("tracking-stream-status"),
        deliveryStatusElement: document.getElementById("delivery-status"),
        createEventSource: typeof window.EventSource === "function"
            ? url => new window.EventSource(url)
            : null,
        fetchStatus: typeof window.fetch === "function"
            ? requestShipmentStatus
            : null,
        fetchBackfill: typeof window.fetch === "function"
            ? (shipmentId, afterLocationId) => loadLocationBackfill({
                shipmentId,
                afterLocationId,
                fetchPage: requestLocationPage
            })
            : null,
        pageTarget: window
    });
}
