import test from 'node:test';
import assert from 'node:assert/strict';

import {
    expandSettlementData,
    findSettlements,
    normalizeSearchText,
    selectionMatchesInput
} from '../../main/resources/static/js/components/katottg-autocomplete.js';

const settlements = [
    {name: 'Кривий Ріг', type: 'місто', oblast: 'Дніпропетровська', code: 'UA1'},
    {name: 'Криве', type: 'село', oblast: 'Львівська', code: 'UA2'},
    {name: 'Криве', type: 'село', oblast: 'Тернопільська', code: 'UA3'},
    {name: 'Некрасове', type: 'село', oblast: 'Вінницька', code: 'UA4'}
];

test('searches Ukrainian settlement names without regard to case', () => {
    assert.deepEqual(
        findSettlements(settlements, 'КРИ').map(item => item.code),
        ['UA2', 'UA3', 'UA1']
    );
    assert.equal(normalizeSearchText('  Мар’янівка '), "мар'янівка");
});

test('requires two characters and returns at most eight suggestions', () => {
    assert.deepEqual(findSettlements(settlements, 'к'), []);
    assert.equal(findSettlements(Array.from({length: 12}, (_, index) => ({
        name: `Село ${index}`,
        code: `UA${index}`
    })), 'се').length, 8);
});

test('keeps settlements with duplicate names as separate choices', () => {
    const matches = findSettlements(settlements, 'Криве');
    assert.equal(matches.length, 2);
    assert.deepEqual(matches.map(item => item.oblast), ['Львівська', 'Тернопільська']);
});

test('accepts only the selected KATOTTG settlement', () => {
    const selected = {name: 'Кривий Ріг', code: 'UA1'};
    assert.equal(selectionMatchesInput('Кривий Ріг', selected), true);
    assert.equal(selectionMatchesInput('Криве', selected), false);
    assert.equal(selectionMatchesInput('Кривий Ріг', null), false);
});

test('expands the compact official data format', () => {
    const result = expandSettlementData({
        fields: ['name', 'type', 'oblast', 'raion', 'hromada', 'code'],
        types: {M: 'місто'},
        settlements: [['Кривий Ріг', 'M', 'Дніпропетровська', 'Криворізький', 'Криворізька', 'UA1']]
    });

    assert.deepEqual(result[0], {
        name: 'Кривий Ріг',
        type: 'місто',
        oblast: 'Дніпропетровська',
        raion: 'Криворізький',
        hromada: 'Криворізька',
        code: 'UA1'
    });
});
