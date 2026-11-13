import {createKatottgAutocomplete} from '../components/katottg-autocomplete.js';

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

    const options = {
        title: 'SHIPMENT NOT CREATED',
        message: shipmentCreationErrorMessage(),
        buttonLabel: 'BACK TO FORM'
    };

    if (window.WebExpressModal?.alert) {
        await window.WebExpressModal.alert(options);
        return;
    }

    window.alert(options.message);
}

function startCreateShipment() {
    const form = document.getElementById('shipment-form');
    const modal = document.getElementById('confirmation');
    const closeModalButton = document.getElementById('close-modal');
    if (!form || !modal || !closeModalButton) return;

    const submitButton = form.querySelector('button[type="submit"]');
    const pickupAddress = document.getElementById('pickup-address');
    const deliveryAddress = document.getElementById('delivery-address');

    const value = id => document.getElementById(id).value.trim();

    function display(targetId, text) {
        const target = document.getElementById(targetId);
        target.textContent = text || 'Not specified yet';
        target.classList.toggle('placeholder', !text);
    }

    let pickupAutocomplete;
    let deliveryAutocomplete;

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

    function dismissModal() {
        modal.hidden = true;
        submitButton.focus();
    }

    form.addEventListener('input', updateSummary);
    form.addEventListener('reset', () => requestAnimationFrame(updateSummary));

    form.addEventListener('submit', async event => {
        event.preventDefault();

        try {
            pickupAutocomplete.validate();
            deliveryAutocomplete.validate();
            updateSummary();
            if (!form.reportValidity()) return;

            modal.hidden = false;
            closeModalButton.focus();
        } catch (error) {
            await showShipmentCreationError(error);
        }
    });

    closeModalButton.addEventListener('click', dismissModal);
    modal.addEventListener('click', event => {
        if (event.target === modal) dismissModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden) dismissModal();
    });

    updateSummary();
}

if (typeof document !== 'undefined') {
    startCreateShipment();
}
