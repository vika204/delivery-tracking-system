import {createKatottgAutocomplete} from '../components/katottg-autocomplete.js';

// Shared visual prototype for registration and login.
// Inputs intentionally have no names, so credentials cannot be submitted.
const forms = [...document.querySelectorAll('[data-auth-form]')];
const registrationTabs = [...document.querySelectorAll('[data-registration-tab]')];
const registrationPanels = [...document.querySelectorAll('[data-registration-panel]')];
const registrationFormArea = document.getElementById('registration-form-area');
const titleFirst = document.getElementById('register-title-first');
const titleSecond = document.getElementById('register-title-second');

let courierWorkZone = null;
const courierWorkZoneInput = document.getElementById('courier-work-zone');
if (courierWorkZoneInput) {
    courierWorkZone = createKatottgAutocomplete({
        input: courierWorkZoneInput,
        codeInput: document.getElementById('courier-work-zone-code'),
        suggestions: document.getElementById('courier-work-zone-suggestions'),
        help: document.getElementById('courier-work-zone-help')
    });
}

function activateRegistration(role, focusTab = false) {
    registrationTabs.forEach(tab => {
        const active = tab.dataset.registrationTab === role;
        tab.classList.toggle('active', active);
        tab.setAttribute('aria-selected', String(active));
        tab.tabIndex = active ? 0 : -1;
        if (active && focusTab) tab.focus();
    });

    registrationPanels.forEach(panel => {
        panel.hidden = panel.dataset.registrationPanel !== role;
    });

    registrationFormArea?.classList.toggle('courier-active', role === 'courier');
    if (titleFirst && titleSecond) {
        titleFirst.textContent = role === 'courier' ? 'JOIN AS' : 'CREATE';
        titleSecond.textContent = role === 'courier' ? 'COURIER.' : 'ACCOUNT.';
    }

    if (role === 'courier') courierWorkZone?.load().catch(() => {});
}

registrationTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => {
        const role = tab.dataset.registrationTab;
        activateRegistration(role);

        const url = new URL(location.href);
        if (role === 'courier') url.searchParams.set('type', 'courier');
        else url.searchParams.delete('type');
        history.replaceState(null, '', url);
    });

    tab.addEventListener('keydown', event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();

        let nextIndex = index;
        if (event.key === 'ArrowLeft') nextIndex = (index - 1 + registrationTabs.length) % registrationTabs.length;
        if (event.key === 'ArrowRight') nextIndex = (index + 1) % registrationTabs.length;
        if (event.key === 'Home') nextIndex = 0;
        if (event.key === 'End') nextIndex = registrationTabs.length - 1;

        const nextTab = registrationTabs[nextIndex];
        activateRegistration(nextTab.dataset.registrationTab, true);
    });
});

if (registrationTabs.length > 0) {
    const requestedRole = new URLSearchParams(location.search).get('type');
    activateRegistration(requestedRole === 'courier' ? 'courier' : 'client');
}

document.querySelectorAll('.show').forEach(toggle => {
    const password = toggle.closest('.password').querySelector('input');

    toggle.addEventListener('click', () => {
        const visible = password.type === 'password';
        password.type = visible ? 'text' : 'password';
        toggle.textContent = visible ? 'HIDE' : 'SHOW';
        toggle.setAttribute('aria-pressed', String(visible));
        toggle.setAttribute(
            'aria-label',
            visible ? 'Hide password' : 'Show password'
        );
    });
});

forms.forEach(form => {
    const password = form.querySelector('[data-password]');
    const confirmation = form.querySelector('[data-confirm-password]');

    confirmation?.addEventListener('input', () => {
        confirmation.setCustomValidity('');
    });

    form.addEventListener('submit', event => {
        event.preventDefault();

        if (form.dataset.registrationRole === 'courier') {
            courierWorkZone?.validate();
        }
        if (password && confirmation) {
            confirmation.setCustomValidity(
                password.value === confirmation.value
                    ? ''
                    : 'Passwords must match.'
            );
        }
        if (!form.reportValidity()) return;

        form.querySelector('.status').textContent = form.dataset.prototypeMessage;
    });
});

// Courier descends on a web, then gently swings.
// Clicking it adds a little swing.
(() => {
    const courier = document.querySelector('.hanger');
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0;
    let start;
    let lastTap = -Infinity;

    courier.addEventListener('click', () => {
        if (!reducedMotion.matches) lastTap = performance.now();
    });

    courier.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            courier.click();
        }
    });

    function smooth(value) {
        const x = Math.min(1, Math.max(0, value));
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    function tick(now) {
        if (start === undefined) start = now;
        const time = (now - start) / 1000;
        const distance = innerHeight + 500;
        const omega = 5.6;
        const damping = .91;
        const wd = omega * Math.sqrt(1 - damping * damping);

        const y = -distance * Math.exp(-damping * omega * time) *
            (Math.cos(wd * time) +
                (damping * omega / wd) * Math.sin(wd * time));

        const amplitude = 1.25 * smooth((time - .85) / 2.3);
        const elapsed = (now - lastTap) / 1000;

        const pulse = Number.isFinite(elapsed) && elapsed >= 0
            ? 6 * Math.exp(-elapsed * 1.15) * Math.sin(elapsed * 5)
            : 0;

        const angle = amplitude *
            Math.sin((time - .85) * 2 * Math.PI / 6.2) + pulse;

        courier.style.transform =
            `translate(-50%, ${y}px) rotate(${angle}deg)`;

        frame = requestAnimationFrame(tick);
    }

    function play() {
        cancelAnimationFrame(frame);
        start = undefined;

        if (reducedMotion.matches) {
            courier.style.transform = 'translateX(-50%)';
        } else {
            frame = requestAnimationFrame(tick);
        }
    }

    reducedMotion.addEventListener('change', play);

    document.addEventListener('visibilitychange', () => {
        cancelAnimationFrame(frame);

        if (!document.hidden && !reducedMotion.matches) {
            frame = requestAnimationFrame(tick);
        }
    });

    play();
})();

