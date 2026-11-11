import "../components/tracking-map.js";
import { TEST_COORDINATES } from "./tracking-route.js";

const reduceMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
);

const duration = (TEST_COORDINATES.length - 1) * 5;

const ui = {
    follow: document.getElementById("follow"),
    pause: document.getElementById("pause"),
    replay: document.getElementById("restart"),
    overview: document.getElementById("overview"),
    view: document.getElementById("toggle-3d"),
    slider: document.getElementById("progress"),
    progressLabel: document.getElementById("progress-label"),
    currentGps: document.getElementById("gps-current"),
    coordinates: document.getElementById("coordinates"),
    download: document.getElementById("download"),
    eta: document.getElementById("eta"),
    status: document.getElementById("delivery-status"),
    message: document.getElementById("tracking-message")
};

// Text representation of simulated GPS points.

const coordinatesText =
    "# WEB EXPRESS — simulated courier GPS\n" +
    "# elapsed_seconds,latitude,longitude\n" +
    TEST_COORDINATES.map((point, index) =>
        `${index * 5},${point[1].toFixed(6)},${point[0].toFixed(6)}`
    ).join("\n");

ui.coordinates.textContent = coordinatesText;

// Download GPS coordinates.

ui.download.addEventListener("click", () => {
    const url = URL.createObjectURL(
        new Blob([coordinatesText], { type: "text/plain" })
    );

    const link = document.createElement("a");

    link.href = url;
    link.download = "courier-test-coordinates.txt";
    link.click();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
});

// Tracking form is currently a demo.

document.querySelector(".tracking-search")
    .addEventListener("submit", event => {

        const value = event.currentTarget
            .elements.namedItem("shipmentId")
            .value.trim();

        if (!/^[1-9]\d*$/.test(value)) {
            event.preventDefault();

            ui.message.textContent =
                "Enter a numeric shipment ID to search the backend. " +
                "WE-204-081 is a demo number.";
        }
    });

// Start playback only after MapLibre is ready.

window.addEventListener("web-express:map-ready", event => {

    const { map, courierMarker } = event.detail;

    let playing = !reduceMotion.matches;
    let elapsed = 0;
    let previousFrame = null;
    let following = false;
    let view3d = true;
    let lastCameraUpdate = 0;

    // Enable controls.

    [
        ui.follow,
        ui.pause,
        ui.replay,
        ui.overview,
        ui.view,
        ui.slider
    ].forEach(control => {
        control.disabled = false;
    });

    function updatePauseLabel() {
        ui.pause.textContent =
            elapsed >= duration ? "REPLAY" :
                playing ? "PAUSE" : "PLAY";
    }

    function setFollowing(enabled) {
        following = enabled;

        ui.follow.textContent = enabled
            ? "FOLLOWING ✓"
            : "FOLLOW COURIER";

        ui.follow.setAttribute(
            "aria-pressed",
            String(enabled)
        );

        if (enabled) {
            map.easeTo({
                center: courierMarker.getLngLat(),
                zoom: 16.3,
                duration: reduceMotion.matches ? 0 : 800
            });
        }
    }

    // Render current position along the route.

    function render() {
        const fraction = Math.min(elapsed / duration, 1);

        const position =
            fraction * (TEST_COORDINATES.length - 1);

        const index = Math.min(
            Math.floor(position),
            TEST_COORDINATES.length - 2
        );

        const t = position - index;

        const from = TEST_COORDINATES[index];
        const to = TEST_COORDINATES[index + 1];

        // Interpolate between two GPS points.

        const point = [
            from[0] + (to[0] - from[0]) * t,
            from[1] + (to[1] - from[1]) * t
        ];

        courierMarker.setLngLat(point);

        // Update travelled route.

        map.getSource("travelled").setData({
            type: "Feature",
            geometry: {
                type: "LineString",
                coordinates: [
                    ...TEST_COORDINATES.slice(0, index + 1),
                    point
                ]
            }
        });

        // Update UI.

        ui.slider.value = String(
            Math.round(fraction * 1000)
        );

        ui.progressLabel.textContent =
            `${Math.round(fraction * 100)}%`;

        ui.currentGps.textContent =
            `${point[1].toFixed(5)}, ${point[0].toFixed(5)}`;

        ui.eta.textContent = fraction === 1
            ? "0"
            : String(Math.max(
                1,
                Math.ceil((1 - fraction) * 12)
            ));

        ui.status.textContent =
            "YOUR COURIER IS ON THE WAY";

        // Camera follows the courier.

        if (
            following &&
            playing &&
            performance.now() - lastCameraUpdate > 650
        ) {
            lastCameraUpdate = performance.now();

            map.easeTo({
                center: point,
                duration: reduceMotion.matches ? 0 : 700,
                easing: x => x
            });
        }

        if (fraction === 1) {
            playing = false;
        }

        updatePauseLabel();
    }

    // Follow courier.

    ui.follow.addEventListener("click", () => {
        setFollowing(!following);
    });

    // Pause or continue playback.

    ui.pause.addEventListener("click", () => {
        if (elapsed >= duration) {
            elapsed = 0;
        }

        playing = !playing;
        render();
    });

    // Replay from the beginning.

    ui.replay.addEventListener("click", () => {
        elapsed = 0;
        playing = !reduceMotion.matches;
        previousFrame = null;

        render();
    });

    // Show the entire route.

    ui.overview.addEventListener("click", () => {
        setFollowing(false);

        const bounds = new maplibregl.LngLatBounds();

        TEST_COORDINATES.forEach(point => {
            bounds.extend(point);
        });

        map.fitBounds(bounds, {
            padding: {
                top: 85,
                bottom: 65,
                left: 55,
                right: 55
            },
            maxZoom: 15,
            duration: reduceMotion.matches ? 0 : 700
        });
    });

    // Toggle 2D / 3D view.

    ui.view.addEventListener("click", () => {
        view3d = !view3d;

        map.easeTo({
            pitch: view3d ? 52 : 0,
            bearing: view3d ? -18 : 0,
            duration: reduceMotion.matches ? 0 : 800
        });

        ui.view.textContent = view3d
            ? "2D VIEW"
            : "3D VIEW";

        ui.view.setAttribute(
            "aria-pressed",
            String(view3d)
        );
    });

    // Manual progress selection.

    ui.slider.addEventListener("input", () => {
        elapsed =
            Number(ui.slider.value) / 1000 * duration;

        render();
    });

    // Stop following on manual camera interaction.

    map.on("dragstart", () => setFollowing(false));

    map.on("zoomstart", event => {
        if (event.originalEvent) {
            setFollowing(false);
        }
    });

    map.on("rotatestart", event => {
        if (event.originalEvent) {
            setFollowing(false);
        }
    });

    document.addEventListener("visibilitychange", () => {
        previousFrame = null;
    });

    reduceMotion.addEventListener("change", () => {
        if (reduceMotion.matches) {
            playing = false;
            updatePauseLabel();
        }
    });

    // Animation loop.

    function tick(now) {
        if (previousFrame !== null && playing) {
            elapsed = Math.min(
                duration,
                elapsed + Math.min(
                    (now - previousFrame) / 1000,
                    0.1
                )
            );

            render();
        }

        previousFrame = now;
        requestAnimationFrame(tick);
    }

    render();
    requestAnimationFrame(tick);

}, { once: true });