(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com','pokemon-zone.com'].includes(location.hostname)) return '[]';
  if (typeof window.__pocketZoneProbeActive === 'undefined') window.__pocketZoneProbeActive = true;
  // Only fixed event categories leave this page; no field values or arbitrary button labels.
  if (!window.__pocketZoneProbe) {
    const events = [];
    const classify = element => {
      const button = element?.closest('button,a,[role="button"],input[type="submit"]');
      if (!button) return null;
      const label = (button.innerText || button.value || '').trim().toLowerCase();
      if (/^(sync|sync your data|sync data|synchronize|sincronizar|refresh|update collection)$/.test(label)) return 'sync_control';
      if (/^(sign in|log in|login|my account|mi cuenta|iniciar sesión)$/.test(label)) return 'account_control';
      if (/^(load more|show more|cargar más|ver más)$/.test(label)) return 'load_control';
      return null;
    };
    document.addEventListener('click', event => {
      if (!window.__pocketZoneProbeActive) return;
      const kind = classify(event.target);
      if (kind) { if (events.length >= 40) events.shift(); events.push(kind); }
    }, true);
    window.__pocketZoneProbe = { drain: () => events.splice(0, 40) };
  }
  return JSON.stringify(window.__pocketZoneProbe.drain());
})();
