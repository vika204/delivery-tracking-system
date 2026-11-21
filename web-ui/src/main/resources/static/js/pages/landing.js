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
