(() => {
  if (location.origin !== 'https://www.pokemon-zone.com' || location.search || location.hash) return '{}';
  const route = /^\/players\/([0-9]{16})\/$/.exec(location.pathname);
  if (!route) return '{}';
  const root = document.querySelector('main');
  if (!root) return '{}';
  const excluded = 'script,style,noscript,nav,header,footer,form,input,textarea,[contenteditable],[hidden],[aria-hidden="true"]';
  const visible = el => el && !el.closest(excluded) && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  const text = el => visible(el) ? (el.innerText || '').trim().replace(/\s+/g, ' ') : '';
  if (/just a moment|checking your browser|verify you are human/i.test(document.title) || /(?:loading|cargando)\s*(?:\.\.\.|…)/i.test(root.innerText || '')) return '{}';
  const heading = root.querySelector('[data-player-name], .player-profile__name, .player-header__name, .player-profile-name, h1');
  let nickname = text(heading);
  if (!nickname || nickname.length > 60 || /^(profile|player|cards|collection|loading|perfil|cartas|colección|cargando)[.!…\s]*$/i.test(nickname)) nickname = '';
  const levelNode = root.querySelector('[data-player-level], .player-profile__level, .player-header__level, .player-level');
  let labeledLevel = '';
  for (const dt of root.querySelectorAll('dl dt')) {
    if (/^(level|nivel|lv\.?|lvl\.?)$/i.test(text(dt)) && dt.nextElementSibling?.tagName === 'DD') labeledLevel = text(dt.nextElementSibling);
  }
  const levelText = text(levelNode);
  const match = /(?:\bLevel|\bNivel|\bLv\.?|\bLvl\.?)\s*[:.-]?\s*(\d{1,3})\b/i.exec(levelText);
  const rawNumber = labeledLevel || text(levelNode);
  const numeric = /^\d{1,3}$/.test(rawNumber) ? Number(rawNumber) : 0;
  const level = match ? Number(match[1]) : numeric;
  return JSON.stringify({friendId: route[1], nickname, level: level >= 1 && level <= 999 ? level : null});
})()
