const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');
const script = fs.readFileSync(__dirname + '/src/main/assets/extract.js', 'utf8');
let browser;
before(async () => { browser = await chromium.launch({ headless: true, ...(process.env.POCKET_ZONE_TEST_BROWSER ? { executablePath: process.env.POCKET_ZONE_TEST_BROWSER } : {}) }); });
after(async () => { if (browser) await browser.close(); });
async function extract(html, url = 'https://www.pokemon-zone.com/players/3778164033299021/') {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html; charset=utf-8', body: html }));
  await page.goto(url);
  try { return JSON.parse(await page.evaluate(script)); } finally { await page.close(); }
}
test('reads public visible data and preserves the long friend ID', async () => {
  const data = await extract('<title>Perfil</title><main><h1>Demeberant</h1><dl><dt>Nivel</dt><dd>50</dd></dl></main>');
  assert.equal(data.friendId, '3778164033299021');
  assert.equal(data.visibleHeading, 'Demeberant');
  assert.deepEqual(data.visibleFields, [{label:'Nivel', value:'50'}]);
  assert.equal(data.collectionComplete, false);
});
test('ignores hidden content, forms and embedded JSON', async () => {
  const data = await extract('<main><h1>Perfil</h1><form>contraseña privada<input value="secret"></form><div hidden>oculto</div><div style="visibility:hidden">invisible</div><script type="application/json">{"token":"secret"}</script><p>Texto público</p></main>');
  assert.equal(data.visibleSummary, 'Perfil\nTexto público');
  assert.equal(JSON.stringify(data).includes('secret'), false);
});
test('fails closed while loading', async () => {
  assert.equal((await extract('<main>Loading...</main>')).error, 'loading');
});
test('detects challenge page without bypassing it', async () => {
  assert.equal((await extract('<title>Just a moment</title><main>Wait</main>')).error, 'blocked');
});
test('rejects an unrelated host and query tokens', async () => {
  assert.equal((await extract('<main>Perfil</main>', 'https://evil.test/players/3778164033299021/')).error, 'origin');
  assert.equal((await extract('<main>Perfil</main>', 'https://www.pokemon-zone.com/players/3778164033299021/?token=secret')).error, 'origin');
});
test('collects only already-loaded cards without treating absent counts as zero', async () => {
  const card = (count, href='/cards/a1-1/') => `<div class="player-expansion-collection-card"><div class="player-expansion-collection-card__card"><a href="${href}">Carta</a></div><div class="player-expansion-collection-card__count">${count}</div><div class="player-expansion-collection-card__name-text">Bulbasaur</div></div>`;
  const data = await extract('<main>' + card('2') + card('2') + card('') + card('3','https://evil.test/cards/a1-2/') + '</main>');
  assert.equal(data.visibleCards.length, 1);
  assert.equal(data.visibleCards[0].quantity, 2);
  assert.equal(data.collectionComplete, false);
});
test('bounds the fallback summary', async () => {
  const data = await extract('<main>' + '<p>abc'.repeat(5000) + '</main>');
  assert.ok(data.visibleSummary.length <= 6000);
});
test('process probe emits only fixed click categories, never form content', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({contentType:'text/html; charset=utf-8', body:'<button id="sync">Sync</button><button id="login">Sign In</button><input type="password" value="private-password"><button id="other">private-token</button>'}));
  await page.goto('https://www.pokemon-zone.com/settings/');
  const probe = fs.readFileSync(__dirname + '/src/main/assets/process-probe.js','utf8');
  assert.equal(await page.evaluate(probe), '[]');
  await page.click('#sync'); await page.click('#login'); await page.click('#other');
  assert.deepEqual(JSON.parse(await page.evaluate(probe)), ['sync_control','account_control']);
  await page.evaluate('window.__pocketZoneProbeActive=false');
  await page.click('#sync');
  assert.equal(await page.evaluate(probe), '[]');
  await page.close();
});

