import {createKatottgAutocomplete} from '../components/katottg-autocomplete.js';

(async () => {
    const form = document.getElementById('profile-form');
    if (!form) return;

    const view = document.getElementById('profile-view');
    const editButton = document.getElementById('edit-button');
    const cancelButton = document.getElementById('cancel-button');
    const message = document.getElementById('profile-message');
    const courier = Boolean(form.elements.namedItem('workZone'));
    const fields = ['firstName', 'lastName', 'email', 'phone'];
    if (courier) fields.push('workZone', 'maxWeight');

    const workZone = courier ? createKatottgAutocomplete({
        input: document.getElementById('work-zone'),
        codeInput: document.getElementById('work-zone-code'),
        suggestions: document.getElementById('work-zone-suggestions'),
        help: document.getElementById('work-zone-help')
    }) : null;

    let profile;
    let saving = false;

    function showMessage(text, error = false) {
        message.textContent = text;
        message.hidden = !text;
        message.classList.toggle('profile-notice--error', error);
    }

    async function request(method, body) {
        let response;
        try {
            response = await fetch(form.dataset.profileUrl, {
                method,
                headers: {'Accept': 'application/json', ...(body ? {'Content-Type': 'application/json'} : {})},
                body: body ? JSON.stringify(body) : undefined,
                cache: 'no-store'
            });
        } catch {
            throw new Error('Profile is temporarily unavailable. Check your connection and try again.');
        }
        const result = await response.json().catch(() => null);
        if (!response.ok || !result) {
            throw new Error(result?.detail || 'Profile could not be loaded or saved. Please try again.');
        }
        return result;
    }

    function displayProfile() {
        document.getElementById('display-name').textContent = `${profile.firstName} ${profile.lastName}`;
        document.getElementById('display-email').textContent = profile.email;
        document.getElementById('display-phone').textContent = profile.phone;
        if (courier) {
            document.getElementById('display-work-zone').textContent = profile.workZone;
            document.getElementById('display-max-weight').textContent = `${profile.maxWeight} kg`;
        }
    }

    function finishEditing() {
        workZone?.close();
        form.hidden = true;
        view.hidden = false;
        editButton.focus();
    }

    editButton.addEventListener('click', () => {
        for (const field of fields) form.elements.namedItem(field).value = profile[field] ?? '';
        form.elements.namedItem('lastName').setCustomValidity('');
        workZone?.setValue(profile.workZone, profile.workZoneCode ?? '');
        view.hidden = true;
        form.hidden = false;
        showMessage('');
        document.getElementById('first-name').focus();
        workZone?.load().catch(() => {});
    });
    cancelButton.addEventListener('click', finishEditing);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving) return;
        const body = {};
        for (const field of fields) {
            const input = form.elements.namedItem(field);
            input.value = input.value.trim();
            body[field] = input.value;
        }
        if (courier) {
            const fullName = `${body.firstName} ${body.lastName}`;
            form.elements.namedItem('lastName').setCustomValidity(
                fullName.length > 100 ? 'The full name must be at most 100 characters.' : '');
            workZone.validate(profile.workZoneCode ? '' : profile.workZone);
            body.workZoneCode = workZone.getSelection()?.code || profile.workZoneCode || null;
            body.maxWeight = Number(body.maxWeight);
        }
        if (!form.reportValidity()) return;

        saving = true;
        const buttons = form.querySelectorAll('button');
        buttons.forEach(button => button.disabled = true);
        try {
            profile = await request('PUT', body);
            displayProfile();
            finishEditing();
            showMessage('Profile updated.');
        } catch (error) {
            const text = error.message + (courier ? ' Some changes may already have been saved. Retry saving to finish.' : '');
            showMessage(text, true);
            window.WebExpressModal.alert({title: 'PROFILE NOT SAVED', message: text});
        } finally {
            saving = false;
            buttons.forEach(button => button.disabled = false);
        }
    });

    try {
        profile = await request('GET');
        displayProfile();
        editButton.disabled = false;
        showMessage('');
    } catch (error) {
        showMessage(error.message + ' Reload the page to try again.', true);
    }
})();
