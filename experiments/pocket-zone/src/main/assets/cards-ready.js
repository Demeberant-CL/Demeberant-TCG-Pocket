(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com','pokemon-zone.com'].includes(location.hostname)
      || !/^\/players\/\d{10,20}\/cards\/$/.test(location.pathname) || location.search || location.hash) return 'blocked';
  const root = document.querySelector('main') || document.body;
  return root?.querySelector('.player-expansion-collection-card') ? 'ready' : 'waiting';
})();
