import {createKatottgAutocomplete} from '../components/katottg-autocomplete.js';
import {
    createIdempotencyKeys,
    createRequestFrom,
    formatDistance,
    formatPrice,
    isQuoteProblem,
    isQuoteUsable,
    problemMessage,
    quoteRequestFrom,
    sameRequest
} from '../components/shipment-creation.js';

export function buildFullAddress(settlementName, streetAddress) {
    const settlement = String(settlementName ?? '').trim();
    const street = String(streetAddress ?? '').trim();
    return settlement && street ? `${settlement}, ${street}` : '';
}

export function shipmentCreationErrorMessage() {
    return 'We could not create your shipment. Check your connection and try again. Your form data is still here.';
}

async function showShipmentCreationError(error) {
    console.error('Could not create shipment', error);
    await showProblem(shipmentCreationErrorMessage());
}

async function showProblem(message) {
    const options = {
        title: 'SHIPMENT NOT CREATED',
        message,
        buttonLabel: 'BACK TO FORM'
    };

    if (window.WebExpressModal?.alert) {
        await window.WebExpressModal.alert(options);
        return;
    }

    window.alert(options.message);
}

const QUOTE_DELAY_MS = 400;

async function postJson(url, body, headers = {}) {
    const response = await fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json', ...headers},
        body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => null);
    return {ok: response.ok, status: response.status, body: payload};
}

