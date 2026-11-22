if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/service-worker.js').catch(error => {
        console.warn('Offline page could not be prepared.', error);
    });
}
