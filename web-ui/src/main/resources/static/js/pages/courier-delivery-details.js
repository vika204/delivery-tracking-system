import {
    formatValue,
    isRetriableFailure,
    locationPayload,
    nextAction,
    sharesGps,
    shouldSendPosition,
    statusClass,
    statusLabel
} from '../components/courier-delivery.js';

const page = document.querySelector('[data-shipment-id]');
const shipmentId = page?.dataset.shipmentId;
const api = `/api/courier/shipments/${encodeURIComponent(shipmentId)}`;

const el = id => document.getElementById(id);
const badge = el('shipment-status');
const actions = el('delivery-actions');
const advance = el('advance-delivery');
const advanceLabel = el('advance-label');
const gpsStatus = el('gps-status');
const notice = el('delivery-notice');

let current = null;
let gpsWatch = null;
let lastSentAt = null;
let sending = false;
let pendingPoint = null;
let requestInProgress = false;
let refreshTimer = null;
let pageClosed = false;

async function call(method, path, body) {
    let response;
    try {
        response = await fetch(api + (path ?? ''), {
            method,
            cache: 'no-store',
            headers: body ? {'Content-Type': 'application/json'} : {},
            body: body ? JSON.stringify(body) : undefined
        });
    } catch (networkError) {
        throw Object.assign(new Error('Connection lost, retrying.'), {status: null});
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw Object.assign(new Error(payload?.detail || 'Delivery data is unavailable right now.'),
            {status: response.status});
    }
    return payload;
}

function render(shipment) {
    current = shipment;
    badge.textContent = statusLabel(shipment.status);
    badge.className = `delivery-status ${statusClass(shipment.status)}`.trim();

    el('detail-pickup').textContent = shipment.pickupAddress ?? '—';
    el('detail-delivery').textContent = shipment.deliveryAddress ?? '—';
    el('detail-recipient').textContent = shipment.recipient?.initials ?? '—';
    const phone = el('detail-recipient-phone');
    phone.textContent = shipment.recipient?.phone ?? '—';
    phone.href = shipment.recipient?.phone ? `tel:${shipment.recipient.phone.replace(/[^+0-9]/g, '')}` : '#';
    el('detail-distance').textContent = formatValue(shipment.distance, 'km');
    el('detail-weight').textContent = formatValue(shipment.weight, 'kg');
    el('detail-length').textContent = formatValue(shipment.length, 'cm');
    el('detail-width').textContent = formatValue(shipment.width, 'cm');
    el('detail-height').textContent = formatValue(shipment.height, 'cm');
    el('detail-price').textContent = formatValue(shipment.price, 'UAH');

    const action = nextAction(shipment.status);
    actions.hidden = !action;
    if (action) advanceLabel.textContent = action.label;

    if (sharesGps(shipment.status)) startGps(); else stopGps();
    if (shipment.status === 'DELIVERED' || shipment.status === 'CANCELLED') {
        clearTimeout(refreshTimer);
        refreshTimer = null;
    }
}

function setGpsStatus(text, warning = false) {
    gpsStatus.hidden = false;
    gpsStatus.textContent = text;
    gpsStatus.classList.toggle('warning', warning);
}

async function sendPosition(position) {
    if (pageClosed || !sharesGps(current?.status) || sending ||
        !shouldSendPosition(lastSentAt, Date.now())) return;
    sending = true;
    lastSentAt = Date.now();
    const point = pendingPoint ?? locationPayload(position);
    try {
        await call('POST', '/locations', point);
        pendingPoint = null;
        if (!pageClosed && sharesGps(current?.status)) {
            setGpsStatus(`GPS: sharing location · last sent ${new Date().toLocaleTimeString()}`);
        }
    } catch (error) {
        if (!pageClosed && sharesGps(current?.status)) {
            pendingPoint = isRetriableFailure(error.status) ? point : null;
            setGpsStatus(`GPS: ${error.message}`, true);
        }
    } finally {
        sending = false;
    }
}

function startGps() {
    if (gpsWatch !== null) return;
    if (!('geolocation' in navigator)) {
        setGpsStatus('GPS: this browser cannot share location.', true);
        return;
    }
    setGpsStatus('GPS: waiting for location…');
    gpsWatch = navigator.geolocation.watchPosition(
        sendPosition,
        error => {
            if (!pageClosed && sharesGps(current?.status)) {
                setGpsStatus(`GPS: ${error.message || 'location is unavailable'}`, true);
            }
        },
        {enableHighAccuracy: true, maximumAge: 5000}
    );
}

function stopGps() {
    if (gpsWatch !== null) {
        navigator.geolocation.clearWatch(gpsWatch);
        gpsWatch = null;
    }
    pendingPoint = null;
    gpsStatus.hidden = true;
}

advance.addEventListener('click', async () => {
    const action = current && nextAction(current.status);
    if (pageClosed || requestInProgress || !action) return;
    requestInProgress = true;
    advance.disabled = true;
    notice.textContent = '';
    try {
        const shipment = await call('POST', `/${action.action}`);
        if (!pageClosed) render(shipment);
    } catch (error) {
        if (!pageClosed) notice.textContent = error.message;
    } finally {
        requestInProgress = false;
        advance.disabled = false;
    }
});

async function refreshShipment() {
    if (pageClosed) return;
    if (!requestInProgress) {
        requestInProgress = true;
        advance.disabled = true;
        try {
            const shipment = await call('GET');
            if (!pageClosed) {
                render(shipment);
                notice.textContent = '';
            }
        } catch (error) {
            if (!pageClosed) notice.textContent = error.message;
        } finally {
            requestInProgress = false;
            advance.disabled = false;
        }
    }
    if (!pageClosed && current?.status !== 'DELIVERED' && current?.status !== 'CANCELLED') {
        refreshTimer = setTimeout(refreshShipment, 5000);
    }
}

window.addEventListener('pagehide', () => {
    pageClosed = true;
    clearTimeout(refreshTimer);
    stopGps();
});

refreshShipment();
