(() => {
  'use strict';
  if (location.protocol !== 'https:' || !['www.pokemon-zone.com', 'pokemon-zone.com'].includes(location.hostname)) return '[]';
  if (!window.__pocketZoneSyncProbe) {
    const events = [], tracked = new WeakMap();
    const flow = { run: 0, phase: "idle" };
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
    const fields = new Set(['status', 'state', 'task_status', 'task_state', 'ready', 'is_ready', 'done', 'is_done', 'finished', 'is_finished', 'completed', 'is_completed', 'complete', 'success', 'successful', 'is_successful', 'failed', 'is_failed', 'error', 'has_error', 'progress', 'percent', 'percentage']);
    const containers = new Set(['data', 'result', 'task', 'job', 'response', 'value']);
    const phases = new Set(['pending', 'queued', 'received', 'started', 'running', 'processing', 'in_progress', 'progress', 'retry', 'revoked', 'success', 'successful', 'succeeded', 'completed', 'complete', 'finished', 'done', 'ok', 'failed', 'failure', 'error']);
    const normalizeKey = key => key.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
    const safeValue = value => {
      if (typeof value === 'boolean') return String(value);
      if (typeof value === 'string') {
        const normalized = value.toLowerCase().trim();
        return phases.has(normalized) ? normalized : 'string';
      }
      if (value === null) return 'null';
      if (Array.isArray(value)) return 'array';
      return ['number', 'object'].includes(typeof value) ? typeof value : 'unknown';
    };
    const inspect = (kind, data) => {
      // Verified site contract: readiness alone is not success.
      if (kind === 'status' && flow.phase === 'waiting' && data?.data?.ready === true) {
        const status = typeof data.data.status === 'string' ? data.data.status.toLowerCase().trim() : '';
        if (status === 'success') flow.phase = 'success';
        else if (['failure', 'failed', 'error', 'revoked'].includes(status)) flow.phase = 'failed';
      }
      const signals = new Set(); let recognized = false;
      const note = suffix => { if (signals.size < 20) signals.add('sync_' + kind + '_' + suffix); };
      note('shape_' + (data === null ? 'null' : Array.isArray(data) ? 'array' : typeof data));
      const visit = (node, path, depth) => {
        if (depth > 3) return;
        if (node === null || typeof node !== 'object' || Array.isArray(node)) {
          note(path + 'value_' + safeValue(node)); recognized = true; return;
        }
        for (const key of Object.keys(node).slice(0, 64)) {
          const name = normalizeKey(key);
          if (fields.has(name)) { note(path + name + '_' + safeValue(node[key])); recognized = true; }
          else if (containers.has(name)) visit(node[key], path + name + '_', depth + 1);
        }
      };
      visit(data, '', 0);
      if (!recognized) note('unknown');
      signals.forEach(emit);
    };
    const parse = (kind, text) => {
      if (typeof text !== 'string' || text.length > 16384) { emit('sync_' + kind + '_oversize'); return; }
      try { inspect(kind, JSON.parse(text)); } catch (_) { emit('sync_' + kind + '_parse_error'); }
    };
    const consume = async (kind, response, lease) => {
      if (!response.ok) { if (lease === flow.run && flow.phase === 'waiting') flow.phase = 'failed'; emit('sync_' + kind + '_http_error'); return; }
      const copy = response.clone();
      const reader = copy.body?.getReader();
      if (!reader) { emit('sync_' + kind + '_unknown'); return; }
      const decoder = new TextDecoder(); let text = '', size = 0;
      try {
        while (true) {
          const chunk = await reader.read();
          if (chunk.done) break;
          size += chunk.value.byteLength;
          if (size > 16384) { reader.cancel().catch(() => {}); emit('sync_' + kind + '_oversize'); return; }
          text += decoder.decode(chunk.value, { stream: true });
        }
        if (lease === flow.run) parse(kind, text + decoder.decode());
      } finally { reader.releaseLock(); }
    };
    if (typeof window.fetch === 'function') {
      const original = window.fetch;
      window.fetch = function (...args) {
        const kind = purpose(args[0]), result = original.apply(this, args);
        if (kind) {
          if (kind === 'start') { flow.run++; flow.phase = 'waiting'; }
          const lease = flow.run;
          emit('sync_transport_fetch');
          result.then(response => consume(kind, response, lease).catch(() => emit('sync_' + kind + '_unknown')), () => {
            if (lease === flow.run && flow.phase === 'waiting') flow.phase = 'failed';
            emit('sync_' + kind + '_http_error');
          });
        }
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
        if (kind === 'start') { flow.run++; flow.phase = 'waiting'; }
        const lease = flow.run;
        emit('sync_transport_xhr');
        this.addEventListener('loadend', () => {
          if (lease !== flow.run) return;
          if (this.status < 200 || this.status >= 300) { if (flow.phase === 'waiting') flow.phase = 'failed'; emit('sync_' + kind + '_http_error'); return; }
          try {
            if (this.responseType === 'json') inspect(kind, this.response);
            else if (this.responseType === '' || this.responseType === 'text') parse(kind, this.responseText);
            else emit('sync_' + kind + '_unknown');
          } catch (_) { emit('sync_' + kind + '_unknown'); }
        }, { once: true });
      }
      return send.apply(this, args);
    };
    window.__pocketZoneSyncProbe = { drain: () => events.splice(0, 40), prepare: () => { flow.run++; flow.phase = "idle"; }, state: () => flow.phase };
  }
  return null;
})();
