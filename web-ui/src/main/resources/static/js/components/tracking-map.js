import {TrackingLocationStore} from "./tracking-map-state.js";
import {TrackingMarkerAnimator} from "./tracking-marker-animator.js";

const MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const DEFAULT_MAP_CENTER = [31.1656, 48.3794];
const ROUTE_SOURCE_ID = "web-express-tracking-route";
const ROUTE_LAYER_ID = "web-express-tracking-route-line";
const MARKER_ANIMATION_DURATION_MS = 1000;

const mapElement = document.getElementById("live-map");
const errorElement = document.getElementById("map-error");
const locationStatusElement = document.getElementById(
    "tracking-location-status"
);
const followButton = document.getElementById("follow");
const overviewButton = document.getElementById("overview");
const toggle3dButton = document.getElementById("toggle-3d");

const trackingData = readTrackingData();
const locationStore = new TrackingLocationStore(
    trackingData.shipmentId,
    trackingData.locations
);

let mapInstance = null;
let mapLoaded = false;
let courierMarker = null;
let followingCourier = false;
let is3d = true;
let locationHistoryComplete = trackingData.locationHistoryComplete;
let hasCurrentPosition = locationHistoryComplete &&
    locationStore.size() > 0;
const markerAnimator = new TrackingMarkerAnimator({
    requestFrame: callback => window.requestAnimationFrame(callback),
    cancelFrame: frame => window.cancelAnimationFrame(frame),
    reducedMotion: prefersReducedMotion,
    duration: MARKER_ANIMATION_DURATION_MS
});

