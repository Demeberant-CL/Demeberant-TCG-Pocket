import test from 'node:test';
import assert from 'node:assert/strict';
import {validateSnapshot} from './connector.mjs';

const card=(id,owned=1)=>({id,owned,name:'Petilil',rulesName:'Petilil',expansion:'B1',type:'Planta',category:'Pokémon',stage:'Básico',evolvesFrom:'',effects:''});
const snapshot=(cards=[card('B1-017')],decks=[])=>({format:1,cards,decks,draft:null});
test('valid collection accepted',()=>assert.equal(validateSnapshot(snapshot()).format,1));
test('duplicate card IDs rejected',()=>assert.throws(()=>validateSnapshot(snapshot([card('x'),card('x')]))));
test('negative quantity rejected',()=>assert.throws(()=>validateSnapshot(snapshot([card('x',-1)]))));
test('fractional quantity rejected',()=>assert.throws(()=>validateSnapshot(snapshot([card('x',1.5)]))));
test('malformed deck rejected',()=>assert.throws(()=>validateSnapshot(snapshot([card('x')],[{id:'25',name:'Deck',data:42}]))));
test('missing decks rejected',()=>assert.throws(()=>validateSnapshot({format:1,cards:[]})));
test('catalog limit enforced',()=>assert.throws(()=>validateSnapshot(snapshot(Array.from({length:10001},(_,i)=>card(String(i)))))));