test('reads more than 200 loaded cards without duplicates', async () => {
  const card = n => `<div class="player-expansion-collection-card"><div class="player-expansion-collection-card__card"><a href="/cards/a1/${n}/card/">Card</a></div><div class="player-expansion-collection-card__count">2</div><div class="player-expansion-collection-card__name-text">Card ${n}</div></div>`;
  const data = await extract('<main>' + Array.from({length:250},(_,i)=>card(i+1)).join('') + card(1) + '</main>');
  assert.equal(data.visibleCards.length,250);
  assert.equal(data.collectionComplete,false);
});

test('advances through a Load more boundary and ignores unrelated controls', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({contentType:'text/html', body:`<header><button onclick="window.wrong=true">Load more</button></header><main><div class="player-expansion-collection-card">A2b</div><form><button onclick="window.wrong=true">Load more</button></form><button hidden onclick="window.wrong=true">Load more</button><button disabled onclick="window.wrong=true">Load more</button><button id="load" onclick="document.querySelector('main').insertAdjacentHTML('beforeend','<div class=player-expansion-collection-card>A3</div>');this.remove()">Load more</button></main>`}));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/cards/');
  const advance = fs.readFileSync(__dirname + '/src/main/assets/advance-cards.js', 'utf8');
  assert.equal(await page.evaluate(advance), 'load');
  assert.equal(await page.locator('.player-expansion-collection-card').count(), 2);
  assert.equal(await page.evaluate('window.wrong'), undefined);
  assert.equal(await page.evaluate(advance), 'scroll');
  await page.goto('https://www.pokemon-zone.com/settings/');
  assert.equal(await page.evaluate(advance), 'blocked');
  await page.close();
});

test('fast scan keeps the same card quantities and deduplication without repeating summary work', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({contentType:'text/html', body:`<main><h1>Profile</h1><p>Public summary</p><div class="player-expansion-collection-card"><div class="player-expansion-collection-card__card"><a href="/cards/a1/1/bulbasaur/">Card</a></div><div class="player-expansion-collection-card__count">2</div><div class="player-expansion-collection-card__name-text">Bulbasaur</div></div></main>`}));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/cards/');
  const full = JSON.parse(await page.evaluate(script));
  const fastScript = script.replace('})();', '})(true);');
  const fast = JSON.parse(await page.evaluate(fastScript));
  assert.deepEqual(fast.visibleCards, full.visibleCards);
  assert.equal(fast.visibleSummary, '');
  assert.equal(fast.collectionComplete, false);
  await page.locator('.player-expansion-collection-card__count').evaluate(el => el.textContent = '5');
  assert.equal(JSON.parse(await page.evaluate(fastScript)).visibleCards[0].quantity, 5);
  await page.close();
});

test('incremental scan processes new and changed rows only, keeping quantities and ignoring ad changes', async () => {
  const page = await browser.newPage();
  const card = n => `<div class="player-expansion-collection-card" id="card${n}"><div class="player-expansion-collection-card__card"><a href="/cards/a1/${n}/card/">Card</a></div><div class="player-expansion-collection-card__count">2</div><div class="player-expansion-collection-card__name-text">Card ${n}</div></div>`;
  await page.route('**/*', route => route.fulfill({contentType:'text/html',body:'<main>'+Array.from({length:1320},(_,i)=>card(i+1)).join('')+'<div id="ad">Ad</div></main>'}));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/cards/');
  const observer = fs.readFileSync(__dirname+'/src/main/assets/scan-observer.js','utf8');
  const scan = observer+';'+script.replace('})();','})(true);');
  const read = async () => JSON.parse(await page.evaluate(scan));
  assert.equal((await read()).visibleCards.length,1320);
  assert.equal((await read()).visibleCards.length,0);
  await page.locator('#ad').evaluate(el=>el.textContent='New ad');
  assert.equal((await read()).visibleCards.length,0);
  await page.locator('#card3 .player-expansion-collection-card__count').evaluate(el=>el.textContent='7');
  const changed=(await read()).visibleCards;
  assert.equal(changed.length,1); assert.equal(changed[0].quantity,7);
  await page.locator('main').evaluate((el,html)=>el.insertAdjacentHTML('beforeend',html),card(1321));
  const added=(await read()).visibleCards;
  assert.equal(added.length,1);assert.equal(added[0].cardPath,'/cards/a1/1321/card/');
  await page.evaluate('window.__pocketZoneScan.observer.disconnect();delete window.__pocketZoneScan');
  assert.equal((await read()).visibleCards.length,1321);
  await page.close();
});
test('Load more waits for an actual added card before allowing another click', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route=>route.fulfill({contentType:'text/html',body:'<main><div class="player-expansion-collection-card">First</div><button onclick="window.clicks=(window.clicks||0)+1">Load more</button><div id="ad">Ad</div></main>'}));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/cards/');
  await page.evaluate(fs.readFileSync(__dirname+'/src/main/assets/scan-observer.js','utf8'));
  const advance=fs.readFileSync(__dirname+'/src/main/assets/advance-cards.js','utf8');
  assert.equal(await page.evaluate(advance),'load');
  assert.equal(await page.evaluate(advance),'waiting');
  await page.locator('#ad').evaluate(el=>el.textContent='Changed');
  assert.equal(await page.evaluate(advance),'waiting');
  assert.equal(await page.evaluate('window.clicks'),1);
  await page.locator('main').evaluate(el=>el.insertAdjacentHTML('beforeend','<div class="player-expansion-collection-card">Next</div>'));
  assert.equal(await page.evaluate(advance),'load');
  assert.equal(await page.evaluate('window.clicks'),2);
  await page.evaluate('window.__pocketZoneScan.waiting.time=Date.now()-21000');
  assert.equal(await page.evaluate(advance),'load-timeout');
  await page.close();
});

