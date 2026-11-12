const STATUS = Object.freeze({
    CONNECTING: Object.freeze({state: "connecting", text: "CONNECTING"}),
    LIVE: Object.freeze({state: "live", text: "LIVE"}),
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

    element.dataset.state = status.state;
    const textElement = element.querySelector("[data-stream-status-text]");

    if (textElement) {
        textElement.textContent = status.text;
    }
}

export function startLiveTracking({
    config,
    mapApi,
    statusElement,
    createEventSource,
    pageTarget
}) {
    const shipmentId = validShipmentId(config?.shipmentId);

    if (config?.streamEnabled !== true || shipmentId === null) {
        return null;
    }

    if (typeof mapApi?.appendLocation !== "function" ||
        typeof createEventSource !== "function") {
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

    source.addEventListener("complete", () => {
        updateStatusElement(statusElement, STATUS.LOST);
        close();
    });

    source.addEventListener("stream-error", () => {
        updateStatusElement(statusElement, STATUS.LOST);
        close();
    });

    source.addEventListener("error", () => {
        updateStatusElement(statusElement, STATUS.LOST);
        close();
    });

    pageTarget?.addEventListener("pagehide", close, {once: true});
    pageTarget?.addEventListener("beforeunload", close, {once: true});

    return source;
}

if (typeof window !== "undefined" && typeof document !== "undefined") {
    startLiveTracking({
        config: window.webExpressTracking,
        mapApi: window.webExpressTrackingMap,
        statusElement: document.getElementById("tracking-stream-status"),
        createEventSource: typeof window.EventSource === "function"
            ? url => new window.EventSource(url)
            : null,
        pageTarget: window
    });
}
