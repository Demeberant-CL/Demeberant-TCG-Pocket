(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com', 'pokemon-zone.com'].includes(location.hostname)) return '[]';
  if (!window.__pocketZoneSyncProbe) {
    const events = [], tracked = new WeakMap();
    const emit = event => {
      if (window.__pocketZoneProbeActive === false) return;
      if (events.length >= 40) events.shift();
      events.push(event);
    };
    const purpose = input => {
      try {
        const url = new URL(typeof input === 'string' ? input : input.url, location.href);
        if (url.origin !== location.origin) return null;
        if (/^\/api\/players\/sync\/?$/.test(url.pathname)) return 'start';
        if (/^\/api\/players\/sync\/status\/[^/]{1,200}\/?$/.test(url.pathname)) return 'status';
      } catch (_) { }
      return null;
    };
    // Only fixed, allowlisted categories leave the page. No raw values, IDs or response bodies.
    const inspect = (kind, data) => {
      if (!data || typeof data !== 'object' || Array.isArray(data)) { emit('sync_' + kind + '_unknown'); return; }
      let found = false;
      for (const key of ['status', 'state', 'done', 'completed', 'success', 'error']) {
        const value = data[key];
        let safe = null;
        if (typeof value === 'boolean') safe = String(value);
        else if (typeof value === 'string') {
          const normalized = value.toLowerCase().trim();
          if (['pending', 'queued', 'running', 'processing', 'in_progress', 'success', 'completed', 'complete', 'done', 'failed', 'failure', 'error'].includes(normalized)) safe = normalized;
        }
        if (safe !== null) { found = true; emit('sync_' + kind + '_' + key + '_' + safe); }
      }
      if (!found) emit('sync_' + kind + '_unknown');
    };
    const parse = (kind, text) => {
      if (typeof text !== 'string' || text.length > 16384) { emit('sync_' + kind + '_unknown'); return; }
      try { inspect(kind, JSON.parse(text)); } catch (_) { emit('sync_' + kind + '_unknown'); }
    };
    const consume = async (kind, response) => {
      if (!response.ok) { emit('sync_' + kind + '_http_error'); return; }
      const copy = response.clone();
      const reader = copy.body?.getReader();
      if (!reader) { emit('sync_' + kind + '_unknown'); return; }
      const decoder = new TextDecoder(); let text = '', size = 0;
      try {
        while (true) {
          const chunk = await reader.read();
          if (chunk.done) break;
          size += chunk.value.byteLength;
          if (size > 16384) { reader.cancel().catch(() => {}); emit('sync_' + kind + '_unknown'); return; }
          text += decoder.decode(chunk.value, { stream: true });
        }
        parse(kind, text + decoder.decode());
      } finally { reader.releaseLock(); }
    };
    if (typeof window.fetch === 'function') {
      const original = window.fetch;
      window.fetch = function (...args) {
        const kind = purpose(args[0]), result = original.apply(this, args);
        if (kind) { emit('sync_transport_fetch'); result.then(response => consume(kind, response).catch(() => emit('sync_' + kind + '_unknown')), () => emit('sync_' + kind + '_http_error')); }
        return result;
      };
    }
    const open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (...args) {
      const result = open.apply(this, args);
      tracked.set(this, purpose(args[1])); return result;
    };
    XMLHttpRequest.prototype.send = function (...args) {
      const kind = tracked.get(this);
      if (kind) {
        emit('sync_transport_xhr');
        this.addEventListener('loadend', () => {
          if (this.status < 200 || this.status >= 300) { emit('sync_' + kind + '_http_error'); return; }
          try {
            if (this.responseType === 'json') inspect(kind, this.response);
            else if (this.responseType === '' || this.responseType === 'text') parse(kind, this.responseText);
            else emit('sync_' + kind + '_unknown');
          } catch (_) { emit('sync_' + kind + '_unknown'); }
        }, { once: true });
      }
      return send.apply(this, args);
    };
    window.__pocketZoneSyncProbe = { drain: () => events.splice(0, 40) };
  }
  return null;
})();
