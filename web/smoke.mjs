import {chromium} from 'playwright';
import {spawn} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';

const server=spawn('npm',['run','preview','--','--host','127.0.0.1'],{stdio:'inherit'});
let browser, page;
async function assertTheme(theme){await page.waitForFunction(value=>document.body.dataset.theme===value,theme,{timeout:5000});assert.equal(await page.locator('body').getAttribute('data-theme'),theme);}
try {
  for(let attempt=0;attempt<60;attempt++){
    try{if((await fetch('http://127.0.0.1:3000')).ok)break;}catch{}
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  browser=await chromium.launch({headless:true});
  const context=await browser.newContext({viewport:{width:412,height:915}});
  page=await context.newPage();const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://**/*',route=>route.abort());
  await page.goto('http://127.0.0.1:3000');
  await page.getByRole('heading',{name:'Mi colección',exact:true}).waitFor();
  await page.emulateMedia({colorScheme:'dark'});
  await assertTheme('dark');
  await page.emulateMedia({colorScheme:'light'});
  await assertTheme('light');
  assert.ok((await page.locator('main').innerText()).includes('0 de 4317 cartas'));
  await page.locator('#collection-search').fill('A1-001');
  await page.getByRole('button',{name:'Bulbasaur A1-001 0 copias',exact:true}).click();
  await page.getByLabel('Copias en mi colección').fill('3');
  await page.getByRole('button',{name:'Guardar cantidad',exact:true}).click();
  await page.getByRole('button',{name:'Bulbasaur A1-001 3 copias',exact:true}).click();
  await page.getByRole('button',{name:'Añadir a Deseos',exact:true}).click();
  await page.getByRole('button',{name:'Cerrar',exact:true}).click();
  for(const name of ['Tengo','Deseos','Ver repetidas']){
    await page.getByRole('button',{name,exact:true}).click();
    assert.equal(await page.getByRole('button',{name:'Bulbasaur A1-001 3 copias',exact:true}).count(),1);
  }
  await page.getByRole('button',{name:'Faltan',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Bulbasaur A1-001 3 copias',exact:true}).count(),0);
  assert.ok((await page.locator('main').innerText()).includes('No se encontraron cartas'));
  await page.getByRole('button',{name:'Todas',exact:true}).click();
  const [download]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'Exportar CSV',exact:true}).click()]);
  const stream=await download.createReadStream();let csv='';for await(const chunk of stream)csv+=chunk.toString();
  assert.ok(csv.includes('"A1","1","Bulbasaur","♦","3","sí","sí"'));

  await page.getByRole('button',{name:'Mazos',exact:true}).click();
  await page.getByLabel('Nombre del mazo', {exact:true}).fill('Prueba guardada');
  await page.getByRole('button',{name:'Añadir',exact:true}).first().click();
  await page.getByRole('button',{name:'Planta',exact:true}).click();
  await page.getByRole('button',{name:'Guardar',exact:true}).click();
  await page.reload();
  await page.getByRole('button',{name:'Mazos',exact:true}).click();
  assert.ok((await page.locator('main').innerText()).includes('Prueba guardada'));
  await page.getByRole('button',{name:'Editar',exact:true}).click();
  assert.equal(await page.getByLabel('Nombre del mazo',{exact:true}).inputValue(),'Prueba guardada');
  assert.equal(await page.getByRole('button',{name:'Planta',exact:true}).getAttribute('aria-pressed'),'true');

  await page.getByRole('button',{name:'Abrir ajustes',exact:true}).click();
  const [backupDownload]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'Guardar respaldo completo',exact:true}).click()]);
  const backupStream=await backupDownload.createReadStream();let backupText='';for await(const chunk of backupStream)backupText+=chunk.toString();
  const backup=JSON.parse(backupText);assert.equal(backup.inventory[0].quantity,3);assert.equal(backup.inventory[0].wishlist,true);assert.equal(backup.decks.length,1);
  await page.getByLabel('Tema',{exact:true}).selectOption('light');
  await assertTheme('light');
  await page.emulateMedia({colorScheme:'dark'});
  await assertTheme('light');
  assert.equal(await page.getByLabel('Tema',{exact:true}).locator('option').count(),3);
  assert.equal(await page.getByLabel('Idioma de imágenes',{exact:true}).count(),0);
  await page.getByLabel('Tema',{exact:true}).selectOption('system');
  await assertTheme('dark');
  await page.reload();
  await page.getByRole('heading',{name:'Mi colección',exact:true}).waitFor();
  await page.emulateMedia({colorScheme:'light'});
  await assertTheme('light');
  await page.getByRole('button',{name:'Abrir ajustes',exact:true}).click();
  assert.equal(await page.getByLabel('Tema',{exact:true}).inputValue(),'system');
  await page.getByRole('button',{name:'Cerrar',exact:true}).click();
  await page.getByRole('button',{name:'Canjes',exact:true}).click();
  assert.ok((await page.locator('main').innerText()).includes('Bulbasaur'));
  await page.getByRole('button',{name:'Análisis',exact:true}).click();
  await page.getByLabel('Probabilidad total por sobre (%)',{exact:true}).fill('10');
  await page.getByLabel('Número de sobres',{exact:true}).fill('2');
  assert.ok((await page.locator('main').innerText()).includes('19.00 %'));

  page.on('dialog',dialog=>dialog.accept());
  await page.getByRole('button',{name:'Abrir ajustes',exact:true}).click();
  await page.getByRole('button',{name:'Restaurar respaldo completo',exact:true}).click();
  await page.locator('#file').setInputFiles({name:'backup.json',mimeType:'application/json',buffer:Buffer.from(backupText)});
  await page.getByRole('status').filter({hasText:'Respaldo restaurado'}).waitFor();
  assert.equal(JSON.parse(await page.evaluate(()=>localStorage.getItem('demeberant-pocket-v1'))).decks.length,1);
  await page.getByRole('button',{name:'Colección',exact:true}).click();
  await page.locator('#collection-search').fill('A1-001');
  await page.emulateMedia({colorScheme:'dark'});
  await assertTheme('dark');
  await mkdir('web-reports',{recursive:true});await page.screenshot({path:'web-reports/mobile-dark.png',fullPage:true});
  await page.setViewportSize({width:1280,height:900});await page.screenshot({path:'web-reports/desktop-dark.png',fullPage:true});
  assert.deepEqual(errors,[]);
  await writeFile('web-reports/result.txt','Browser checks passed: quantity, wishlist, CSV, deck persistence, energies, complete backup, merge restore, theme persistence and system changes, filters, trades, probability, no page errors.\n');
  console.log('Browser smoke checks passed.');
 } catch(error) {
  await mkdir('web-reports',{recursive:true});
  await writeFile('web-reports/error.txt',String(error.stack));
  if(page) { await page.screenshot({path:'web-reports/failure.png',fullPage:true}).catch(()=>{});await writeFile('web-reports/page.html',await page.content().catch(()=>'')); }
  throw error;
} finally {await browser?.close();server.kill();}
