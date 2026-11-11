const MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const DEFAULT_MAP_CENTER = [31.1656, 48.3794];

const mapElement = document.getElementById("live-map");
const errorElement = document.getElementById("map-error");

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
            center: DEFAULT_MAP_CENTER,
            zoom: 5.5,
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
