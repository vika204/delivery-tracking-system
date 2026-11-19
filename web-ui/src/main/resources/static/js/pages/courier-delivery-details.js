import {
    formatValue,
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

async function call(method, path = '', body) {
    const response = await fetch(api + path, {
        method,
        headers: body ? {'Content-Type': 'application/json'} : {},
        body: body ? JSON.stringify(body) : undefined
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(payload?.detail || 'Delivery data is unavailable right now.');
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
}

function setGpsStatus(text, warning = false) {
    gpsStatus.hidden = false;
    gpsStatus.textContent = text;
    gpsStatus.classList.toggle('warning', warning);
}

async function sendPosition(position) {
    if (sending || !shouldSendPosition(lastSentAt, Date.now())) return;
    sending = true;
    lastSentAt = Date.now();
    try {
        await call('POST', '/locations', locationPayload(position));
        setGpsStatus(`GPS: sharing location · last sent ${new Date().toLocaleTimeString()}`);
    } catch (error) {
        // наступна позиція спробує ще раз
        setGpsStatus(`GPS: ${error.message}`, true);
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
        error => setGpsStatus(`GPS: ${error.message || 'location is unavailable'}`, true),
        {enableHighAccuracy: true, maximumAge: 5000}
    );
}

function stopGps() {
    if (gpsWatch !== null) {
        navigator.geolocation.clearWatch(gpsWatch);
        gpsWatch = null;
    }
    gpsStatus.hidden = true;
}

advance.addEventListener('click', async () => {
    const action = current && nextAction(current.status);
    if (!action) return;
    advance.disabled = true;
    notice.textContent = '';
    try {
        render(await call('POST', `/${action.action}`));
    } catch (error) {
        notice.textContent = error.message;
    } finally {
        advance.disabled = false;
    }
});

window.addEventListener('pagehide', stopGps);

call('GET').then(render).catch(error => {
    notice.textContent = error.message;
});
