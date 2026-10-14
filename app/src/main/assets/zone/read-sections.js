(() => {
  'use strict';
  const key = '__tcgLabZoneSections';
  const storageKey = '__tcgLabZoneSectionsState';
  const labels = ['Home', 'Stats', 'Cards', 'Sets', 'Decks', 'What to Open', 'Play Sets', 'Binders', 'Display Boards', 'Themed Collections', 'Wishlist'];
  const routes = {
    'Home': /^\/players\/\d+\/?$/,
    'Stats': /^\/players\/\d+\/stats\/?$/,
    'Cards': /^\/players\/\d+\/cards\/?$/,
    'Sets': /^\/players\/\d+\/sets\/?$/,
    'Decks': /^\/players\/\d+\/decks\/?$/,
    'What to Open': /^\/players\/\d+\/tools\/what-to-open\/?$/,
    'Play Sets': /^\/players\/\d+\/tools\/play-sets\/?$/,
    'Binders': /^\/players\/\d+\/binders\/?$/,
    'Display Boards': /^\/players\/\d+\/display-boards\/?$/,
    'Themed Collections': /^\/players\/\d+\/themed-collections\/?$/,
    'Wishlist': /^\/players\/\d+\/wishlist\/?$/
  };
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  const normalize = value => (value || '').replace(/\s+/g, ' ').replace(/\s*CURRENT\s*$/i, '').trim().toLowerCase();
  const visible = el => !!el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  let state;
  try { state = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { state = null; }
  if (!state || state.status === 'complete' || !Array.isArray(state.sections)) {
    state = {status:'running', index:0, sections:[], awaiting:false, returningHome:false, startedAt:Date.now()};
  }
  window[key] = state;
  const save = () => { try { sessionStorage.setItem(storageKey, JSON.stringify(state)); } catch {} };
  const root = () => document.querySelector('main') || document.querySelector('[role="main"]') || document.querySelector('.js-player-detail-app') || document.body;
  const sectionText = () => {
    const area = root();
    return area ? (area.innerText || '').split(/\n+/).map(line => line.replace(/\s+/g, ' ').trim())
      .filter(line => line && !/^Go to$|^Jump to a section/i.test(line)).join('\n').slice(0, 12000) : '';
  };
  const pageReady = label => {
    const area = root();
    const text = (area && area.innerText) || '';
    const path = location.pathname;
    const heading = [...document.querySelectorAll('h1,h2,[role="heading"]')]
      .filter(visible).map(el => normalize(el.innerText)).join(' ');
    const expectedHeading = label === 'Home' ? /card stats/.test(heading) : heading.includes(normalize(label));
    const loading = /(^|\n)\s*(?:loading(?:\s+[a-z ]+)?|cargando)(?:\s*(?:\.{3}|…))?\s*(?=\n|$)/im.test(text);
    return !!routes[label] && routes[label].test(path) && expectedHeading && !loading;
  };
  const waitForSection = async label => {
    const deadline = Date.now() + 12000;
    while (Date.now() < deadline) {
      if (pageReady(label)) return sectionText();
      await wait(250);
    }
    return '';
  };
  const controls = () => [...document.querySelectorAll('button,[role="button"],a,summary,[role="option"],[role="menuitem"]')].filter(visible);
  const findExact = (text, scope=document) => {
    const wanted = normalize(text);
    return [...scope.querySelectorAll('button,[role="button"],a,summary,[role="option"],[role="menuitem"]')]
      .find(el => visible(el) && normalize(el.innerText || el.getAttribute('aria-label')) === wanted);
  };
  const openMenu = async () => {
    const names = new Set(labels.map(normalize));
    const candidates = controls();
    const trigger = candidates.find(el => {
      const text = normalize(el.innerText || el.getAttribute('aria-label') || el.getAttribute('title'));
      const isButton = el.tagName === 'BUTTON' || el.getAttribute('role') === 'button' || !!el.getAttribute('aria-haspopup');
      return isButton && (text === 'go to' || names.has(text));
    });
    if (!trigger) return false;
    trigger.click();
    await wait(250);
    return true;
  };
  const closeMenu = async () => {
    const dialog = document.querySelector('[role="dialog"]') || document.querySelector('[aria-modal="true"]');
    const close = (dialog && (findExact('Close', dialog) || findExact('Cerrar', dialog))) || findExact('Close') || findExact('Cerrar');
    if (close) close.click();
    else document.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape', bubbles:true}));
    await wait(150);
  };
  const select = async label => {
    if (!(await openMenu())) return false;
    const dialog = document.querySelector('[role="dialog"]') || document.querySelector('[aria-modal="true"]');
    const option = (dialog && findExact(label, dialog)) || findExact(label);
    if (!option) { await closeMenu(); return false; }
    state.awaiting = true;
    state.pending = label;
    save();
    option.click();
    return true;
  };
  (async () => {
    try {
      if (state.returningHome) {
        await waitForSection('Home');
        state.returningHome = false;
        state.status = 'complete';
        state.completedAt = Date.now();
        save();
        return;
      }
      while (state.index < labels.length) {
        if (state.awaiting) {
          const name = state.pending || labels[state.index];
          const body = await waitForSection(name);
          state.sections.push({name, status:body ? 'read' : 'timeout', text:body});
          state.index = Math.max(state.index + 1, labels.indexOf(name) + 1);
          state.awaiting = false;
          delete state.pending;
          save();
          continue;
        }
        const label = labels[state.index];
        if (state.index === 0) {
          const body = await waitForSection(label);
          state.sections.push({name:label, status:body ? 'read' : 'timeout', text:body});
          state.index++;
          save();
          continue;
        }
        if (!(await select(label))) {
          state.sections.push({name:label, status:'unavailable', text:''});
          state.index++;
          save();
        }
      }
      state.returningHome = true;
      save();
      if (await select('Home')) await waitForSection('Home');
      state.returningHome = false;
      state.status = 'complete';
      state.completedAt = Date.now();
      save();
    } catch (error) {
      state.error = String(error && error.name || 'read_error');
      state.status = 'complete';
      state.completedAt = Date.now();
      save();
    }
  })();
  return 'started';
})();