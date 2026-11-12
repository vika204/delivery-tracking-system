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
