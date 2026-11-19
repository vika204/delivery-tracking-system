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

async function updateNotificationIndicator() {
    const link = document.querySelector('[data-notification-status-url]');
    if (!link) return;

    const page = document.querySelector('[data-notification-read-through]');
    const throughId = page?.dataset.notificationReadThrough;
    let response = null;
    if (throughId && throughId !== '0') {
        try {
            const query = new URLSearchParams({throughId});
            response = await fetch(`${link.dataset.notificationReadUrl}?${query}`, {
                method: 'POST',
                headers: {Accept: 'application/json'}
            });
            if (!response.ok) throw new Error('Unable to mark notifications as viewed');
        } catch {
            response = null;
            console.warn('Notifications could not be marked as viewed.');
        }
    }

    try {
        response ??= await fetch(link.dataset.notificationStatusUrl, {
            headers: {Accept: 'application/json'}, cache: 'no-store'
        });
        if (!response.ok) throw new Error('Notification status unavailable');
        const hasUnread = await response.json();
        if (typeof hasUnread !== 'boolean') throw new Error('Invalid notification status');
        document.querySelectorAll('[data-notification-indicator]').forEach(indicator => {
            indicator.hidden = !hasUnread;
        });
    } catch {
        console.warn('Notification status is temporarily unavailable.');
    }
}

updateNotificationIndicator();
