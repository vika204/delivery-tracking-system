// Shared visual prototype for registration and login.
// Inputs intentionally have no names, so credentials cannot be submitted.
const form = document.querySelector('[data-auth-form]');
const message = form.querySelector('.status');

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

form.addEventListener('submit', event => {
    event.preventDefault();
    if (!form.reportValidity()) return;

    message.textContent = form.dataset.prototypeMessage;
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

// Small, temporary cobwebs follow the pointer (desktop only).
(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const fine = matchMedia('(pointer: fine)');
    const canvas = document.getElementById('cursor-webs');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let webs = [], frame = 0, last = null, lastTime = 0;

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
        webs = webs.filter(w => now - w.born < 1500);

        for (const w of webs) {
            const age = (now - w.born) / 1500;
            const alpha = Math.min(age * 9, 1) * Math.pow(1 - age, 1.5);
            ctx.save();
            ctx.translate(w.x, w.y);
            ctx.rotate(w.angle);
            ctx.strokeStyle = `rgba(112,63,43,${alpha * .33})`;
            ctx.lineWidth = .65;
            const radius = w.radius * (.86 + age * .14), rays = 7;
            const point = (i, r) => {
                const a = i * Math.PI * 2 / rays;
                const length = r * (1 + .12 * Math.sin(i * 4.1 + w.angle));
                return [Math.cos(a) * length, Math.sin(a) * length];
            };

            ctx.beginPath();
            for (let i = 0; i < rays; i++) {
                ctx.moveTo(0, 0);
                ctx.lineTo(...point(i, radius));
            }
            ctx.stroke();

            for (let ring = 1; ring <= 4; ring++) {
                const r = radius * ring / 4;
                ctx.beginPath();
                ctx.moveTo(...point(0, r));
                for (let i = 0; i < rays; i++) {
                    const a = (i + .5) * Math.PI * 2 / rays;
                    ctx.quadraticCurveTo(
                        Math.cos(a) * r * .72,
                        Math.sin(a) * r * .72,
                        ...point((i + 1) % rays, r)
                    );
                }
                ctx.stroke();
            }
            ctx.restore();
        }

        if (webs.length) frame = requestAnimationFrame(draw);
    }

    document.addEventListener('pointermove', e => {
        if (motion.matches || !fine.matches || e.pointerType === 'touch') return;
        const now = performance.now();
        if (last && Math.hypot(e.clientX - last.x, e.clientY - last.y) < 52) return;
        if (now-lastTime < 65) return;
        last = {x: e.clientX, y: e.clientY};
        lastTime = now;
        webs.push({
            x: e.clientX,
            y: e.clientY,
            born: now,
            radius: 19+Math.random()*15,
            angle: Math.random()*Math.PI
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
        webs=[];
        ctx.clearRect(0, 0, innerWidth, innerHeight);
    });
})();
