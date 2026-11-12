const STATUS = Object.freeze({
    CONNECTING: Object.freeze({state: "connecting", text: "CONNECTING"}),
    LIVE: Object.freeze({state: "live", text: "LIVE"}),
    LOST: Object.freeze({state: "lost", text: "CONNECTION LOST"}),
    CHECKING: Object.freeze({state: "connecting", text: "CHECKING STATUS"}),
    UNAVAILABLE: Object.freeze({
        state: "lost",
        text: "STATUS UPDATE UNAVAILABLE"
    })
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

    element.dataset.state = status.state;
    const textElement = element.querySelector("[data-stream-status-text]");

    if (textElement) {
        textElement.textContent = status.text;
    }
}

function terminalStatus(response, shipmentId) {
    if (Number(response?.shipmentId) !== shipmentId ||
        typeof response?.status !== "string") {
        return null;
    }

    const status = response.status.trim().toUpperCase();

    return status === "DELIVERED" || status === "CANCELLED"
        ? status
        : null;
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
        throw new Error(`Shipment status request failed: ${response.status}`);
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
    pageTarget
}) {
    const shipmentId = validShipmentId(config?.shipmentId);

    if (config?.streamEnabled !== true || config?.terminal === true ||
        shipmentId === null) {
        return null;
    }

    if (typeof mapApi?.appendLocation !== "function" ||
        typeof createEventSource !== "function" ||
        typeof fetchStatus !== "function") {
        updateStatusElement(statusElement, STATUS.LOST);
        return null;
    }

    updateStatusElement(statusElement, STATUS.CONNECTING);

    let source;

    try {
        source = createEventSource(
            `/api/tracking/shipments/${encodeURIComponent(shipmentId)}/events`
        );
    } catch (error) {
        console.warn("Could not open live tracking stream", error);
        updateStatusElement(statusElement, STATUS.LOST);
        return null;
    }
    let closed = false;

    const close = () => {
        if (!closed) {
            closed = true;
            source.close();
        }
    };

    let completionRequest = null;

    const complete = () => {
        if (completionRequest) {
            return completionRequest;
        }

        close();
        updateStatusElement(statusElement, STATUS.CHECKING);

        completionRequest = Promise.resolve()
            .then(() => fetchStatus(shipmentId))
            .then(response => {
                const status = terminalStatus(response, shipmentId);

                if (!status) {
                    throw new Error("Shipment status is not terminal");
                }

                if (deliveryStatusElement) {
                    deliveryStatusElement.textContent = status;
                }

                if (statusElement) {
                    statusElement.hidden = true;
                }

                return status;
            })
            .catch(error => {
                console.warn("Could not refresh shipment status", error);
                updateStatusElement(statusElement, STATUS.UNAVAILABLE);
                return null;
            });

        return completionRequest;
    };

    const closeWithConnectionLost = () => {
        if (closed) {
            return;
        }

        updateStatusElement(statusElement, STATUS.LOST);
        close();
    };

    source.addEventListener("open", () => {
        if (!closed) {
            updateStatusElement(statusElement, STATUS.LIVE);
        }
    });

    source.addEventListener("location", event => {
        if (closed) {
            return;
        }

        try {
            mapApi.appendLocation(JSON.parse(event.data));
        } catch (error) {
            console.warn("Ignored invalid live tracking event", error);
        }
    });

    source.addEventListener("complete", complete);
    source.addEventListener("stream-error", closeWithConnectionLost);
    source.addEventListener("error", closeWithConnectionLost);

    pageTarget?.addEventListener("pagehide", close, {once: true});
    pageTarget?.addEventListener("beforeunload", close, {once: true});

    return source;
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
