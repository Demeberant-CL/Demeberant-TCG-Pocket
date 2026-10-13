(() => {
  'use strict';
  const key = '__tcgLabZoneSections';
  const labels = ['Home', 'Stats', 'Cards', 'Sets', 'Decks', 'What to Open', 'Play Sets', 'Binders', 'Display Boards', 'Missions'];
  const state = {status:'running', index:0, sections:[], startedAt:Date.now()};
  window[key] = state;
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
    if (!root) return '';
    const excluded = 'script,style,noscript,nav,header,footer,form,input,textarea,[contenteditable],[hidden],[aria-hidden="true"],[role="navigation"],[role="dialog"]';
    return (root.innerText || '').split(/\n+/).map(line => line.replace(/\s+/g,' ').trim())
      .filter(line => line && !line.match(/^Go to$|^Jump to a section/i))
      .join('\n').slice(0, 12000);
  };
  const openMenu = async () => {
    const current = (document.querySelector('[aria-current="page"]')?.innerText || '').trim();
    const candidates = controls();
    let trigger = candidates.find(el => normalize(el.innerText).replace(/[^a-z ]/g,'').trim() === normalize(current).replace(/[^a-z ]/g,'').trim() && (el.hasAttribute('aria-haspopup') || /HOME|STATS|CARDS|SETS|DECKS|BINDERS/i.test(el.innerText || '')));
    if (!trigger) trigger = candidates.find(el => /^(home|stats|cards|sets|decks|what to open|play sets|binders|display boards)(?:\s*[⌄v▼])?$/i.test((el.innerText || '').trim()));
    if (!trigger) trigger = candidates.find(el => /^(go to|home|stats|cards|sets|decks|what to open|play sets|binders|display boards)$/i.test((el.getAttribute('aria-label') || '').trim()));
    if (!trigger) return false;
    trigger.click(); await wait(250); return true;
  };
  const clickSection = async label => {
    let option = findExact(label);
    if (!option) {
      const dialog = document.querySelector('[role="dialog"]');
      if (dialog) option = findExact(label, dialog);
    }
    if (!option) return false;
    option.click(); await wait(900); return true;
  };
  (async () => {
    try {
      for (let i=0; i<labels.length; i++) {
        state.index = i;
        const label = labels[i];
        let selected = true;
        if (i > 0) selected = (await openMenu()) && (await clickSection(label));
        else {
          // Start from Home, even when the profile opened on another section.
          const current = document.querySelector('[aria-current="page"]')?.innerText || '';
          if (normalize(current) !== 'home') selected = (await openMenu()) && (await clickSection('Home'));
        }
        await wait(350);
        const body = selected ? sectionText() : '';
        state.sections.push({name:label, status:selected && body ? 'read' : selected ? 'empty' : 'unavailable', text:body});
        await wait(180);
      }
    } catch (error) {
      state.error = String(error && error.name || 'read_error');
    } finally {
      state.status = 'complete';
      state.completedAt = Date.now();
    }
  })();
  return 'started';
})();