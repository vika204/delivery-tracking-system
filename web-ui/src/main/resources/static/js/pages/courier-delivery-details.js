(() => {
    const badge = document.getElementById('shipment-status');
    const actions = document.getElementById('delivery-actions');
    const advance = document.getElementById('advance-delivery');
    const advanceLabel = document.getElementById('advance-label');
    const cancel = document.getElementById('cancel-delivery');
    const notice = document.getElementById('preview-notice');

    if (!badge || !actions || !advance || !advanceLabel || !cancel || !notice) return;

    let stage = 'TO_PICKUP';

    function render() {
        const labels = {
            TO_PICKUP: 'TO PICK UP',
            IN_TRANSIT: 'IN TRANSIT',
            DELIVERED: 'DELIVERED',
            WITHDRAWN: 'WITHDRAWN'
        };

        badge.textContent = labels[stage];
        badge.className = 'delivery-status';
        if (stage === 'IN_TRANSIT') badge.classList.add('in-transit');
        if (stage === 'DELIVERED') badge.classList.add('delivered');
        if (stage === 'WITHDRAWN') badge.classList.add('withdrawn');

        cancel.hidden = stage !== 'TO_PICKUP';
        actions.hidden = stage === 'DELIVERED' || stage === 'WITHDRAWN';
        advanceLabel.textContent = stage === 'TO_PICKUP' ? 'CONFIRM PICKUP' : 'MARK AS DELIVERED';
    }

    advance.addEventListener('click', () => {
        if (stage === 'TO_PICKUP') {
            stage = 'IN_TRANSIT';
            notice.textContent = 'Pickup confirmed — preview only. No data was saved.';
        } else if (stage === 'IN_TRANSIT') {
            stage = 'DELIVERED';
            notice.textContent = 'Delivery completed — preview only. No data was saved.';
        }
        render();
    });

    cancel.addEventListener('click', async () => {
        if (stage !== 'TO_PICKUP') return;

        const approved = await window.WebExpressModal.confirm({
            title: 'WITHDRAW FROM DELIVERY?',
            message: 'Withdraw from this delivery? The shipment itself will remain active.',
            confirmLabel: 'WITHDRAW',
            cancelLabel: 'KEEP DELIVERY'
        });
        if (!approved) return;

        stage = 'WITHDRAWN';
        notice.textContent = 'Courier withdrew — preview only. No data was saved.';
        render();
    });

    render();
})();
