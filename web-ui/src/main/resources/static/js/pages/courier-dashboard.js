import {
    availabilityLabel,
    deliveryStatusLabel,
    formatDistance,
    formatPrice,
    formatWeight,
    problemMessage,
    secondsUntil
} from '../components/courier-dashboard-view.js';

const API = '/api/courier/dashboard';
const REFRESH_MS = 5000;

const el = id => document.getElementById(id);
const toggle = el('availability-switch');
const stateLabel = el('availability-text');
const courierName = el('courier-name');
const notice = el('action-notice');
const offerCard = el('offer-card');
const noOffer = el('no-offer');
const acceptButton = el('accept-offer');
const declineButton = el('decline-offer');
const deliveriesList = el('deliveries-list');
const noDeliveries = el('no-deliveries');
const deliveryTemplate = el('delivery-template');

let current = null;
let busy = false;
let offerDeadline = 0;

async function call(method, path, body) {
    const response = await fetch(API + (path ?? ''), {
        method,
        headers: body ? {'Content-Type': 'application/json'} : {},
        body: body ? JSON.stringify(body) : undefined
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(problemMessage(payload, 'Dashboard data is unavailable right now.'));
    }
    return payload;
}

function render(view) {
    current = view;
    courierName.textContent = view.courierName || `Courier #${view.courierId}`;
    toggle.setAttribute('aria-checked', String(view.online));
    toggle.disabled = false;
    stateLabel.textContent = availabilityLabel(view.state);
    renderOffer(view.offer);
    renderDeliveries(view.deliveries || []);
}

function renderOffer(offer) {
    offerCard.hidden = !offer;
    noOffer.hidden = Boolean(offer);
    if (!offer) return;

    el('offer-shipment').textContent = offer.shipmentId;
    el('offer-pickup').textContent = offer.pickupAddress || '—';
    el('offer-delivery').textContent = offer.deliveryAddress || '—';
    el('offer-weight').textContent = formatWeight(offer.parcelWeight);
    el('offer-distance').textContent = formatDistance(offer.distanceKm);
    el('offer-price').textContent = formatPrice(offer.price);
    offerDeadline = Date.now() + offer.secondsLeft * 1000;
    renderCountdown();
}

function renderCountdown() {
    if (!current?.offer) return;
    el('offer-countdown').textContent = `${secondsUntil(offerDeadline, Date.now())} s`;
}

function renderDeliveries(deliveries) {
    deliveriesList.replaceChildren(...deliveries.map(delivery => {
        const card = deliveryTemplate.content.firstElementChild.cloneNode(true);
        card.querySelector('.shipment-id').textContent = `SHIPMENT #${delivery.shipmentId}`;
        card.querySelector('.delivery-from').textContent = delivery.pickupAddress || '—';
        card.querySelector('.delivery-to').textContent = delivery.deliveryAddress || '—';
        card.querySelector('.status-chip').textContent = deliveryStatusLabel(delivery);
        const link = card.querySelector('.details-link');
        link.href = `/courier/deliveries/${delivery.shipmentId}`;
        link.setAttribute('aria-label', `Open shipment ${delivery.shipmentId} details`);
        return card;
    }));
    noDeliveries.hidden = deliveries.length > 0;
}

async function act(method, path, body, success) {
    if (busy) return;
    busy = true;
    acceptButton.disabled = declineButton.disabled = toggle.disabled = true;
    try {
        render(await call(method, path, body));
        notice.textContent = success;
    } catch (error) {
        notice.textContent = error.message;
        await refresh();
    } finally {
        busy = false;
        acceptButton.disabled = declineButton.disabled = false;
        toggle.disabled = !current;
    }
}

async function refresh() {
    try {
        render(await call('GET'));
    } catch (error) {
        notice.textContent = error.message;
    }
}

toggle.addEventListener('click', () => {
    const online = toggle.getAttribute('aria-checked') !== 'true';
    void act('PUT', '/availability', {online}, online ? 'You are online.' : 'You are offline.');
});

acceptButton.addEventListener('click', () => {
    const offer = current?.offer;
    if (offer) void act('POST', `/offers/${offer.offerId}/accept`, null, `Shipment #${offer.shipmentId} accepted.`);
});

declineButton.addEventListener('click', () => {
    const offer = current?.offer;
    if (offer) void act('POST', `/offers/${offer.offerId}/decline`, null, `Shipment #${offer.shipmentId} declined.`);
});

void refresh();
setInterval(() => { if (!busy) void refresh(); }, REFRESH_MS);
setInterval(renderCountdown, 1000);