function readTrackingData() {
    const data = window.webExpressTracking ?? {};

    return {
        shipmentId: data.shipmentId,
        locations: Array.isArray(data.locations) ? data.locations : [],
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

function routeFeature(coordinates) {
    return {
        type: "Feature",
        properties: {},
        geometry: {
            type: "LineString",
            coordinates
        }
    };
}

function updateRoute(map, coordinates) {
    if (coordinates.length < 2) {
        return;
    }

    const source = map.getSource(ROUTE_SOURCE_ID);

    if (source) {
        source.setData(routeFeature(coordinates));
        return;
    }

    map.addSource(ROUTE_SOURCE_ID, {
        type: "geojson",
        data: routeFeature(coordinates)
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

function createCourierMarker(map, coordinate) {
    const markerElement = document.createElement("div");
    markerElement.className = "tracking-courier-pin";
    markerElement.setAttribute("aria-label", "Last known courier location");

    const image = document.createElement("img");
    image.src = mapElement.dataset.spiderUrl;
    image.alt = "";
    markerElement.appendChild(image);

    return new maplibregl.Marker({
        element: markerElement,
        anchor: "bottom"
    })
        .setLngLat(coordinate)
        .addTo(map);
}

function prefersReducedMotion() {
    return typeof window.matchMedia === "function" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function moveCourierMarker(map, coordinate) {
    if (!courierMarker) {
        courierMarker = createCourierMarker(map, coordinate);
        return;
    }

    markerAnimator.move(courierMarker, coordinate);
}

function fitRoute(map) {
    const coordinates = locationStore.coordinates();

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
        duration: prefersReducedMotion() ? 0 : 800
    });
}

function setFollowingCourier(enabled) {
    followingCourier = enabled && hasCurrentPosition;
    followButton.setAttribute(
        "aria-pressed",
        String(followingCourier)
    );
}

function updateControlAvailability() {
    const locationCount = locationStore.size();

    followButton.disabled = !hasCurrentPosition;
    overviewButton.disabled = locationCount < 2;
    toggle3dButton.disabled = locationCount === 0;

    if (!hasCurrentPosition) {
        setFollowingCourier(false);
    }
}

function updateLocationStatus() {
    if (!locationStatusElement) {
        return;
    }

    if (!locationHistoryComplete) {
        locationStatusElement.textContent = "LOCATION HISTORY INCOMPLETE";
    } else if (locationStore.size() === 0) {
        locationStatusElement.textContent = "WAITING FOR COURIER LOCATION";
    } else {
        locationStatusElement.textContent = "COURIER LOCATION AVAILABLE";
    }
}

function renderAppendedLocations(lastLocation, updateCurrentPosition) {
    if (updateCurrentPosition && lastLocation) {
        hasCurrentPosition = true;
    }

    updateLocationStatus();

    if (!mapLoaded || !mapInstance) {
        return;
    }

    updateRoute(mapInstance, locationStore.coordinates());

    if (updateCurrentPosition && lastLocation) {
        moveCourierMarker(mapInstance, lastLocation.coordinate);

        if (followingCourier) {
            mapInstance.easeTo({
                center: lastLocation.coordinate,
                duration: prefersReducedMotion()
                    ? 0
                    : MARKER_ANIMATION_DURATION_MS
            });
        }
    }

    updateControlAvailability();
}

function configureControls(map) {
    followButton.addEventListener("click", () => {
        setFollowingCourier(!followingCourier);

        if (followingCourier) {
            const lastCoordinate = locationStore.lastLocation()?.coordinate;

            if (lastCoordinate) {
                map.easeTo({
                    center: lastCoordinate,
                    zoom: Math.max(map.getZoom(), 15),
                    duration: prefersReducedMotion() ? 0 : 700
                });
            }
        }
    });

    overviewButton.addEventListener("click", () => {
        setFollowingCourier(false);
        fitRoute(map);
    });

    toggle3dButton.addEventListener("click", () => {
        is3d = !is3d;
        map.easeTo({
            pitch: is3d ? 35 : 0,
            duration: prefersReducedMotion() ? 0 : 500
        });
        toggle3dButton.textContent = is3d ? "2D VIEW" : "3D VIEW";
        toggle3dButton.setAttribute("aria-pressed", String(is3d));
    });

    map.on("movestart", event => {
        if (event.originalEvent) {
            setFollowingCourier(false);
        }
    });

    updateControlAvailability();
}

export function appendLocation(location) {
    const appended = locationStore.append(location);

    if (!appended) {
        return false;
    }

    renderAppendedLocations(appended, true);

    return true;
}

export function appendLocations(
    locations,
    {updateCurrentPosition = true} = {}
) {
    if (!Array.isArray(locations)) {
        return 0;
    }

    const appended = [];

    for (const location of locations) {
        const normalized = locationStore.append(location);

        if (normalized) {
            appended.push(normalized);
        }
    }

    if (appended.length > 0) {
        renderAppendedLocations(
            appended.at(-1),
            updateCurrentPosition
        );
    }

    return appended.length;
}

export function getLastLocationId() {
    return locationStore.lastLocation()?.locationId ?? 0;
}

export function setLocationHistoryComplete(complete) {
    locationHistoryComplete = complete === true;

    if (locationHistoryComplete && locationStore.size() > 0) {
        const lastLocation = locationStore.lastLocation();
        hasCurrentPosition = true;

        if (mapLoaded && mapInstance && lastLocation) {
            moveCourierMarker(mapInstance, lastLocation.coordinate);
        }
    }

    updateLocationStatus();

    if (mapLoaded && mapInstance) {
        updateControlAvailability();
    }
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
        const lastCoordinate = hasCurrentPosition
            ? locationStore.lastLocation()?.coordinate
            : null;
        const map = new maplibregl.Map({
            container: "live-map",
            style,
            center: lastCoordinate ?? DEFAULT_MAP_CENTER,
            zoom: lastCoordinate ? 14 : 5.5,
            pitch: 35,
            bearing: 0,
            attributionControl: true
        });

        mapInstance = map;

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
                const coordinates = locationStore.coordinates();

                addBuildings(map);
                updateRoute(map, coordinates);

                if (hasCurrentPosition) {
                    const currentCoordinate =
                        locationStore.lastLocation()?.coordinate;

                    if (currentCoordinate) {
                        courierMarker = createCourierMarker(
                            map,
                            currentCoordinate
                        );
                    }
                }

                configureControls(map);
                mapLoaded = true;
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

if (typeof window !== "undefined") {
    window.webExpressTrackingMap = Object.freeze({
        appendLocation,
        appendLocations,
        getLastLocationId,
        setLocationHistoryComplete
    });
}

initializeTrackingMap();
