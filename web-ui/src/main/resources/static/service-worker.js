const CACHE_PREFIX = 'web-express-offline-';
const CACHE_NAME = `${CACHE_PREFIX}v2`;
const OFFLINE_PAGE = '/offline.html';
const ASSETS = [OFFLINE_PAGE, '/js/components/offline.js', '/images/spider-icon.svg'];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS)));
});

self.addEventListener('activate', event => {
    event.waitUntil((async () => {
        const names = await caches.keys();
        await Promise.all(names
            .filter(name => name.startsWith(CACHE_PREFIX) && name !== CACHE_NAME)
            .map(name => caches.delete(name)));
        await self.clients.claim();
    })());
});

async function unavailablePage() {
    const cache = await caches.open(CACHE_NAME);
    const page = await cache.match(OFFLINE_PAGE);
    return new Response(page ? page.body : 'SERVER TEMPORARILY UNAVAILABLE', {
        status: 503,
        headers: {'Content-Type': page ? 'text/html; charset=utf-8' : 'text/plain; charset=utf-8', 'Cache-Control': 'no-store'}
    });
}

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== self.location.origin
            || url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) {
        return;
    }

    if (request.mode === 'navigate') {
        event.respondWith((async () => {
            try {
                const response = await fetch(request);
                return [502, 503, 504].includes(response.status) ? unavailablePage() : response;
            } catch {
                return unavailablePage();
            }
        })());
    } else if (ASSETS.includes(url.pathname) && !url.search) {
        event.respondWith(caches.open(CACHE_NAME)
            .then(cache => cache.match(request))
            .then(cached => cached || fetch(request)));
    }
});
