const INITIAL_RETRY_DELAY_MS = 1000;
const MAX_RETRY_DELAY_MS = 30000;
const STABLE_CONNECTION_MS = 10000;
const JITTER_RATIO = 0.1;

const STATUS = Object.freeze({
    CONNECTING: Object.freeze({state: "connecting", text: "CONNECTING"}),
    LIVE: Object.freeze({state: "live", text: "LIVE"}),
    RECONNECTING: Object.freeze({
        state: "reconnecting",
        text: "RECONNECTING"
    }),
    LOST: Object.freeze({state: "lost", text: "CONNECTION LOST"})
});

function validShipmentId(value) {
    const shipmentId = Number(value);

    return Number.isSafeInteger(shipmentId) && shipmentId > 0
        ? shipmentId
        : null;
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

export function startLiveTracking({
    config,
    mapApi,
    statusElement,
    deliveryStatusElement,
    createEventSource,
    fetchStatus,
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
        typeof createEventSource !== "function" ||
        typeof fetchStatus !== "function" ||
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

        source.addEventListener("open", () => {
            if (!isCurrent(source, version)) {
                return;
            }

            updateStatusElement(statusElement, STATUS.LIVE);
            cancelStabilityTimer();
            stabilityTimer = setTimer(() => {
                stabilityTimer = null;
                markConnectionStable(source, version);
            }, stableConnectionMs);
        });

        source.addEventListener("location", event => {
            if (!isCurrent(source, version)) {
                return;
            }

            try {
                const appended = mapApi.appendLocation(JSON.parse(event.data));

                if (appended === true) {
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
        pageTarget: window
    });
}
