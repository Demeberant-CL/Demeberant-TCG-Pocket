(() => {
  'use strict';
  const key = '__tcgLabZoneSections';
  const storageKey = '__tcgLabZoneSectionsState';
  const labels = ['Home', 'Stats', 'Cards', 'Sets', 'Decks', 'What to Open', 'Play Sets', 'Binders', 'Display Boards', 'Theme Collections', 'Wishlist'];
  const routes = {
    'Home': /^\/players\/\d+\/?$/,
    'Stats': /^\/players\/\d+\/(?:stats)\/?$/,
    'Cards': /^\/players\/\d+\/(?:cards)\/?$/,
    'Sets': /^\/players\/\d+\/(?:sets)\/?$/,
    'Decks': /^\/players\/\d+\/(?:decks)\/?$/,
    'What to Open': /^\/players\/\d+\/(?:tools\/)?what-to-open\/?$/,
    'Play Sets': /^\/players\/\d+\/(?:tools\/)?play-sets\/?$/,
    'Binders': /^\/players\/\d+\/binders\/?$/,
    'Display Boards': /^\/players\/\d+\/display-boards\/?$/,
    'Theme Collections': /^\/players\/\d+\/(?:theme|themed)-collections\/?$/,
    'Wishlist': /^\/players\/\d+\/wishlist\/?$/
  };
  const headingAliases = {
    'Home': ['home'],
    'Stats': ['stats', 'card stats'],
    'Cards': ['cards'],
    'Sets': ['sets'],
    'Decks': ['decks'],
    'What to Open': ['what to open', 'what should i open'],
    'Play Sets': ['play sets'],
    'Binders': ['binders'],
    'Display Boards': ['display boards'],
    'Theme Collections': ['theme collections', 'themed collections'],
    'Wishlist': ['wishlist', 'wish list']
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
  const headingElements = () => [...document.querySelectorAll('h1,h2,h3,h4,[role="heading"]')].filter(visible);
  const matchesHeading = (el, label) => {
    const actual = normalize(el.innerText);
    return (headingAliases[label] || [normalize(label)]).some(alias => actual === alias || (label === 'Stats' && actual.endsWith(' stats')));
  };
  const sectionNode = label => {
    const area = root();
    let heading = label === 'Home'
      ? headingElements().find(el => el.tagName === 'H1' && area.contains(el))
      : headingElements().find(el => matchesHeading(el, label));
    if (!heading && label === 'Home') heading = headingElements().find(el => matchesHeading(el, label));
    let target = heading;
    if (!target && location.hash.length > 1) {
      try { target = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch {}
    }
    if (!target) return null;
    const semantic = target.closest('section,article,[role="region"]');
    if (semantic && semantic !== area && semantic !== document.body) return semantic;
    let candidate = target;
    let node = target;
    while (node.parentElement && node.parentElement !== area && node.parentElement !== document.body) {
      const parent = node.parentElement;
      const otherHeadings = [...parent.querySelectorAll('h1,h2,h3,h4,[role="heading"]')].filter(el => el !== heading);
      if (otherHeadings.length) break;
      candidate = parent;
      node = parent;
    }
    return candidate;
  };
  const sectionText = label => {
    const node = sectionNode(label);
    const text = node && (node.innerText || '');
    return (text || '').split(/\n+/).map(line => line.replace(/\s+/g, ' ').trim())
      .filter(line => line && !/^Go to$|^Jump to a section/i.test(line)).join('\n').slice(0, 12000);
  };
  const pageReady = label => {
    const path = location.pathname;
    const baseProfile = /^\/players\/\d+\/?$/.test(path);
    const route = routes[label] && routes[label].test(path);
    if (!baseProfile && !route) return false;
    const text = sectionText(label);
    if (!text) return false;
    return !/(^|\n)\s*(?:loading(?:\s+[a-z ]+)?|cargando)(?:\s*(?:\.{3}|…))?\s*(?=\n|$)/im.test(text);
  };
  const waitForSection = async label => {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
      if (pageReady(label)) return sectionText(label);
      await wait(300);
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
    const trigger = controls().find(el => {
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
    state.pendingHref = option.href || '';
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
          state.sections.push({name, status:body ? 'read' : 'timeout', url:location.href.split('?')[0], text:body});
          state.index = Math.max(state.index + 1, labels.indexOf(name) + 1);
          state.awaiting = false;
          delete state.pending;
          delete state.pendingHref;
          save();
          continue;
        }
        const label = labels[state.index];
        if (state.index === 0) {
          const body = await waitForSection(label);
          state.sections.push({name:label, status:body ? 'read' : 'timeout', url:location.href.split('?')[0], text:body});
          state.index++;
          save();
          continue;
        }
        if (!(await select(label))) {
          state.sections.push({name:label, status:'unavailable', url:location.href.split('?')[0], text:''});
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
      state.status = 'error';
      state.completedAt = Date.now();
      save();
    }
  })();
  return 'started';
})();