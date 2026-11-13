(() => {
    const toggle = document.getElementById('availability-switch');
    const status = document.getElementById('availability-text');
    const notice = document.getElementById('action-notice');
    const offer = document.getElementById('offer-card');
    const empty = document.getElementById('no-offer');
    const acceptButton = document.getElementById('accept-offer');
    const declineButton = document.getElementById('decline-offer');

    if (!toggle || !status || !notice || !offer || !empty || !acceptButton || !declineButton) return;

    toggle.addEventListener('click', () => {
        const next = toggle.getAttribute('aria-checked') !== 'true';
        toggle.setAttribute('aria-checked', String(next));
        status.textContent = next ? 'AVAILABLE' : 'UNAVAILABLE';
        notice.textContent = '';
    });

    acceptButton.addEventListener('click', () => {
        offer.hidden = true;
        empty.hidden = false;

        const template = document.querySelector('.delivery-card').cloneNode(true);
        template.querySelector('.shipment-id').textContent = 'SHIPMENT #2187';

        const addresses = template.querySelectorAll('.address span');
        addresses[0].innerHTML = '<strong>From:</strong> Kyiv, Lva Tolstoho St, 15';
        addresses[1].innerHTML = '<strong>To:</strong> Kyiv, Lypova St, 8';
        template.querySelector('.status-chip').textContent = 'TO PICK UP';

        const detailsLink = template.querySelector('.details-link');
        detailsLink.href = '/courier/deliveries/2187';
        detailsLink.setAttribute('aria-label', 'Open shipment 2187 details');

        document.getElementById('deliveries-list').append(template);
        notice.textContent = 'Offer #2187 accepted (preview only).';
    });

    declineButton.addEventListener('click', () => {
        offer.hidden = true;
        empty.hidden = false;
        notice.textContent = 'Offer #2187 declined (preview only).';
    });
})();
