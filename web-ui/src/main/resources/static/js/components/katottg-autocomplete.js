const MIN_QUERY_LENGTH = 2;
const MAX_SUGGESTIONS = 8;
const DATA_URL = '/data/katottg-settlements.json';

let settlementsPromise = null;

export function normalizeSearchText(value) {
    return String(value ?? '')
        .trim()
        .toLocaleLowerCase('uk-UA')
        .replaceAll('’', "'")
        .replaceAll('ʼ', "'");
}

export function findSettlements(settlements, query, limit = MAX_SUGGESTIONS) {
    const normalizedQuery = normalizeSearchText(query);
    if (normalizedQuery.length < MIN_QUERY_LENGTH) return [];

    return settlements
        .map((settlement, index) => ({
            settlement,
            index,
            normalizedName: normalizeSearchText(settlement.name)
        }))
        .filter(item => item.normalizedName.includes(normalizedQuery))
        .sort((left, right) => {
            const leftStarts = left.normalizedName.startsWith(normalizedQuery);
            const rightStarts = right.normalizedName.startsWith(normalizedQuery);
            if (leftStarts !== rightStarts) return leftStarts ? -1 : 1;

            return left.settlement.name.localeCompare(right.settlement.name, 'uk-UA') ||
                left.index - right.index;
        })
        .slice(0, Math.max(0, limit))
        .map(item => item.settlement);
}

export function selectionMatchesInput(inputValue, selectedSettlement) {
    return Boolean(selectedSettlement?.code) &&
        normalizeSearchText(inputValue) === normalizeSearchText(selectedSettlement.name);
}

export function expandSettlementData(payload) {
    if (!payload || !Array.isArray(payload.fields) || !Array.isArray(payload.settlements)) {
        throw new Error('Invalid KATOTTG data');
    }

    const fieldIndexes = Object.fromEntries(payload.fields.map((field, index) => [field, index]));
    return payload.settlements.map(row => ({
        name: row[fieldIndexes.name],
        type: payload.types[row[fieldIndexes.type]],
        oblast: row[fieldIndexes.oblast] || '',
        raion: row[fieldIndexes.raion] || '',
        hromada: row[fieldIndexes.hromada] || '',
        code: row[fieldIndexes.code]
    }));
}

export function formatSettlementContext(settlement) {
    const parts = [settlement.type];
    if (settlement.oblast) {
        parts.push(settlement.oblast.startsWith('Автономна')
            ? settlement.oblast
            : `${settlement.oblast} область`);
    }
    if (settlement.raion) parts.push(`${settlement.raion} район`);
    if (settlement.hromada) parts.push(`${settlement.hromada} громада`);
    return parts.join(' · ');
}

export function loadKatottgSettlements() {
    if (!settlementsPromise) {
        settlementsPromise = fetch(DATA_URL)
            .then(response => {
                if (!response.ok) throw new Error('KATOTTG data is unavailable');
                return response.json();
            })
            .then(expandSettlementData)
            .catch(error => {
                settlementsPromise = null;
                throw error;
            });
    }
    return settlementsPromise;
}

export function createKatottgAutocomplete({
    input,
    codeInput,
    suggestions,
    help,
    onChange = () => {}
}) {
    let selectedSettlement = null;
    let visibleSuggestions = [];
    let searchVersion = 0;

    function setHelp(text, isError = false) {
        help.textContent = text;
        help.hidden = !text;
        help.classList.toggle('katottg-help--error', isError);
    }

    function close() {
        suggestions.hidden = true;
        suggestions.replaceChildren();
        input.setAttribute('aria-expanded', 'false');
        visibleSuggestions = [];
    }

    function render(items) {
        close();
        if (items.length === 0) {
            setHelp('No matching settlement found.', true);
            return;
        }

        visibleSuggestions = items;
        const fragment = document.createDocumentFragment();
        items.forEach((settlement, index) => {
            const option = document.createElement('button');
            option.type = 'button';
            option.className = 'katottg-option';
            option.dataset.index = String(index);
            option.setAttribute('role', 'option');

            const name = document.createElement('span');
            name.className = 'katottg-option__name';
            name.textContent = settlement.name;

            const context = document.createElement('span');
            context.className = 'katottg-option__context';
            context.textContent = formatSettlementContext(settlement);

            option.append(name, context);
            fragment.append(option);
        });

        suggestions.append(fragment);
        suggestions.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        setHelp('Choose a settlement from the official KATOTTG list.');
    }

    function choose(settlement) {
        selectedSettlement = settlement;
        input.value = settlement.name;
        codeInput.value = settlement.code;
        input.setCustomValidity('');
        setHelp('');
        close();
        onChange(selectedSettlement);
        input.focus();
    }

    async function search() {
        const currentSearch = ++searchVersion;
        if (!selectionMatchesInput(input.value, selectedSettlement)) {
            selectedSettlement = null;
            codeInput.value = '';
        }
        input.setCustomValidity('');
        onChange(selectedSettlement);

        if (normalizeSearchText(input.value).length < MIN_QUERY_LENGTH) {
            close();
            setHelp('Enter at least 2 Ukrainian letters.');
            return;
        }

        try {
            const settlements = await loadKatottgSettlements();
            if (currentSearch !== searchVersion) return;
            render(findSettlements(settlements, input.value));
        } catch {
            if (currentSearch !== searchVersion) return;
            close();
            setHelp('The KATOTTG list is temporarily unavailable.', true);
        }
    }

    function setValue(name, code = '') {
        input.value = name;
        codeInput.value = code;
        selectedSettlement = code ? {name, code} : null;
        input.setCustomValidity('');
        setHelp('Choose a settlement from the official KATOTTG list.');
        close();
        onChange(selectedSettlement);
    }

    function validate(allowedUnselectedValue = '') {
        const allowedValueMatches = allowedUnselectedValue &&
            input.value.trim() === allowedUnselectedValue;
        const valid = allowedValueMatches ||
            selectionMatchesInput(input.value, selectedSettlement);

        input.setCustomValidity(valid
            ? ''
            : 'Choose a settlement from the KATOTTG suggestions.');
        return valid;
    }

    input.addEventListener('input', search);
    input.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            close();
        } else if (event.key === 'Enter' && visibleSuggestions.length > 0) {
            event.preventDefault();
            choose(visibleSuggestions[0]);
        }
    });

    suggestions.addEventListener('click', event => {
        const option = event.target.closest('.katottg-option');
        if (!option) return;
        const settlement = visibleSuggestions[Number(option.dataset.index)];
        if (settlement) choose(settlement);
    });

    document.addEventListener('click', event => {
        const field = event.target.closest('.katottg-field');
        if (!field || !field.contains(input)) close();
    });

    return Object.freeze({
        close,
        getSelection: () => selectedSettlement,
        load: loadKatottgSettlements,
        setValue,
        validate
    });
}