const syncProbe = fs.readFileSync(__dirname + '/src/main/assets/sync-probe.js', 'utf8');
test('sync probe preserves fetch responses and exports only fixed state categories', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main>Profile</main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/');
  await page.route('**/api/players/sync/status/*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ status: 'completed', token: 'private-secret', playerId: 'private-id' }) }));
  await page.evaluate(syncProbe);
  await page.evaluate(syncProbe); // installation must be idempotent
  const original = await page.evaluate(async () => (await fetch('/api/players/sync/status/private-job')).json());
  assert.equal(original.token, 'private-secret');
  await page.waitForFunction(() => window.__pocketZoneSyncProbe !== undefined);
  const events = [];
  for (let i = 0; i < 40 && !events.includes('sync_status_status_completed'); i++) {
    events.push(...await page.evaluate(() => window.__pocketZoneSyncProbe.drain()));
    if (!events.includes('sync_status_status_completed')) await page.waitForTimeout(10);
  }
  assert.deepEqual(events, ['sync_transport_fetch', 'sync_status_shape_object', 'sync_status_status_completed']);
  assert.equal(JSON.stringify(events).includes('private'), false);
  await page.close();
});
test('sync probe handles XHR and excludes unrelated or unknown response data', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main>Profile</main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/');
  await page.route('**/api/players/sync/status/*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ state: 'private-secret', token: 'private-secret' }) }));
  await page.evaluate(syncProbe);
  await page.evaluate(() => new Promise(resolve => { const xhr = new XMLHttpRequest(); xhr.open('GET', '/api/players/sync/status/private-job'); xhr.responseType = 'json'; xhr.addEventListener('loadend', resolve); xhr.send(); }));
  assert.deepEqual(await page.evaluate(() => window.__pocketZoneSyncProbe.drain()), ['sync_transport_xhr', 'sync_status_shape_object', 'sync_status_state_string']);
  await page.evaluate(async () => { await fetch('/api/players/unrelated'); });
  assert.deepEqual(await page.evaluate(() => window.__pocketZoneSyncProbe.drain()), []);
  await page.close();
});

