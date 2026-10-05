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
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: html }));
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
