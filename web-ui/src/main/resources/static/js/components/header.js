document.querySelectorAll('[data-profile-menu]').forEach(profile => {
    const button = profile.querySelector('[data-profile-menu-button]');
    const menu = profile.querySelector('[data-profile-menu-panel]');

    function setOpen(open) {
        button.setAttribute('aria-expanded', String(open));
        menu.hidden = !open;
    }

    button.addEventListener('click', () => {
        setOpen(button.getAttribute('aria-expanded') !== 'true');
    });

    menu.addEventListener('click', event => {
        if (event.target.closest('button')) setOpen(false);
    });

    document.addEventListener('click', event => {
        if (!profile.contains(event.target)) setOpen(false);
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && button.getAttribute('aria-expanded') === 'true') {
            setOpen(false);
            button.focus();
        }
    });
});

document.querySelectorAll('[data-logout-button]').forEach(logoutButton => {
    logoutButton.addEventListener('click', async () => {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: {'Accept': 'application/json'}
            });
        } finally {
            window.location.href = '/login';
        }
    });
});

(async () => {
    const link = document.querySelector('[data-notification-status-url]');
    if (!link) return;

    const page = document.querySelector('[data-notification-refresh-url]');
    let acknowledgedThrough = '0';

    async function refreshCards() {
        const response = await fetch(page.dataset.notificationRefreshUrl, {cache: 'no-store'});
        if (!response.ok) throw new Error('Notification list unavailable');
        const html = new DOMParser().parseFromString(await response.text(), 'text/html');
        const nextPage = html.querySelector('[data-notification-refresh-url]');
        if (!nextPage) throw new Error('Notification list missing');

        const list = page.querySelector('.parcel-list');
        const nextList = nextPage.querySelector('.parcel-list');
        const existing = new Map([...list.querySelectorAll('[data-notification-id]')]
            .map(card => [card.dataset.notificationId, card]));
        const incoming = [...nextList.querySelectorAll('[data-notification-id]')];
        const nextIds = incoming.map(card => card.dataset.notificationId).join(',');
        if (!incoming.length || [...existing.keys()].join(',') !== nextIds) {
            list.replaceChildren(...(incoming.length
                ? incoming.map(card => existing.get(card.dataset.notificationId) || card)
                : nextList.children));
        }
        page.dataset.notificationReadThrough = nextPage.dataset.notificationReadThrough ?? '0';
        page.querySelector('.notifications-limit').hidden = incoming.length < 50;
    }

    async function refresh(initial = false) {
        try {
            if (document.hidden) return;
            if (page && !initial) await refreshCards();
            if (document.hidden) return;

            const throughId = page?.dataset.notificationReadThrough;
            let response = null;
            if (throughId && throughId !== '0' && throughId !== acknowledgedThrough) {
                try {
                    const query = new URLSearchParams({throughId});
                    response = await fetch(`${link.dataset.notificationReadUrl}?${query}`, {
                        method: 'POST',
                        headers: {Accept: 'application/json'}
                    });
                    if (!response.ok) throw new Error('Unable to mark notifications as viewed');
                    acknowledgedThrough = throughId;
                } catch {
                    response = null;
                    console.warn('Notifications could not be marked as viewed.');
                }
            }

            response ??= await fetch(link.dataset.notificationStatusUrl, {
                headers: {Accept: 'application/json'}, cache: 'no-store'
            });
            if (!response.ok) throw new Error('Notification status unavailable');
            const hasUnread = await response.json();
            if (typeof hasUnread !== 'boolean') throw new Error('Invalid notification status');
            document.querySelectorAll('[data-notification-indicator]').forEach(indicator => {
                indicator.hidden = !hasUnread;
            });
            if (page) page.querySelector('.notifications-refresh-error').hidden = true;
        } catch {
            if (page) page.querySelector('.notifications-refresh-error').hidden = false;
            console.warn('Notification updates are temporarily unavailable.');
        } finally {
            setTimeout(() => refresh(), 5000);
        }
    }

    await refresh(true);
})();
