(() => {
    const form = document.getElementById('shipment-form');
    const modal = document.getElementById('confirmation');
    const closeModalButton = document.getElementById('close-modal');
    const submitButton = form.querySelector('button[type="submit"]');

    const value = id => document.getElementById(id).value.trim();

    function display(targetId, text) {
        const target = document.getElementById(targetId);
        target.textContent = text || 'Not specified yet';
        target.classList.toggle('placeholder', !text);
    }

    function updateSummary() {
        display('summary-from', value('pickup'));
        display('summary-to', value('destination'));
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

    function dismissModal() {
        modal.hidden = true;
        submitButton.focus();
    }

    form.addEventListener('input', updateSummary);
    form.addEventListener('reset', () => requestAnimationFrame(updateSummary));

    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;

        modal.hidden = false;
        closeModalButton.focus();
    });

    closeModalButton.addEventListener('click', dismissModal);
    modal.addEventListener('click', event => {
        if (event.target === modal) dismissModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden) dismissModal();
    });

    updateSummary();
})();
