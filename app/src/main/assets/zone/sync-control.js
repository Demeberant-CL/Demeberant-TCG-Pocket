(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com', 'pokemon-zone.com'].includes(location.hostname)
      || !/^\/players\/\d{10,20}\/$/.test(location.pathname) || location.search || location.hash) return 'blocked';
  const probe = window.__pocketZoneSyncProbe;
  if (!probe) return 'waiting';
  if (!window.__pocketZoneAutoSync) {
    const root = document.querySelector('main') || document.body;
    const button = [...root.querySelectorAll('button,a,[role="button"]')].find(element => {
      if (!/^(sync|sincronizar)$/i.test((element.innerText || '').trim())) return false;
      if (element.closest('nav,header,footer,form,[hidden],[aria-hidden="true"]') || element.disabled || element.getAttribute('aria-disabled') === 'true') return false;
      const style = getComputedStyle(element);
      return element.getClientRects().length > 0 && style.visibility !== 'hidden' && style.display !== 'none';
    });
    if (!button) return 'waiting';
    probe.prepare();
    window.__pocketZoneAutoSync = true;
    button.click();
  }
  const state = probe.state();
  return ['success', 'failed'].includes(state) ? state : 'waiting';
})();
