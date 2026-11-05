'use strict';

const byId = (id) => document.getElementById(id);
const form = byId('search-form');
const query = byId('query');
const status = byId('status');
const didYouMean = byId('didyoumean');
const correctionButton = byId('correction');
const panel = byId('panel');
const list = byId('results');
const meta = byId('meta');
const dictionaryLabel = byId('dictionary');

const SUGGESTION_LIMIT = 12;
const DEBOUNCE_MS = 140;
const number = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 });
const distanceFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 });

let generation = 0;
let controller;
let timer;
let items = [];
let active = -1;

function resetResults() {
  items = [];
  active = -1;
  list.replaceChildren();
  query.setAttribute('aria-expanded', 'false');
  query.removeAttribute('aria-activedescendant');
  panel.hidden = true;
}

function setStatus(text, exact = false) {
  status.textContent = text;
  status.classList.toggle('exact', exact);
}

function setCorrection(correction) {
  if (!correction) {
    didYouMean.hidden = true;
    correctionButton.textContent = '';
    return;
  }
  correctionButton.textContent = correction.term;
  correctionButton.dataset.term = correction.term;
  didYouMean.hidden = false;
}

function invalidate() {
  generation += 1;
  clearTimeout(timer);
  if (controller) {
    controller.abort();
  }
  resetResults();
  setCorrection(null);
  meta.textContent = '';
  return generation;
}

function schedule() {
  const version = invalidate();
  const value = query.value.trim();
  if (!value) {
    setStatus('Bereit.');
    return;
  }
  setStatus('Suche läuft …');
  timer = setTimeout(() => search(version, value), DEBOUNCE_MS);
}

async function search(version, value) {
  controller = new AbortController();
  const started = performance.now();
  try {
    const params = new URLSearchParams({ q: value, k: String(SUGGESTION_LIMIT) });
    const response = await fetch(`/api/suggest?${params}`, { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const data = await response.json();
    if (version !== generation) {
      return;
    }
    render(data, performance.now() - started);
  } catch (failure) {
    if (version !== generation || failure.name === 'AbortError') {
      return;
    }
    resetResults();
    setStatus(failure instanceof TypeError
      ? 'Server nicht erreichbar.'
      : `Suche fehlgeschlagen: ${failure.message}`);
  }
}

function render(data, responseMillis) {
  resetResults();
  if (data.dictionarySize > 0) {
    dictionaryLabel.textContent = `${number.format(data.dictionarySize)} Begriffe im Wörterbuch`;
  }
  items = data.suggestions;
  renderList();
  if (data.exact) {
    setStatus(`„${data.query}“ steht im Wörterbuch.`, true);
  } else if (items.length === 0) {
    setStatus(`Keine Treffer für „${data.query}“.`);
  } else {
    const suffix = items.length === 1 ? 'Vorschlag' : 'Vorschläge';
    setStatus(`${items.length} ${suffix} für „${data.query}“`);
  }
  setCorrection(data.correction && data.correction.term !== data.query ? data.correction : null);
  meta.textContent = `${number.format(data.micros)} µs Lookup · `
    + `${distanceFormat.format(responseMillis)} ms Antwort`;
}

function renderList() {
  const fragment = document.createDocumentFragment();
  items.forEach((item, index) => {
    const row = document.createElement('li');
    row.id = `suggestion-${index}`;
    row.setAttribute('role', 'option');
    row.setAttribute('aria-selected', 'false');

    const term = document.createElement('span');
    term.className = 'term';
    term.textContent = item.term;

    const distance = document.createElement('span');
    distance.className = `distance${item.distance === 0 ? ' zero' : ''}`;
    distance.textContent = distanceFormat.format(item.distance);

    const frequency = document.createElement('span');
    frequency.className = 'frequency';
    frequency.textContent = number.format(item.frequency);

    row.append(term, distance, frequency);
    row.addEventListener('mousedown', (event) => event.preventDefault());
    row.addEventListener('click', () => choose(index));
    fragment.append(row);
  });
  list.append(fragment);
  panel.hidden = items.length === 0;
  query.setAttribute('aria-expanded', String(items.length > 0));
}

function choose(index) {
  const item = items[index];
  if (!item) {
    return;
  }
  query.value = item.term;
  const version = invalidate();
  setStatus('Suche läuft …');
  search(version, item.term);
}

function move(delta) {
  if (items.length === 0) {
    return;
  }
  active = (active + delta + items.length) % items.length;
  [...list.children].forEach((row, index) => {
    row.setAttribute('aria-selected', String(index === active));
  });
  const current = list.children[active];
  if (current) {
    query.setAttribute('aria-activedescendant', current.id);
    current.scrollIntoView({ block: 'nearest' });
  }
}

query.addEventListener('input', schedule);

query.addEventListener('keydown', (event) => {
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    move(1);
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    move(-1);
  } else if (event.key === 'Enter') {
    if (active >= 0) {
      event.preventDefault();
      choose(active);
    }
  } else if (event.key === 'Escape') {
    invalidate();
    setStatus('Bereit.');
  }
});

form.addEventListener('submit', (event) => {
  event.preventDefault();
  if (active >= 0) {
    choose(active);
    return;
  }
  const version = invalidate();
  if (query.value.trim()) {
    search(version, query.value.trim());
  }
});

correctionButton.addEventListener('click', () => {
  const term = correctionButton.dataset.term;
  if (!term) {
    return;
  }
  query.value = term;
  const version = invalidate();
  search(version, term);
});

window.addEventListener('load', () => {
  const shared = new URLSearchParams(window.location.search).get('q');
  if (shared) {
    query.value = shared;
    search(invalidate(), shared.trim());
  }
  loadDictionaryInfo();
});

async function loadDictionaryInfo() {
  try {
    const response = await fetch('/api/suggest?q=&k=1');
    const data = await response.json();
    if (data.dictionarySize > 0) {
      dictionaryLabel.textContent = `${number.format(data.dictionarySize)} Begriffe im Wörterbuch`;
    }
  } catch {
    dictionaryLabel.textContent = 'Wörterbuch nicht erreichbar';
  }
}
