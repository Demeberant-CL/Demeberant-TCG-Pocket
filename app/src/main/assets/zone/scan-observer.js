(() => {
  'use strict';
  const route = location.pathname;
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com','pokemon-zone.com'].includes(location.hostname)
      || !/^\/players\/[0-9]{10,20}\/cards\/$/.test(route) || location.search || location.hash) return;
  const selector = '.player-expansion-collection-card';
  const root = document.querySelector('main') || document.body;
  if (!root) return;
  if (window.__pocketZoneScan?.route === route && window.__pocketZoneScan.root === root) return;
  window.__pocketZoneScan?.observer.disconnect();
  const state = {route, root, loadedRevision:0, dirty: new Set(root.querySelectorAll(selector)), revision: 0, waiting: null};
  const mark = element => {
    if (!element || element.nodeType !== 1) return;
    const card = element.closest(selector);
    if (card) { state.dirty.add(card); state.revision++; state.loadedRevision++; }
    for (const child of element.querySelectorAll(selector)) { state.dirty.add(child); state.revision++; state.loadedRevision++; }
  };
  state.collect = records => {
    for (const record of records) {
      const target = record.target.nodeType === 1 ? record.target : record.target.parentElement;
      // Text/attributes only dirty the containing card; added subtrees are scanned once.
      const card = target?.closest(selector);
      if (card) { state.dirty.add(card); state.revision++; }
      if (record.type === 'childList') for (const added of record.addedNodes) mark(added);
    }
  };
  state.observer = new MutationObserver(state.collect);
  state.observer.observe(root, {subtree:true, childList:true, characterData:true, attributes:true,
    attributeFilter:['href','class','hidden','aria-hidden','style']});
  window.__pocketZoneScan = state;
})()
