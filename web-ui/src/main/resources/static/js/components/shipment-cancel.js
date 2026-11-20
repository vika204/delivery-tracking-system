document.querySelectorAll('[data-cancel-url]').forEach(button => {
    button.addEventListener('click', async () => {
        if (button.disabled) return;
        button.disabled = true;
        try {
            const confirmed = await window.WebExpressModal.confirm({
                title: 'CANCEL SHIPMENT?',
                message: 'Are you sure you want to cancel this shipment?',
                confirmLabel: 'YES, CANCEL',
                cancelLabel: 'KEEP SHIPMENT'
            });
            if (!confirmed) return;

            const response = await fetch(button.dataset.cancelUrl, {
                method: 'POST',
                headers: {Accept: 'application/json'}
            });
            if (!response.ok) {
                const problem = await response.json().catch(() => null);
                await window.WebExpressModal.alert({
                    title: 'CANNOT CANCEL SHIPMENT',
                    message: problem?.detail || 'Shipment could not be cancelled. Please try again.'
                });
                return;
            }
            const card = button.closest('.parcel-card');
            const status = card.querySelector('[data-shipment-status]');
            status.textContent = 'CANCELLED';
            status.className = 'status cancelled';
            const tracking = card.querySelector('[data-track-shipment]');
            tracking.removeAttribute('href');
            tracking.setAttribute('aria-disabled', 'true');
            tracking.setAttribute('tabindex', '-1');
            button.remove();
        } catch {
            await window.WebExpressModal.alert({
                title: 'CANCELLATION FAILED',
                message: 'Check your connection and try again. Cancellation could not be confirmed.'
            });
        } finally {
            button.disabled = false;
        }
    });
});
