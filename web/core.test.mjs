import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {canonical,parseCsv,exportCsv,deckReferences,validateBackup,mergeBackup,coverage,cumulativeChance,drawChance} from './core.mjs';
const record={id:'A1-001',name:'Bulbasaur',rarity:'♦',pack:'',quantity:2,wishlist:true,acquiredAt:100};
const prefs={dark:true,language:'es',theme:'dark'};
const backup={format:'demeberant-tcg-pocket-backup',version:1,inventory:[record],decks:[],preferences:prefs};
test('canonical IDs and invalid input',()=>{assert.equal(canonical(' promo-a-1 '),'PROMO-A-001');assert.equal(canonical('b4b-1'),'B4B-001');assert.throws(()=>canonical('__proto__'));assert.throws(()=>canonical('A1-0'));});
test('quoted CSV roundtrip preserves wishes and multiline names',()=>{
 const cards=[{...record,name:'Carta, "especial"\ny otra línea'}];
 const parsed=parseCsv('\uFEFF'+exportCsv(cards));
 assert.equal(parsed[0].name,cards[0].name);assert.equal(parsed[0].quantity,2);assert.equal(parsed[0].wishlist,true);
});
test('old CSV does not specify wishes',()=>{assert.equal(parseCsv('Set,ID,Nombre,Rareza,Cantidad\nA1,1,Bulbasaur,♦,1')[0].wishlist,undefined);});
test('invalid CSV is rejected completely',()=>{
 const header='Set,ID,Nombre,Rareza,Cantidad\n';
 for(const rows of ['A1,1,Bulbasaur,♦,-1','A1,1,Bulbasaur,♦,1\nA1,001,Bulbasaur,♦,2','A1,1,"Sin cerrar,♦,1','A1,1,Bulbasaur,♦,1.2'])
   assert.throws(()=>parseCsv(header+rows));
});
test('backup validation rejects invalid values',()=>{
 assert.deepEqual(validateBackup(JSON.stringify(backup)).inventory,[record]);
 assert.throws(()=>validateBackup(JSON.stringify({...backup,version:2})));
 assert.throws(()=>validateBackup(JSON.stringify({...backup,inventory:[{...record,quantity:-1}]})));
 assert.throws(()=>validateBackup(JSON.stringify({...backup,inventory:[record,record]})));
 assert.throws(()=>validateBackup(JSON.stringify({...backup,inventory:[{...record,wishlist:'true'}]})));
});
test('restore retains absent cards and deduplicates decks',()=>{
 const deck={name:'Borrador',archetype:'Manual',strategy:'Notas',cards:'A1-001:2',total:2,createdAt:100};
 const state={inventory:{'A1-002':{...record,id:'A1-002',quantity:5}},decks:[deck],preferences:prefs};
 const checked=validateBackup(JSON.stringify({...backup,decks:[deck]}));
 const restored=mergeBackup(mergeBackup(state,checked),checked);
 assert.equal(restored.inventory['A1-002'].quantity,5);assert.equal(restored.inventory['A1-001'].quantity,2);assert.equal(restored.decks.length,1);
});
test('deck codec supports legacy and current formats',()=>{
 assert.deepEqual(deckReferences('A1-1:2'),[{id:'A1-001',count:2}]);
 assert.deepEqual(deckReferences(JSON.stringify({format:2,cards:[{id:'A1-001',count:2}],energies:['Planta']})),[{id:'A1-001',count:2}]);
 assert.throws(()=>deckReferences('A1-1:3'));assert.throws(()=>deckReferences('A1-1:1;A1-001:1'));
 assert.throws(()=>deckReferences(JSON.stringify({format:2,cards:[],energies:['Planta','Planta']})));
});
test('probability exact cases and invalid inputs',()=>{
 assert.ok(Math.abs(cumulativeChance(.1,2)-.19)<1e-10);assert.equal(cumulativeChance(1,0),0);
 assert.ok(Math.abs(drawChance(20,2,5)-(1-210/380))<1e-10);assert.equal(drawChance(20,0,5),0);
 assert.throws(()=>cumulativeChance(NaN,2));assert.throws(()=>drawChance(20,21,5));
});
test('coverage prioritizes only missing cards and excludes promos',()=>{
 const result=coverage([{...record,packs:['Mewtwo'],quantity:0}],new Set([record.id]));
 assert.equal(result[0].score,9);assert.equal(coverage([{...record,packs:['Mewtwo']}]).length,0);
 assert.equal(coverage([{...record,id:'PROMO-A-001',packs:['Promo'],quantity:0}]).length,0);
});
test('bundled catalogue has unique canonical cards and no placeholder health',async()=>{
 const data=JSON.parse(await readFile(new URL('../public/pocket-catalog.json',import.meta.url),'utf8'));
 assert.equal(data.cards.length,4317);assert.equal(new Set(data.cards.map(c=>c.id)).size,4317);
 assert.equal(new Set(data.cards.map(c=>c.id.slice(0,c.id.lastIndexOf("-")))).size,24);
 assert.ok(data.cards.every(c=>canonical(c.id)===c.id&&!('hp'in c)));
 assert.ok(data.cards.some(c=>c.id==='B4B-001'));
});
