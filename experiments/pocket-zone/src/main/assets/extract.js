(() => {
  'use strict';
  const allowedHost = ['www.pokemon-zone.com', 'pokemon-zone.com'].includes(location.hostname);
  const match = /^\/players\/([0-9]{10,20})\/(?:cards\/)?$/.exec(location.pathname);
  if (!allowedHost || location.protocol !== 'https:' || location.search || location.hash || !match) {
    return JSON.stringify({error: 'origin'});
  }
  const root = document.querySelector('main') || document.body;
  const excluded = 'script,style,noscript,nav,header,footer,form,input,textarea,[contenteditable],[hidden],[aria-hidden="true"],[role="navigation"]';
  const visible = el => !!el && !el.closest(excluded) && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  const text = (el, max) => visible(el) ? (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, max) : '';
  if (!root || /(?:loading|cargando)\s*(?:\.\.\.|…)/i.test(root.innerText || '')) {
    return JSON.stringify({error: 'loading'});
  }
  if (/just a moment|checking your browser|verify you are human|attention required/i.test(document.title)) {
    return JSON.stringify({error: 'blocked'});
  }
  const fields = [];
  for (const dt of root.querySelectorAll('dl dt')) {
    const dd = dt.nextElementSibling;
    if (dd?.tagName === 'DD') {
      const label = text(dt, 60), value = text(dd, 120);
      if (label && value && fields.length < 30) fields.push({label, value});
    }
  }
  // Solo lee filas ya cargadas, sin pulsar botones, ni sincronizar con Nintendo.
  const cards = [];
  const seen = new Set();
  for (const el of root.querySelectorAll('.player-expansion-collection-card')) {
    if (cards.length >= 5000) break;
    const anchor = el.querySelector('.player-expansion-collection-card__card a[href]');
    const count = text(el.querySelector('.player-expansion-collection-card__count'), 20);
    if (!visible(el) || !anchor || !/^\d+$/.test(count)) continue;
    let url;
    try { url = new URL(anchor.href, location.href); } catch { continue; }
    if (!['www.pokemon-zone.com', 'pokemon-zone.com'].includes(url.hostname) || url.protocol !== 'https:' || !/^\/cards\/[A-Za-z0-9/_-]+\/?$/.test(url.pathname) || seen.has(url.pathname)) continue;
    seen.add(url.pathname);
    cards.push({cardPath: url.pathname, quantity: Number(count), name: text(el.querySelector('.player-expansion-collection-card__name-text'), 120)});
  }
  const lines = [];
  let total = 0;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node && total < 6000; node = walker.nextNode()) {
    if (!visible(node.parentElement)) continue;
    const line = (node.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 200);
    if (!line || line === lines[lines.length - 1]) continue;
    lines.push(line.slice(0, 6000 - total));
    total += line.length + 1;
  }
  return JSON.stringify({
    schemaVersion: 1,
    source: 'pokemon-zone',
    friendId: match[1],
    url: 'https://www.pokemon-zone.com' + location.pathname,
    pageTitle: (document.title || '').slice(0, 180),
    visibleHeading: text(root.querySelector('h1'), 180),
    visibleFields: fields,
    visibleSummary: lines.join('\n').slice(0, 6000),
    visibleCards: cards,
    collectionComplete: false
  });
})();