function startCreateShipment() {
    const form = document.getElementById('shipment-form');
    const modal = document.getElementById('confirmation');
    const closeModalButton = document.getElementById('close-modal');
    const confirmButton = document.getElementById('confirm-create');
    if (!form || !modal || !closeModalButton || !confirmButton) return;

    const submitButton = form.querySelector('button[type="submit"]');
    const pickupAddress = document.getElementById('pickup-address');
    const deliveryAddress = document.getElementById('delivery-address');
    const quoteStatus = document.getElementById('quote-status');

    const value = id => document.getElementById(id).value.trim();
    const idempotencyKeys = createIdempotencyKeys();

    // Остання пропозиція та запит, для якого її розраховано.
    let quote = null;
    let quoteRequest = null;
    let pendingQuote = null;
    let quoteTimer = null;

    function display(targetId, text) {
        const target = document.getElementById(targetId);
        target.textContent = text || 'Not specified yet';
        target.classList.toggle('placeholder', !text);
    }

    function setQuoteStatus(message, isError = false) {
        quoteStatus.textContent = message;
        quoteStatus.classList.toggle('error', isError);
    }

    let pickupAutocomplete;
    let deliveryAutocomplete;

    function fields() {
        return {
            pickupAddress: pickupAddress.value,
            pickupCityCode: pickupAutocomplete?.getSelection()?.code,
            deliveryAddress: deliveryAddress.value,
            deliveryCityCode: deliveryAutocomplete?.getSelection()?.code,
            weight: value('weight'),
            deliveryType: value('delivery-type'),
            senderName: value('sender-name'),
            senderPhone: value('sender-phone'),
            recipientName: value('recipient-name'),
            recipientPhone: value('recipient-phone'),
            length: value('length'),
            width: value('width'),
            height: value('height')
        };
    }

    function showQuote() {
        display('summary-distance', quote ? formatDistance(quote.distanceKm) : null);
        display('summary-price', quote ? formatPrice(quote.totalPrice) : null);
    }

    /** Повертає дійсну пропозицію для поточних даних, за потреби розраховує нову. */
    async function currentQuote() {
        const request = quoteRequestFrom(fields());
        if (!request) {
            quote = quoteRequest = null;
            showQuote();
            setQuoteStatus('Price is calculated by WEB EXPRESS once the route and weight are filled in.');
            return null;
        }
        if (quote && sameRequest(request, quoteRequest) && isQuoteUsable(quote, Date.now())) {
            return quote;
        }
        if (pendingQuote && sameRequest(request, pendingQuote.request)) {
            return pendingQuote.promise;
        }

        setQuoteStatus('Calculating delivery price…');
        const promise = postJson('/api/shipments/quotes', request).then(result => {
            if (!sameRequest(request, quoteRequestFrom(fields()))) return currentQuote();
            if (!result.ok) {
                quote = quoteRequest = null;
                showQuote();
                setQuoteStatus(problemMessage(result.body, 'Delivery price is unavailable right now.'), true);
                return null;
            }
            quote = result.body;
            quoteRequest = request;
            showQuote();
            setQuoteStatus('Price calculated by WEB EXPRESS. It is fixed when you confirm the shipment.');
            return quote;
        }).catch(() => {
            setQuoteStatus('Delivery price is unavailable right now. Check your connection.', true);
            return null;
        }).finally(() => {
            if (pendingQuote?.promise === promise) pendingQuote = null;
        });
        pendingQuote = {request, promise};
        return promise;
    }

    function scheduleQuote() {
        clearTimeout(quoteTimer);
        quoteTimer = setTimeout(currentQuote, QUOTE_DELAY_MS);
    }

    function updateSummary() {
        const pickupFullAddress = buildFullAddress(
            pickupAutocomplete?.getSelection()?.name,
            value('pickup-street')
        );
        const deliveryFullAddress = buildFullAddress(
            deliveryAutocomplete?.getSelection()?.name,
            value('delivery-street')
        );

        pickupAddress.value = pickupFullAddress;
        deliveryAddress.value = deliveryFullAddress;
        display('summary-from', pickupFullAddress);
        display('summary-to', deliveryFullAddress);
        display(
            'summary-weight',
            value('weight') ? `${value('weight')} kg` : null
        );

        const dimensions = ['length', 'width', 'height'].map(value);
        display(
            'summary-dims',
            dimensions.every(Boolean) ? `${dimensions.join(' × ')} cm` : null
        );
        scheduleQuote();
    }

    pickupAutocomplete = createKatottgAutocomplete({
        input: document.getElementById('pickup-city'),
        codeInput: document.getElementById('pickup-city-code'),
        suggestions: document.getElementById('pickup-city-suggestions'),
        help: document.getElementById('pickup-city-help'),
        onChange: updateSummary
    });

    deliveryAutocomplete = createKatottgAutocomplete({
        input: document.getElementById('delivery-city'),
        codeInput: document.getElementById('delivery-city-code'),
        suggestions: document.getElementById('delivery-city-suggestions'),
        help: document.getElementById('delivery-city-help'),
        onChange: updateSummary
    });

    function openConfirmation(confirmedQuote) {
        document.getElementById('confirm-price').textContent = formatPrice(confirmedQuote.totalPrice);
        document.getElementById('confirm-distance').textContent = formatDistance(confirmedQuote.distanceKm);
        modal.hidden = false;
        confirmButton.focus();
    }

    function dismissModal() {
        modal.hidden = true;
        submitButton.focus();
    }

    function setBusy(busy) {
        confirmButton.disabled = busy;
        closeModalButton.disabled = busy;
        submitButton.disabled = busy;
    }

    async function confirmShipment() {
        const confirmedQuote = quote;
        if (!confirmedQuote) return;
        const request = createRequestFrom(confirmedQuote.quoteId, fields());
        const idempotencyKey = idempotencyKeys.keyFor(request);

        setBusy(true);
        try {
            const result = await postJson('/api/shipments', request, {'Idempotency-Key': idempotencyKey});
            if (result.ok) {
                window.location.assign(`/ship/success/${result.body.shipmentId}`);
                return;
            }
            modal.hidden = true;
            if (isQuoteProblem(result.body)) {
                // пропозиція спливла чи вже використана: рахуємо нову й просимо підтвердити ще раз
                quote = quoteRequest = null;
                setQuoteStatus('The price has to be recalculated. Check it and confirm again.', true);
                await currentQuote();
                return;
            }
            if (result.status >= 400 && result.status < 500) {
                await showProblem(problemMessage(result.body, shipmentCreationErrorMessage()));
                return;
            }
            await showShipmentCreationError(new Error(`Shipment creation failed with ${result.status}`));
        } catch (error) {
            // мережа впала: повторне підтвердження піде з тим самим Idempotency-Key
            modal.hidden = true;
            await showShipmentCreationError(error);
        } finally {
            setBusy(false);
        }
    }

    form.addEventListener('input', updateSummary);
    form.addEventListener('change', updateSummary);
    form.addEventListener('reset', () => requestAnimationFrame(updateSummary));

    form.addEventListener('submit', async event => {
        event.preventDefault();

        try {
            pickupAutocomplete.validate();
            deliveryAutocomplete.validate();
            updateSummary();
            if (!form.reportValidity()) return;

            clearTimeout(quoteTimer);
            const confirmedQuote = await currentQuote();
            if (confirmedQuote) openConfirmation(confirmedQuote);
        } catch (error) {
            await showShipmentCreationError(error);
        }
    });

    confirmButton.addEventListener('click', confirmShipment);
    closeModalButton.addEventListener('click', dismissModal);
    modal.addEventListener('click', event => {
        if (event.target === modal && !confirmButton.disabled) dismissModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden && !confirmButton.disabled) dismissModal();
    });

    updateSummary();
}

if (typeof document !== 'undefined') {
    startCreateShipment();
}
