import { TEST_COORDINATES } from "../demo/tracking-route.js";

const MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

const mapElement = document.getElementById("live-map");
const errorElement = document.getElementById("map-error");

const prefersReducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
);

function showError(message) {
    errorElement.textContent = message;
    errorElement.hidden = false;
}

function customizeMapStyle(style) {
    for (const layer of style.layers) {
        layer.paint ??= {};

        const id = layer.id.toLowerCase();

        if (layer.type === "background") {
            layer.paint["background-color"] = "#e7d6c2";
        }

        if (layer.type === "fill") {
            layer.paint["fill-color"] =
                /water/.test(id) ? "#bccbc5" :
                    /park|grass|wood|forest/.test(id) ? "#c5c6a8" :
                        /building/.test(id) ? "#d7c3a5" :
                            "#e8d8bf";
        }

        if (layer.type === "line" &&
            /road|highway|bridge|tunnel/.test(id)) {

            layer.paint["line-color"] =
                /casing/.test(id) ? "#d0baa0" : "#fbefdc";
        }

        if (layer.type === "symbol") {
            if (layer.layout?.["text-field"]) {
                layer.paint["text-color"] = "#76624e";
                layer.paint["text-halo-color"] = "#f6e6cf";
            }

            if (/poi/.test(id)) {
                layer.layout = {
                    ...layer.layout,
                    visibility: "none"
                };
            }
        }
    }

    return style;
}

function addBuildings(map) {
    const layers = map.getStyle().layers;

    const existing3d = layers.find(
        layer => layer.type === "fill-extrusion"
    );

    if (existing3d) {
        map.setLayoutProperty(
            existing3d.id,
            "visibility",
            "visible"
        );

        map.setPaintProperty(
            existing3d.id,
            "fill-extrusion-color",
            "#c8ac89"
        );

        return;
    }

    const buildings = layers.find(
        layer => layer.type === "fill" &&
            layer["source-layer"] === "building"
    );

    if (!buildings) {
        return;
    }

    map.addLayer({
        id: "web-express-buildings-3d",
        type: "fill-extrusion",
        source: buildings.source,
        "source-layer": "building",
        minzoom: 13,
        paint: {
            "fill-extrusion-color": "#c8ac89",
            "fill-extrusion-height": [
                "coalesce",
                ["get", "render_height"],
                ["get", "height"],
                9
            ],
            "fill-extrusion-base": [
                "coalesce",
                ["get", "render_min_height"],
                ["get", "min_height"],
                0
            ],
            "fill-extrusion-opacity": 0.85
        }
    });
}

function addRoute(map) {
    const route = {
        type: "Feature",
        geometry: {
            type: "LineString",
            coordinates: TEST_COORDINATES
        }
    };

    map.addSource("route", {
        type: "geojson",
        data: route
    });

    map.addLayer({
        id: "route-outline",
        type: "line",
        source: "route",
        layout: {
            "line-cap": "round",
            "line-join": "round"
        },
        paint: {
            "line-color": "#fff0db",
            "line-width": 9
        }
    });

    map.addLayer({
        id: "route-line",
        type: "line",
        source: "route",
        layout: {
            "line-cap": "round",
            "line-join": "round"
        },
        paint: {
            "line-color": "#bc2c1d",
            "line-width": 4
        }
    });

    map.addSource("travelled", {
        type: "geojson",
        data: {
            type: "Feature",
            geometry: {
                type: "LineString",
                coordinates: [
                    TEST_COORDINATES[0],
                    TEST_COORDINATES[0]
                ]
            }
        }
    });

    map.addLayer({
        id: "travelled-line",
        type: "line",
        source: "travelled",
        layout: {
            "line-cap": "round"
        },
        paint: {
            "line-color": "#fff5dd",
            "line-width": 3,
            "line-opacity": 0.95,
            "line-dasharray": [2, 0.7]
        }
    });
}

function addStopMarker(map, label, coordinates, description) {
    const element = document.createElement("div");

    element.className = "tracking-stop-marker";
    element.textContent = label;

    new maplibregl.Marker({ element })
        .setLngLat(coordinates)
        .setPopup(
            new maplibregl.Popup({ offset: 20 })
                .setText(description)
        )
        .addTo(map);
}

function addCourierMarker(map) {
    const element = document.createElement("div");
    element.className = "tracking-courier-pin";
    element.setAttribute("aria-label", "Demo courier");

    const image = document.createElement("img");
    image.src = mapElement.dataset.spiderUrl;
    image.alt = "Spider courier";

    element.appendChild(image);

    return new maplibregl.Marker({
        element,
        anchor: "bottom",
        pitchAlignment: "viewport",
        rotationAlignment: "viewport",
        subpixelPositioning: true
    })
        .setLngLat(TEST_COORDINATES[0])
        .setPopup(
            new maplibregl.Popup({ offset: 27 })
                .setText("Alex · simulated GPS")
        )
        .addTo(map);
}

async function initializeTrackingMap() {
    if (!mapElement || !errorElement) {
        return;
    }

    try {
        if (!window.maplibregl) {
            throw new Error("MapLibre is unavailable");
        }

        const response = await fetch(MAP_STYLE_URL);

        if (!response.ok) {
            throw new Error("Map style is unavailable");
        }

        const style = customizeMapStyle(await response.json());

        const map = new maplibregl.Map({
            container: "live-map",
            style,
            center: TEST_COORDINATES[10],
            zoom: 15,
            pitch: 52,
            bearing: -18,
            attributionControl: true
        });

        map.addControl(
            new maplibregl.NavigationControl({
                showCompass: true
            }),
            "bottom-right"
        );

        map.addControl(
            new maplibregl.FullscreenControl({
                container: document.querySelector(
                    ".tracking-map-panel"
                )
            }),
            "top-right"
        );

        let loaded = false;

        map.on("error", () => {
            if (!loaded) {
                showError(
                    "The map could not load. Check your internet connection."
                );
            }
        });

        map.on("load", () => {
            try {
                addBuildings(map);
                addRoute(map);

                addStopMarker(
                    map,
                    "A",
                    TEST_COORDINATES[0],
                    "Pickup · test point A"
                );

                addStopMarker(
                    map,
                    "B",
                    TEST_COORDINATES.at(-1),
                    "Destination · test point B"
                );

                const courierMarker = addCourierMarker(map);

                const bounds = new maplibregl.LngLatBounds();

                TEST_COORDINATES.forEach(
                    point => bounds.extend(point)
                );

                map.fitBounds(bounds, {
                    padding: {
                        top: 85,
                        bottom: 65,
                        left: 55,
                        right: 55
                    },
                    maxZoom: 15,
                    bearing: -18,
                    pitch: 52,
                    duration: prefersReducedMotion.matches ? 0 : 700
                });

                loaded = true;
                errorElement.hidden = true;
                window.dispatchEvent(
                    new CustomEvent("web-express:map-ready", {
                        detail: {
                            map,
                            courierMarker
                        }
                    })
                );

            } catch (error) {
                console.error("Map initialization failed:", error);
                showError("The tracking map could not be initialized.");
            }
        });

    } catch (error) {
        console.error("MapLibre error:", error);

        showError(
            "The map could not load. Internet access is required."
        );
    }
}

initializeTrackingMap();