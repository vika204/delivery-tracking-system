const MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const DEFAULT_MAP_CENTER = [31.1656, 48.3794];
const ROUTE_SOURCE_ID = "web-express-tracking-route";
const ROUTE_LAYER_ID = "web-express-tracking-route-line";

const mapElement = document.getElementById("live-map");
const errorElement = document.getElementById("map-error");
const followButton = document.getElementById("follow");
const overviewButton = document.getElementById("overview");
const toggle3dButton = document.getElementById("toggle-3d");

function readTrackingData() {
    const data = window.webExpressTracking ?? {};
    const locations = Array.isArray(data.locations)
        ? data.locations
            .filter(location =>
                Number.isFinite(Number(location.latitude)) &&
                Number.isFinite(Number(location.longitude)) &&
                Number(location.latitude) >= -90 &&
                Number(location.latitude) <= 90 &&
                Number(location.longitude) >= -180 &&
                Number(location.longitude) <= 180
            )
            .map(location => [
                Number(location.longitude),
                Number(location.latitude)
            ])
        : [];

    return {
        coordinates: locations,
        locationDataAvailable: data.locationDataAvailable === true,
        locationHistoryComplete: data.locationHistoryComplete === true
    };
}

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
        map.setLayoutProperty(existing3d.id, "visibility", "visible");
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

function addRoute(map, coordinates) {
    if (coordinates.length < 2) {
        return;
    }

    map.addSource(ROUTE_SOURCE_ID, {
        type: "geojson",
        data: {
            type: "Feature",
            properties: {},
            geometry: {
                type: "LineString",
                coordinates
            }
        }
    });

    map.addLayer({
        id: ROUTE_LAYER_ID,
        type: "line",
        source: ROUTE_SOURCE_ID,
        layout: {
            "line-cap": "round",
            "line-join": "round"
        },
        paint: {
            "line-color": "#bc2c1d",
            "line-width": 5,
            "line-opacity": 0.85
        }
    });
}

function addCourierMarker(map, coordinate) {
    const markerElement = document.createElement("div");
    markerElement.className = "tracking-courier-pin";
    markerElement.setAttribute("aria-label", "Last known courier location");

    const image = document.createElement("img");
    image.src = mapElement.dataset.spiderUrl;
    image.alt = "";
    markerElement.appendChild(image);

    new maplibregl.Marker({
        element: markerElement,
        anchor: "bottom"
    })
        .setLngLat(coordinate)
        .addTo(map);
}

function fitRoute(map, coordinates) {
    if (coordinates.length < 2) {
        return;
    }

    const bounds = coordinates.slice(1).reduce(
        (routeBounds, coordinate) => routeBounds.extend(coordinate),
        new maplibregl.LngLatBounds(coordinates[0], coordinates[0])
    );

    map.fitBounds(bounds, {
        padding: 70,
        maxZoom: 16,
        duration: 800
    });
}

function activateControls(
    map,
    coordinates,
    locationDataAvailable,
    locationHistoryComplete
) {
    if (!locationDataAvailable || coordinates.length === 0) {
        return;
    }

    const lastCoordinate = coordinates.at(-1);

    if (locationHistoryComplete) {
        followButton.disabled = false;
        followButton.addEventListener("click", () => {
            map.easeTo({
                center: lastCoordinate,
                zoom: Math.max(map.getZoom(), 15),
                duration: 700
            });
        });
    }

    if (coordinates.length >= 2) {
        overviewButton.disabled = false;
        overviewButton.addEventListener("click", () => {
            fitRoute(map, coordinates);
        });
    }

    toggle3dButton.disabled = false;
    let is3d = true;

    toggle3dButton.addEventListener("click", () => {
        is3d = !is3d;
        map.easeTo({
            pitch: is3d ? 35 : 0,
            duration: 500
        });
        toggle3dButton.textContent = is3d ? "2D VIEW" : "3D VIEW";
        toggle3dButton.setAttribute("aria-pressed", String(is3d));
    });
}

async function initializeTrackingMap() {
    if (!mapElement || !errorElement ||
        !followButton || !overviewButton || !toggle3dButton) {
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
        const trackingData = readTrackingData();
        const lastCoordinate = trackingData.coordinates.at(-1);
        const map = new maplibregl.Map({
            container: "live-map",
            style,
            center: trackingData.locationHistoryComplete && lastCoordinate
                ? lastCoordinate
                : DEFAULT_MAP_CENTER,
            zoom: trackingData.locationHistoryComplete && lastCoordinate
                ? 14
                : 5.5,
            pitch: 35,
            bearing: 0,
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
                container: document.querySelector(".tracking-map-panel")
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
                addRoute(map, trackingData.coordinates);

                if (trackingData.locationHistoryComplete && lastCoordinate) {
                    addCourierMarker(map, lastCoordinate);
                }

                activateControls(
                    map,
                    trackingData.coordinates,
                    trackingData.locationDataAvailable,
                    trackingData.locationHistoryComplete
                );
                loaded = true;
                errorElement.hidden = true;
            } catch (error) {
                console.error("Map initialization failed:", error);
                showError("The tracking map could not be initialized.");
            }
        });
    } catch (error) {
        console.error("MapLibre error:", error);
        showError("The map could not load. Internet access is required.");
    }
}

initializeTrackingMap();
