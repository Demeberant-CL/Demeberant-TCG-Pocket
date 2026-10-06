const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require('playwright');
const script = fs.readFileSync(__dirname + '/src/main/assets/extract.js', 'utf8');
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
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
