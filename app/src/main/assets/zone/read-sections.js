(() => {
  'use strict';
  const key = '__tcgLabZoneSections';
  const storageKey = '__tcgLabZoneSectionsState';
  const labels = ['Home', 'Stats', 'Cards', 'Sets', 'Decks', 'What to Open', 'Play Sets', 'Binders', 'Display Boards', 'Missions'];
  let state;
  try { state = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { state = null; }
  if (!state || state.status === 'complete' || !Array.isArray(state.sections)) {
    state = {status:'running', index:0, sections:[], awaiting:false, returningHome:false, startedAt:Date.now()};
  }
  window[key] = state;
  const save = () => { try { sessionStorage.setItem(storageKey, JSON.stringify(state)); } catch {} };
  const normalize = value => (value || '').replace(/\s+/g, ' ').replace(/\s*CURRENT\s*$/i, '').trim().toLowerCase();
  const visible = el => !!el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  const controls = () => [...document.querySelectorAll('button,[role="button"],a,summary,[role="option"]')].filter(visible);
  const findExact = (text, scope=document) => {
    const wanted = normalize(text);
    return [...scope.querySelectorAll('button,[role="button"],a,summary,[role="option"],[role="menuitem"]')]
      .find(el => visible(el) && normalize(el.innerText || el.getAttribute('aria-label')) === wanted);
  };
  const sectionText = () => {
    const root = document.querySelector('main') || document.querySelector('[role="main"]') || document.body;
    return root ? (root.innerText || '').split(/\n+/).map(line => line.replace(/\s+/g, ' ').trim())
      .filter(line => line && !/^Go to$|^Jump to a section/i.test(line)).join('\n').slice(0, 12000) : '';
  };
  const openMenu = async () => {
    const candidates = controls();
    let trigger = candidates.find(el => /^(home|stats|cards|sets|decks|what to open|play sets|binders|display boards)(?:\s*[⌄v▼])?$/i.test((el.innerText || '').trim()));
    if (!trigger) trigger = candidates.find(el => /^(go to|home|stats|cards|sets|decks|what to open|play sets|binders|display boards)$/i.test((el.getAttribute('aria-label') || '').trim()));
    if (!trigger) return false;
    trigger.click(); await wait(250); return true;
  };
  const closeMenu = async () => {
    const close = findExact('Close') || findExact('Cerrar');
    if (close) close.click();
    else document.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape', bubbles:true}));
    await wait(150);
  };
  const select = async label => {
    if (!(await openMenu())) return false;
    const dialog = document.querySelector('[role="dialog"]');
    const option = (dialog && findExact(label, dialog)) || findExact(label);
    if (!option) { await closeMenu(); return false; }
    state.awaiting = true;
    state.pending = label;
    save();
    option.click();
    await wait(1000);
    return true;
  };
  (async () => {
    try {
      if (state.returningHome) {
        await wait(250);
        state.returningHome = false;
        state.status = 'complete';
        state.completedAt = Date.now();
        save();
        return;
      }
      while (state.index < labels.length) {
        if (state.awaiting) {
          await wait(400);
          const name = state.pending || labels[state.index];
          const body = sectionText();
          state.sections.push({name, status:body ? 'read' : 'empty', text:body});
          state.index = Math.max(state.index + 1, labels.indexOf(name) + 1);
          state.awaiting = false;
          delete state.pending;
          save();
          continue;
        }
        const label = labels[state.index];
        if (state.index === 0) {
          const body = sectionText();
          state.sections.push({name:label, status:body ? 'read' : 'empty', text:body});
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
      if (await select('Home')) {
        // If this was a full navigation, onPageFinished reinjects this script and completes it.
        await wait(1000);
      }
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