test('sync probe recognizes nested task states and camel case flags without retaining secrets', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main>Profile</main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/');
  await page.route('**/api/players/sync/status/*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ data: { task: { taskStatus: 'SUCCESS', isReady: true, token: 'secret' } }, credential: 'secret' }) }));
  await page.evaluate(syncProbe);
  await page.evaluate(() => new Promise(resolve => { const xhr = new XMLHttpRequest(); xhr.open('GET', '/api/players/sync/status/job'); xhr.addEventListener('loadend', resolve); xhr.send(); }));
  assert.deepEqual(await page.evaluate(() => window.__pocketZoneSyncProbe.drain()), ['sync_transport_xhr', 'sync_status_shape_object', 'sync_status_data_task_task_status_success', 'sync_status_data_task_is_ready_true']);
  await page.close();
});
const syncControl = fs.readFileSync(__dirname + '/src/main/assets/sync-control.js', 'utf8');
const cardsReady = fs.readFileSync(__dirname + '/src/main/assets/cards-ready.js', 'utf8');
test('automatic Sync clicks once and requires success AND ready from the new operation', async () => {
  const page = await browser.newPage();
  let response = { data: { status: 'SUCCESS', ready: true } };
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main><button id="sync">Sync</button></main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/');
  await page.route('**/api/players/sync', route => route.fulfill({ contentType: 'application/json', body: '{"job":"private"}' }));
  await page.route('**/api/players/sync/status/*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(response) }));
  await page.evaluate(syncProbe);
  const poll = async () => {
    await page.evaluate(() => new Promise(resolve => { const x = new XMLHttpRequest(); x.open('GET', '/api/players/sync/status/job'); x.addEventListener('loadend', resolve); x.send(); }));
  };
  await poll();
  assert.equal(await page.evaluate(() => window.__pocketZoneSyncProbe.state()), 'idle');
  await page.evaluate(() => { window.clicks = 0; document.querySelector('#sync').onclick = () => { window.clicks++; fetch('/api/players/sync', { method: 'POST' }); }; });
  assert.equal(await page.evaluate(syncControl), 'waiting');
  response = { data: { status: 'SUCCESS', ready: false } }; await poll();
  assert.equal(await page.evaluate(syncControl), 'waiting');
  response = { data: { status: 'PENDING', ready: true } }; await poll();
  assert.equal(await page.evaluate(syncControl), 'waiting');
  response = { data: { status: 'SUCCESS', ready: true } }; await poll();
  assert.equal(await page.evaluate(syncControl), 'success');
  assert.equal(await page.evaluate(() => window.clicks), 1);
  await page.close();
});
test('automatic Sync rejects failure and ignores a response belonging to an older start', async () => {
  const page = await browser.newPage();
  let oldRoute; let first = true;
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main>Profile</main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/');
  await page.route('**/api/players/sync', route => route.fulfill({ contentType: 'application/json', body: '{}' }));
  await page.route('**/api/players/sync/status/*', route => {
    if (first) { first = false; oldRoute = route; return; }
    return route.fulfill({ contentType: 'application/json', body: '{"data":{"status":"FAILURE","ready":true}}' });
  });
  await page.evaluate(syncProbe);
  await page.evaluate(async () => { await fetch('/api/players/sync', { method: 'POST' }); window.oldPoll = fetch('/api/players/sync/status/old'); });
  for (let i = 0; !oldRoute && i < 50; i++) await page.waitForTimeout(10);
  assert.ok(oldRoute);
  await page.evaluate(async () => { await fetch('/api/players/sync', { method: 'POST' }); });
  await oldRoute.fulfill({ contentType: 'application/json', body: '{"data":{"status":"SUCCESS","ready":true}}' });
  await page.evaluate(async () => { await window.oldPoll; });
  assert.equal(await page.evaluate(() => window.__pocketZoneSyncProbe.state()), 'waiting');
  await page.evaluate(() => new Promise(resolve => { const x = new XMLHttpRequest(); x.open('GET', '/api/players/sync/status/new'); x.addEventListener('loadend', resolve); x.send(); }));
  assert.equal(await page.evaluate(() => window.__pocketZoneSyncProbe.state()), 'failed');
  await page.close();
});
test('post-sync readiness waits for actual card rows on the collection route', async () => {
  const page = await browser.newPage();
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<main>Loading...</main>' }));
  await page.goto('https://www.pokemon-zone.com/players/3778164033299021/cards/');
  assert.equal(await page.evaluate(cardsReady), 'waiting');
  await page.evaluate(() => { document.querySelector('main').innerHTML = '<div class="player-expansion-collection-card"></div>'; });
  assert.equal(await page.evaluate(cardsReady), 'ready');
  assert.equal(await page.evaluate(syncControl), 'blocked');
  await page.close();
});
