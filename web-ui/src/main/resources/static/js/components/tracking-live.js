import {normalizeTrackingLocation} from "./tracking-map-state.js";

const INITIAL_RETRY_DELAY_MS = 1000;
const MAX_RETRY_DELAY_MS = 30000;
const STABLE_CONNECTION_MS = 10000;
const JITTER_RATIO = 0.1;
const BACKFILL_PAGE_SIZE = 100;
const MAX_BACKFILL_LOCATIONS = 500;

const STATUS = {
    CONNECTING: {state: "connecting", text: "CONNECTING"},
    LIVE: {state: "live", text: "LIVE"},
    RECONNECTING: {state: "reconnecting", text: "RECONNECTING"},
    LOST: {state: "lost", text: "CONNECTION LOST"}
};

function updateStatusElement(element, status) {
    if (element) {
        element.hidden = false;
        element.dataset.state = status.state;
        element.querySelector("[data-stream-status-text]").textContent = status.text;
    }
}

function statusLabel(status) {
    return ({
        WAITING_FOR_COURIER: "WAITING FOR COURIER",
        ASSIGNED: "COURIER ASSIGNED",
        IN_TRANSIT: "YOUR COURIER IS ON THE WAY"
    })[status] ?? status.replaceAll("_", " ");
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

export async function loadLocationBackfill({
    shipmentId,
    afterLocationId,
    fetchPage,
    pageSize = BACKFILL_PAGE_SIZE,
    maxLocations = MAX_BACKFILL_LOCATIONS
}) {
    const locations = [];
    let cursor = afterLocationId;

    while (locations.length < maxLocations) {
        const limit = Math.min(pageSize, maxLocations - locations.length);
        const page = await fetchPage(shipmentId, cursor, limit);

        if (!Array.isArray(page) || page.length > limit) {
            throw new Error("Invalid GPS history response");
        }
        for (const location of page) {
            const locationId = Number(location?.locationId);
            if (!Number.isSafeInteger(locationId) || locationId <= cursor ||
                Number(location?.shipmentId) !== shipmentId) {
                throw new Error("Invalid GPS history location");
            }
            cursor = locationId;
        }
        locations.push(...page);

        if (page.length < limit) {
            return {locations, complete: true};
        }
    }

    return {locations, complete: false};
}

async function requestShipmentStatus(shipmentId) {
    const response = await fetch(`/api/tracking/shipments/${shipmentId}/status`, {
        headers: {Accept: "application/json"},
        cache: "no-store"
    });
    if (!response.ok) {
        const error = new Error(`Shipment status request failed: ${response.status}`);
        error.httpStatus = response.status;
        throw error;
    }
    return response.json();
}

async function requestLocationPage(shipmentId, afterLocationId, limit) {
    const query = new URLSearchParams({afterLocationId, limit});
    const response = await fetch(`/api/tracking/shipments/${shipmentId}/locations?${query}`, {
        headers: {Accept: "application/json"},
        cache: "no-store"
    });
    if (!response.ok) {
        const error = new Error(`GPS backfill request failed: ${response.status}`);
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
    const shipmentId = Number(config?.shipmentId);
    if (config?.streamEnabled !== true || config.terminal === true ||
        !Number.isSafeInteger(shipmentId) || shipmentId <= 0) {
        return null;
    }
    if (!mapApi || !createEventSource || !fetchStatus || !fetchBackfill) {
        updateStatusElement(statusElement, STATUS.LOST);
        return null;
    }

    let source = null;
    let retryTimer = null;
    let stabilityTimer = null;
    let retryAttempt = 0;
    let stopped = false;
    let terminalStatus = null;
    let historyReady = false;
    let lastReceivedLocationId = 0;
    let historyRequest = null;
    let statusRequest = null;

    function closeSource() {
        const previousSource = source;
        source = null;
        clearTimer(stabilityTimer);
        stabilityTimer = null;
        previousSource?.close();
    }

    function resetRetry() {
        retryAttempt = 0;
        clearTimer(stabilityTimer);
        stabilityTimer = null;
    }

    function stop() {
        stopped = true;
        clearTimer(retryTimer);
        retryTimer = null;
        closeSource();
    }

    function scheduleRecovery() {
        if (!stopped && retryTimer === null) {
            if (!terminalStatus) updateStatusElement(statusElement, STATUS.RECONNECTING);
            const delay = retryDelay(retryAttempt++, random);
            retryTimer = setTimer(() => {
                retryTimer = null;
                if (stopped) return;
                if (!terminalStatus && source === null) return connect();
                return recover();
            }, delay);
        }
    }

    function handleFailure(error) {
        if (stopped) return;
        if (!terminalStatus) updateStatusElement(statusElement, STATUS.LOST);
        if (error.httpStatus >= 400 && error.httpStatus < 500) {
            stop();
        } else {
            scheduleRecovery();
        }
    }

    function refreshStatus() {
        if (statusRequest) return statusRequest;
        statusRequest = Promise.resolve()
            .then(() => fetchStatus(shipmentId))
            .then(response => {
                if (stopped) return;
                if (Number(response?.shipmentId) !== shipmentId ||
                    typeof response?.status !== "string" || !response.status.trim()) {
                    throw new Error("Invalid shipment status response");
                }
                const status = response.status.trim().toUpperCase();
                if (deliveryStatusElement) deliveryStatusElement.textContent = statusLabel(status);
                if (status === "DELIVERED" || status === "CANCELLED") {
                    terminalStatus = status;
                    closeSource();
                    if (statusElement) statusElement.hidden = true;
                }
            })
            .finally(() => { statusRequest = null; });
        return statusRequest;
    }

    function restoreHistory() {
        if (historyRequest) return historyRequest;
        const terminalAtStart = terminalStatus;
        historyReady = false;
        historyRequest = Promise.resolve()
            .then(() => fetchBackfill(shipmentId, mapApi.getLastLocationId()))
            .then(result => {
                if (stopped) return;
                const appended = mapApi.appendLocations(result.locations, {updateCurrentPosition: false});
                const complete = result.complete &&
                    mapApi.getLastLocationId() >= lastReceivedLocationId;
                mapApi.setLocationHistoryComplete(complete);
                if (!complete) {
                    scheduleRecovery();
                } else if (terminalStatus) {
                    if (terminalAtStart) stop();
                    else scheduleRecovery();
                } else if (source?.readyState === 1) {
                    historyReady = true;
                    clearTimer(retryTimer);
                    retryTimer = null;
                    updateStatusElement(statusElement, STATUS.LIVE);
                    if (appended > 0) {
                        resetRetry();
                    } else {
                        clearTimer(stabilityTimer);
                        stabilityTimer = setTimer(resetRetry, stableConnectionMs);
                    }
                }
            })
            .catch(error => {
                if (!stopped) mapApi.setLocationHistoryComplete(false);
                handleFailure(error);
            })
            .finally(() => { historyRequest = null; });
        return historyRequest;
    }

    function recover() {
        if (stopped) return Promise.resolve();
        const statusCheck = terminalStatus ? Promise.resolve() : refreshStatus();
        return statusCheck.then(() => {
            if (stopped) return;
            if (terminalStatus || source?.readyState === 1) return restoreHistory();
            if (source === null) scheduleRecovery();
        }).catch(handleFailure);
    }

    function connect() {
        if (stopped || terminalStatus) return null;
        let currentSource;
        try {
            currentSource = createEventSource(`/api/tracking/shipments/${shipmentId}/events`);
        } catch (error) {
            handleFailure(error);
            return null;
        }
        source = currentSource;
        const isCurrent = () => !stopped && source === currentSource;

        currentSource.addEventListener("open", () => {
            if (!isCurrent()) return;
            historyReady = false;
            return (historyRequest ?? Promise.resolve()).then(() => {
                if (!isCurrent()) return;
                historyReady = false;
                return recover();
            });
        });

        currentSource.addEventListener("location", event => {
            if (!isCurrent()) return;
            let location;
            try {
                location = normalizeTrackingLocation(JSON.parse(event.data), shipmentId);
            } catch {
                return;
            }
            if (!location) return;
            lastReceivedLocationId = Math.max(lastReceivedLocationId, location.locationId);
            if (historyReady && mapApi.appendLocation(location)) resetRetry();
        });

        const streamEnded = () => {
            if (!isCurrent()) return;
            historyReady = false;
            closeSource();
            updateStatusElement(statusElement, STATUS.RECONNECTING);
            return recover();
        };
        currentSource.addEventListener("complete", streamEnded);
        currentSource.addEventListener("stream-error", streamEnded);
        currentSource.addEventListener("error", streamEnded);
        return currentSource;
    }

    pageTarget?.addEventListener("pagehide", stop, {once: true});
    pageTarget?.addEventListener("beforeunload", stop, {once: true});
    updateStatusElement(statusElement, STATUS.CONNECTING);
    return connect();
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
        fetchStatus: typeof window.fetch === "function" ? requestShipmentStatus : null,
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
