import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = (await readFile(new URL('../../main/resources/static/js/pages/profile.js', import.meta.url), 'utf8'))
    .replace(/^import .*;\r?\n/, '');
const initial = {firstName: 'Olena', lastName: 'Kovalenko', email: 'olena@example.com', phone: '+380671234567'};

test('loads real client data and enables editing only after loading', async () => {
    const page = await openProfile();
    assert.equal(page.requests.length, 1);
    assert.equal(page.requests[0].url, '/api/profile');
    assert.equal(page.requests[0].options.cache, 'no-store');
    assert.equal(page.nodes['display-name'].textContent, 'Olena Kovalenko');
    assert.equal(page.nodes['edit-button'].disabled, false);
});

test('client save sends trimmed allowed fields and shows the server response', async () => {
    const page = await openProfile();
    page.edit();
    page.fields.firstName.value = '  Vika  ';
    page.fields.lastName.value = 'Roman';
    await page.save();
    const body = JSON.parse(page.requests[1].options.body);
    assert.deepEqual(body, {...initial, firstName: 'Vika', lastName: 'Roman'});
    assert.equal(page.requests[1].options.method, 'PUT');
    assert.equal(page.nodes['display-name'].textContent, 'Vika Roman');
    assert.equal(page.nodes['profile-form'].hidden, true);
    assert.equal(page.nodes['profile-message'].textContent, 'Profile updated.');
    assert.equal(page.alerts.length, 0);
});

test('cancel discards the draft and makes no save request', async () => {
    const page = await openProfile();
    page.edit();
    page.fields.firstName.value = 'Unsaved';
    page.nodes['cancel-button'].listeners.click();
    page.edit();
    assert.equal(page.fields.firstName.value, initial.firstName);
    assert.equal(page.requests.length, 1);
});

test('failed save keeps the draft and shows the existing modal without false success', async () => {
    const page = await openProfile({saveFails: true});
    page.edit();
    page.fields.firstName.value = 'Vika';
    await page.save();
    assert.equal(page.nodes['profile-form'].hidden, false);
    assert.equal(page.fields.firstName.value, 'Vika');
    assert.equal(page.nodes['display-name'].textContent, 'Olena Kovalenko');
    assert.equal(page.alerts[0].message, 'Email address is already in use.');
    assert.equal(page.nodes['cancel-button'].disabled, false);
});

test('loading failure leaves editing disabled and does not show mock data', async () => {
    const page = await openProfile({loadFails: true});
    assert.equal(page.nodes['edit-button'].disabled, true);
    assert.equal(page.nodes['display-name'].textContent, 'Not available');
    assert.match(page.nodes['profile-message'].textContent, /Reload the page/);
});

test('courier saves selected KATOTTG code and decimal weight through its own endpoint', async () => {
    const page = await openProfile({courier: true});
    page.edit();
    page.fields.workZone.value = 'Lviv';
    page.selection = {name: 'Lviv', code: 'UA46060250010050297'};
    page.fields.maxWeight.value = '25.5';
    await page.save();
    const body = JSON.parse(page.requests[1].options.body);
    assert.equal(page.requests[1].url, '/api/courier/profile');
    assert.equal(body.workZone, 'Lviv');
    assert.equal(body.workZoneCode, 'UA46060250010050297');
    assert.equal(body.maxWeight, 25.5);
    assert.equal(page.nodes['display-work-zone'].textContent, 'Lviv');
    assert.equal(page.nodes['display-max-weight'].textContent, '25.5 kg');
});

test('invalid fields or unselected courier settlement prevent saving', async () => {
    const client = await openProfile({valid: false});
    await client.save();
    assert.equal(client.requests.length, 1);
    const courier = await openProfile({courier: true, valid: false});
    courier.edit();
    courier.fields.workZone.value = 'Unknown place';
    await courier.save();
    assert.equal(courier.requests.length, 1);
});

test('courier failure explains that sequential updates may have partially succeeded', async () => {
    const page = await openProfile({courier: true, saveFails: true});
    page.edit();
    await page.save();
    assert.match(page.alerts[0].message, /Some changes may already have been saved/);
    assert.equal(page.nodes['profile-form'].hidden, false);
});

async function openProfile({courier = false, loadFails = false, saveFails = false, valid = true} = {}) {
    const requests = [];
    const alerts = [];
    const nodes = {};
    const fields = {};
    function element() {
        let value = '';
        return {
            hidden: false, disabled: false, textContent: 'Not available', listeners: {},
            get value() { return value; }, set value(text) { value = String(text); },
            addEventListener(name, callback) { this.listeners[name] = callback; },
            focus() {}, setCustomValidity() {}, classList: {toggle() {}}
        };
    }
    for (const name of ['firstName', 'lastName', 'email', 'phone', ...(courier ? ['workZone', 'maxWeight'] : [])]) {
        fields[name] = element();
    }
    for (const id of ['profile-form', 'profile-view', 'edit-button', 'cancel-button', 'profile-message',
        'display-name', 'display-email', 'display-phone', 'display-work-zone', 'display-max-weight', 'first-name']) {
        nodes[id] = element();
    }
    nodes['edit-button'].disabled = true;
    nodes['profile-form'].hidden = true;
    nodes['profile-form'].dataset = {profileUrl: courier ? '/api/courier/profile' : '/api/profile'};
    nodes['profile-form'].elements = {namedItem: name => fields[name] ?? null};
    nodes['profile-form'].reportValidity = () => valid;
    nodes['profile-form'].querySelectorAll = () => [nodes['cancel-button']];
    const page = {nodes, fields, requests, alerts, selection: null};
    const fetch = async (url, options) => {
        requests.push({url, options});
        const saving = options.method === 'PUT';
        const failed = saving ? saveFails : loadFails;
        const result = saving ? JSON.parse(options.body) : {...initial,
            ...(courier ? {workZone: 'Kyiv', workZoneCode: 'UA80000000000093317', maxWeight: 20} : {})};
        return {ok: !failed, json: async () => failed ? {detail: 'Email address is already in use.'} : result};
    };
    const autocomplete = {
        close() {}, load: async () => [], validate: () => valid,
        setValue(name, code) { fields.workZone.value = name; page.selection = code ? {name, code} : null; },
        getSelection: () => page.selection
    };
    await vm.runInNewContext(source, {
        document: {getElementById: id => nodes[id]}, fetch,
        createKatottgAutocomplete: () => autocomplete,
        window: {WebExpressModal: {alert: options => alerts.push(options)}}
    });
    page.edit = () => nodes['edit-button'].listeners.click();
    page.save = () => nodes['profile-form'].listeners.submit({preventDefault() {}});
    return page;
}
