(() => {
    "use strict";

    const requests = [];

    let elements;
    let activeRequest = null;
    let initialized = false;
    let initializationScheduled = false;

    function findElements() {
        const dialog = document.getElementById("we-modal");

        if (!dialog) {
            return null;
        }

        return {
            dialog,
            title: dialog.querySelector("#we-modal-title"),
            message: dialog.querySelector("#we-modal-message"),
            cancelButton: dialog.querySelector(".we-modal-cancel"),
            confirmButton: dialog.querySelector(".we-modal-confirm")
        };
    }

    function hasCompleteMarkup(candidate) {
        return candidate &&
            candidate.title &&
            candidate.message &&
            candidate.cancelButton &&
            candidate.confirmButton;
    }

    function restoreFocus(request) {
        const target = request.returnFocus;

        if (!target || !target.isConnected || typeof target.focus !== "function") {
            return;
        }

        try {
            target.focus();
        } catch (error) {
            console.warn("Could not restore focus after closing modal", error);
        }
    }

    function processNextRequest() {
        if (activeRequest || requests.length === 0) {
            return;
        }

        if (!initialize()) {
            scheduleInitialization();
            return;
        }

        const request = requests.shift();
        const isConfirmation = request.type === "confirm";

        activeRequest = request;
        elements.title.textContent = String(request.options.title ?? "NOTICE");
        elements.message.textContent = String(request.options.message ?? "");
        elements.confirmButton.textContent = String(
            request.options.confirmLabel ?? request.options.buttonLabel ?? "GOT IT"
        );
        elements.cancelButton.textContent = String(
            request.options.cancelLabel ?? "CANCEL"
        );
        elements.cancelButton.hidden = !isConfirmation;
        elements.cancelButton.disabled = false;
        elements.confirmButton.disabled = false;

        try {
            elements.dialog.showModal();

            if (isConfirmation) {
                elements.cancelButton.focus();
            } else {
                elements.confirmButton.focus();
            }
        } catch (error) {
            console.error("Could not open Web Express modal", error);
            activeRequest = null;
            request.resolve(false);
            queueMicrotask(processNextRequest);
        }
    }

    function settleActiveRequest(result) {
        if (!activeRequest) {
            return;
        }

        const request = activeRequest;
        activeRequest = null;

        if (elements.dialog.open) {
            elements.dialog.close();
        }

        request.resolve(result);
        restoreFocus(request);
        queueMicrotask(processNextRequest);
    }

    function clickedBackdrop(event) {
        if (event.target !== elements.dialog) {
            return false;
        }

        const bounds = elements.dialog.getBoundingClientRect();

        return event.clientX < bounds.left ||
            event.clientX > bounds.right ||
            event.clientY < bounds.top ||
            event.clientY > bounds.bottom;
    }

    function showDeclarativeAlerts() {
        document.querySelectorAll("[data-we-modal-alert]").forEach(source => {
            if (source.dataset.weModalHandled === "true") {
                return;
            }

            source.dataset.weModalHandled = "true";

            const message = source.textContent.trim();

            if (!message) {
                return;
            }

            source.hidden = true;
            window.WebExpressModal.alert({
                title: source.dataset.weModalTitle || "NOTICE",
                message,
                buttonLabel: source.dataset.weModalButton || "GOT IT"
            });
        });
    }

    function initialize() {
        if (initialized) {
            return true;
        }

        const found = findElements();

        if (!hasCompleteMarkup(found)) {
            return false;
        }

        elements = found;
        initialized = true;

        elements.confirmButton.addEventListener("click", () => {
            settleActiveRequest(activeRequest?.type === "confirm");
        });

        elements.cancelButton.addEventListener("click", () => {
            settleActiveRequest(false);
        });

        elements.dialog.addEventListener("cancel", event => {
            event.preventDefault();
            settleActiveRequest(false);
        });

        elements.dialog.addEventListener("click", event => {
            if (clickedBackdrop(event)) {
                settleActiveRequest(false);
            }
        });

        elements.dialog.addEventListener("close", () => {
            settleActiveRequest(false);
        });

        showDeclarativeAlerts();
        return true;
    }

    function scheduleInitialization() {
        if (initializationScheduled) {
            return;
        }

        initializationScheduled = true;

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", () => {
                initializationScheduled = false;
                initialize();
                processNextRequest();
            }, { once: true });
            return;
        }

        initializationScheduled = false;

        if (!initialize()) {
            while (requests.length > 0) {
                requests.shift().resolve(false);
            }
            console.error("Web Express modal markup is missing");
            return;
        }

        processNextRequest();
    }

    function captureReturnFocus() {
        const focused = document.activeElement;
        const dialog = elements?.dialog ?? document.getElementById("we-modal");

        if (dialog?.contains(focused) && activeRequest?.returnFocus) {
            return activeRequest.returnFocus;
        }

        return focused instanceof HTMLElement ? focused : null;
    }

    function enqueue(type, options = {}) {
        return new Promise(resolve => {
            requests.push({
                type,
                options,
                resolve,
                returnFocus: captureReturnFocus()
            });

            if (initialize()) {
                processNextRequest();
            } else {
                scheduleInitialization();
            }
        });
    }

    window.WebExpressModal = Object.freeze({
        alert(options = {}) {
            return enqueue("alert", options).then(() => undefined);
        },

        confirm(options = {}) {
            return enqueue("confirm", options);
        }
    });

    scheduleInitialization();
})();
