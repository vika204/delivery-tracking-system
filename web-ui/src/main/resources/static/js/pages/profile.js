(() => {
    const state = {
        firstName: 'Olena',
        lastName: 'Kovalenko',
        email: 'olena@example.com',
        phone: '+380 67 000 00 00'
    };

    const profileView = document.getElementById('profile-view');
    const form = document.getElementById('profile-form');
    const editButton = document.getElementById('edit-button');
    const cancelButton = document.getElementById('cancel-button');
    const message = document.getElementById('profile-message');

    if (!profileView || !form || !editButton || !cancelButton || !message) return;

    const displayName = document.getElementById('display-name');
    const displayEmail = document.getElementById('display-email');
    const displayPhone = document.getElementById('display-phone');

    function displayProfile() {
        displayName.textContent = [state.firstName, state.lastName].filter(Boolean).join(' ');
        displayEmail.textContent = state.email;
        displayPhone.textContent = state.phone;
    }

    function startEditing() {
        Object.keys(state).forEach(key => {
            form.elements.namedItem(key).value = state[key];
        });

        profileView.hidden = true;
        form.hidden = false;
        message.hidden = true;
        document.getElementById('first-name').focus();
    }

    function finishEditing() {
        form.hidden = true;
        profileView.hidden = false;
        editButton.focus();
    }

    editButton.addEventListener('click', startEditing);
    cancelButton.addEventListener('click', finishEditing);

    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;

        Object.keys(state).forEach(key => {
            state[key] = form.elements.namedItem(key).value.trim();
        });

        displayProfile();
        finishEditing();
        message.textContent = 'Profile updated in this preview. Changes are not saved to the server.';
        message.hidden = false;
    });

    displayProfile();
})();
