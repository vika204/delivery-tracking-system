(() => {
    const dialog = document.getElementById('demo-dialog');
    const heading = document.getElementById('dialog-heading');
    const closeButton = document.getElementById('close-dialog');

    document.querySelectorAll('[data-feature]').forEach(button => {
        button.addEventListener('click', () => {
            heading.textContent = `${button.dataset.feature} — coming soon!`;
            dialog.showModal();
        });
    });

    closeButton.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        if (event.target === dialog) dialog.close();
    });
})();

(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const fine = matchMedia('(pointer: fine)');

    const texts = document.querySelectorAll(
        '.landing-headline > span, .landing-eyebrow, .landing-description, ' +
        '.site-header__nav a, .site-header__nav button'
    );

    texts.forEach(el => {
        el.classList.add('glass-text');
        el.dataset.glassText = el.innerText;
        el.addEventListener('pointermove', event => {
            if (motion.matches || !fine.matches) return;
            el.style.setProperty(
                '--shine-x',
                `${event.clientX - el.getBoundingClientRect().left}px`
            );
            el.classList.add('is-shining');
        });
        el.addEventListener('pointerleave', () => el.classList.remove('is-shining'));
    });

    motion.addEventListener('change', () => {
        texts.forEach(el => el.classList.remove('is-shining'));
    });
})();
