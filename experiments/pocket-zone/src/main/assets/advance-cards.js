(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com', 'pokemon-zone.com'].includes(location.hostname)
      || !/^\/players\/[0-9]{10,20}\/cards\/$/.test(location.pathname) || location.search || location.hash) return 'blocked';
  const root = document.querySelector('main') || document.body;
  if (!root || !root.querySelector('.player-expansion-collection-card')) return 'waiting';
  const excluded = 'nav,header,footer,form,[hidden],[aria-hidden="true"],[role="navigation"]';
  const load = [...root.querySelectorAll('button')].find(button => {
    const label = (button.innerText || '').replace(/\s+/g, ' ').trim().toLowerCase();
    return /^(load more|show more|cargar más|ver más)(?: cards| cartas)?$/.test(label)
      && !button.closest(excluded) && !button.disabled && button.getAttribute('aria-disabled') !== 'true'
      && button.getClientRects().length > 0 && getComputedStyle(button).visibility !== 'hidden';
  });
  if (load) { load.click(); return 'load'; }
  window.scrollTo(0, Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
  return 'scroll';
})()
