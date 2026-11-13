import {createKatottgAutocomplete} from '../components/katottg-autocomplete.js';

const profileView = document.getElementById('profile-view');
const form = document.getElementById('profile-form');
const editButton = document.getElementById('edit-button');
const cancelButton = document.getElementById('cancel-button');
const message = document.getElementById('profile-message');

if (profileView && form && editButton && cancelButton && message) {
    const state = {
        fullName: 'Andrii Kovalenko',
        email: 'andrii@gmail.com',
        phone: '+380 67 123 45 67',
        workZone: 'Kyiv',
        workZoneCode: '',
        maxWeight: '20'
    };

    const workZoneAutocomplete = createKatottgAutocomplete({
        input: document.getElementById('work-zone'),
        codeInput: document.getElementById('work-zone-code'),
        suggestions: document.getElementById('work-zone-suggestions'),
        help: document.getElementById('work-zone-help')
    });

    const displays = {
        fullName: document.getElementById('display-full-name'),
        email: document.getElementById('display-email'),
        phone: document.getElementById('display-phone'),
        workZone: document.getElementById('display-work-zone'),
        maxWeight: document.getElementById('display-max-weight')
    };

    function displayProfile() {
        displays.fullName.textContent = state.fullName;
        displays.email.textContent = state.email;
        displays.phone.textContent = state.phone;
        displays.workZone.textContent = state.workZone;
        displays.maxWeight.textContent = `${state.maxWeight} kg`;
    }

    function startEditing() {
        ['fullName', 'email', 'phone', 'maxWeight'].forEach(key => {
            form.elements.namedItem(key).value = state[key];
        });
        workZoneAutocomplete.setValue(state.workZone, state.workZoneCode);

        profileView.hidden = true;
        form.hidden = false;
        message.hidden = true;
        document.getElementById('full-name').focus();
        workZoneAutocomplete.load().catch(() => {});
    }

    function finishEditing() {
        workZoneAutocomplete.close();
        form.hidden = true;
        profileView.hidden = false;
        editButton.focus();
    }

    editButton.addEventListener('click', startEditing);
    cancelButton.addEventListener('click', finishEditing);

    form.addEventListener('submit', event => {
        event.preventDefault();

        const allowedLegacyZone = !state.workZoneCode ? state.workZone : '';
        workZoneAutocomplete.validate(allowedLegacyZone);
        if (!form.reportValidity()) return;

        state.fullName = form.elements.namedItem('fullName').value.trim();
        state.email = form.elements.namedItem('email').value.trim();
        state.phone = form.elements.namedItem('phone').value.trim();
        state.workZone = form.elements.namedItem('workZone').value.trim();
        state.workZoneCode = workZoneAutocomplete.getSelection()?.code ?? state.workZoneCode;
        state.maxWeight = form.elements.namedItem('maxWeight').value.trim();

        displayProfile();
        finishEditing();
        message.textContent = 'Profile updated in this preview. Changes are not saved to the server.';
        message.hidden = false;
    });

    displayProfile();
}
