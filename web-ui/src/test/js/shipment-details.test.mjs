import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../../main/resources/static/js/components/shipment-cancel.js', import.meta.url), 'utf8');

test('declining the existing modal does not send a cancellation request', async () => {
    const page = openPage({confirmed: false});
    await page.click();
    assert.equal(page.confirmations.length, 1);
    assert.equal(page.requests.length, 0);
    assert.equal(page.button.disabled, false);
    assert.equal(page.reloads, 0);
});

test('confirmed cancellation updates the card and disables tracking without reloading', async () => {
    const page = openPage();
    await page.click();
    assert.equal(page.requests.length, 1);
    assert.equal(page.requests[0].url, '/api/shipments/515/cancel');
    assert.equal(page.requests[0].options.method, 'POST');
    assert.equal(page.reloads, 0);
    assert.equal(page.status.textContent, 'CANCELLED');
    assert.equal(page.status.className, 'status cancelled');
    assert.equal(page.tracking.attributes.href, undefined);
    assert.equal(page.tracking.attributes['aria-disabled'], 'true');
    assert.equal(page.button.removed, true);
    assert.equal(page.alerts.length, 0);
});

test('pickup conflict shows the backend message without pretending cancellation succeeded', async () => {
    const page = openPage({rejected: true});
    await page.click();
    assert.equal(page.reloads, 0);
    assert.equal(page.alerts[0].message, 'The courier has already picked up the parcel.');
    assert.equal(page.button.disabled, false);
    assert.equal(page.status.textContent, 'ASSIGNED');
});

test('network failure shows a neutral message and allows retry', async () => {
    const page = openPage({networkFailure: true});
    await page.click();
    assert.equal(page.reloads, 0);
    assert.match(page.alerts[0].message, /Cancellation could not be confirmed/);
    assert.equal(page.button.disabled, false);
});

test('a second click while confirmation is open does not duplicate requests', async () => {
    const page = openPage();
    await Promise.all([page.click(), page.click()]);
    assert.equal(page.confirmations.length, 1);
    assert.equal(page.requests.length, 1);
});

test('completed shipments without a cancel button do not create requests or modals', () => {
    const page = openPage({hasButton: false});
    assert.equal(page.requests.length, 0);
    assert.equal(page.confirmations.length, 0);
});

test('cancelling one parcel updates only its card in the list', async () => {
    const page = openPage({count: 2});
    await page.click(1);
    assert.equal(page.requests[0].url, '/api/shipments/516/cancel');
    assert.equal(page.cards[0].status.textContent, 'ASSIGNED');
    assert.equal(page.cards[0].tracking.attributes.href, '/tracking?shipmentId=515');
    assert.equal(page.cards[1].status.textContent, 'CANCELLED');
    assert.equal(page.cards[1].tracking.attributes['aria-disabled'], 'true');
});

function openPage({confirmed = true, rejected = false, networkFailure = false, hasButton = true, count = 1} = {}) {
    const requests = [];
    const confirmations = [];
    const alerts = [];
    const cards = Array.from({length: count}, (_, index) => {
        const id = 515 + index;
        const status = {textContent: 'ASSIGNED', className: 'status transit'};
        const tracking = {
            attributes: {href: `/tracking?shipmentId=${id}`},
            removeAttribute(name) { delete this.attributes[name]; },
            setAttribute(name, value) { this.attributes[name] = value; }
        };
        const card = {querySelector: selector => selector === '[data-shipment-status]' ? status : tracking};
        const button = {
            disabled: false,
            dataset: {cancelUrl: `/api/shipments/${id}/cancel`},
            addEventListener(name, callback) { this.handler = callback; },
            closest: () => card,
            remove() { this.removed = true; }
        };
        return {button, status, tracking};
    });
    const page = {...cards[0], cards, requests, confirmations, alerts, reloads: 0,
        click: (index = 0) => cards[index].button.handler()};
    vm.runInNewContext(source, {
        document: {querySelectorAll: () => hasButton ? cards.map(card => card.button) : []},
        fetch: async (url, options) => {
            requests.push({url, options});
            if (networkFailure) throw new Error('private connection details');
            return {ok: !rejected, json: async () => ({detail: 'The courier has already picked up the parcel.'})};
        },
        window: {
            location: {reload() { page.reloads++; }},
            WebExpressModal: {
                confirm: async options => { confirmations.push(options); return confirmed; },
                alert: async options => { alerts.push(options); }
            }
        }
    });
    return page;
}
