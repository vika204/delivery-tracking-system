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
    const canvas = document.getElementById('cursor-webs');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

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

    let webs = [];
    let frame = 0;
    let last = null;
    let lastTime = 0;

    function resize() {
        const dpr = Math.min(devicePixelRatio || 1, 2);
        canvas.width = Math.round(innerWidth * dpr);
        canvas.height = Math.round(innerHeight * dpr);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    addEventListener('resize', resize);
    resize();

    function draw(now) {
        frame = 0;
        ctx.clearRect(0, 0, innerWidth, innerHeight);
        webs = webs.filter(web => now - web.born < 1500);

        for (const web of webs) {
            const age = (now - web.born) / 1500;
            const alpha = Math.min(age * 9, 1) * Math.pow(1 - age, 1.5);
            ctx.save();
            ctx.translate(web.x, web.y);
            ctx.rotate(web.angle);
            ctx.strokeStyle = `rgba(112,63,43,${alpha * .33})`;
            ctx.lineWidth = .65;

            const radius = web.radius * (.86 + age * .14);
            const rays = 7;
            const point = (index, pointRadius) => {
                const angle = index * Math.PI * 2 / rays;
                const length = pointRadius * (
                    1 + .12 * Math.sin(index * 4.1 + web.angle)
                );
                return [Math.cos(angle) * length, Math.sin(angle) * length];
            };

            ctx.beginPath();
            for (let index = 0; index < rays; index++) {
                ctx.moveTo(0, 0);
                ctx.lineTo(...point(index, radius));
            }
            ctx.stroke();

            for (let ring = 1; ring <= 4; ring++) {
                const ringRadius = radius * ring / 4;
                ctx.beginPath();
                ctx.moveTo(...point(0, ringRadius));
                for (let index = 0; index < rays; index++) {
                    const angle = (index + .5) * Math.PI * 2 / rays;
                    ctx.quadraticCurveTo(
                        Math.cos(angle) * ringRadius * .72,
                        Math.sin(angle) * ringRadius * .72,
                        ...point((index + 1) % rays, ringRadius)
                    );
                }
                ctx.stroke();
            }
            ctx.restore();
        }

        if (webs.length) frame = requestAnimationFrame(draw);
    }

    document.addEventListener('pointermove', event => {
        if (motion.matches || !fine.matches || event.pointerType === 'touch') return;

        const now = performance.now();
        if (last && Math.hypot(event.clientX - last.x, event.clientY - last.y) < 52) return;
        if (now - lastTime < 65) return;

        last = {x: event.clientX, y: event.clientY};
        lastTime = now;
        webs.push({
            x: event.clientX,
            y: event.clientY,
            born: now,
            radius: 19 + Math.random() * 15,
            angle: Math.random() * Math.PI
        });
        if (webs.length > 24) webs.shift();
        if (!frame) frame = requestAnimationFrame(draw);
    }, {passive: true});

    document.documentElement.addEventListener('pointerleave', () => {
        last = null;
    });

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            cancelAnimationFrame(frame);
            frame = 0;
            webs = [];
            ctx.clearRect(0, 0, innerWidth, innerHeight);
        }
    });

    motion.addEventListener('change', () => {
        webs = [];
        texts.forEach(el => el.classList.remove('is-shining'));
        ctx.clearRect(0, 0, innerWidth, innerHeight);
    });
})();
