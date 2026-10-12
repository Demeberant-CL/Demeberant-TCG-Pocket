import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createConnectorServer} from './connector.mjs';
const deviceToken='device'.repeat(8),ownerSecret='owner'.repeat(8),publicUrl='https://pocket.example';
const snapshot={format:1,cards:Array.from({length:10},(_,i)=>({id:`A1-${String(i+1).padStart(3,'0')}`,name:`Card ${i}`,rulesName:`Card ${i}`,expansion:'A1',owned:2,type:'Planta',category:'pokemon',stage:'basic',evolvesFrom:'',effects:''})),decks:[{id:'1',name:'Saved',data:'{}'}]};
async function fixture(t,stateFile){const server=createConnectorServer({deviceToken,ownerSecret,publicUrl,stateFile});await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));const url=`http://127.0.0.1:${server.address().port}`;return async(path,options={})=>fetch(url+path,options);}
async function authorize(call,scope='pocket:read pocket:propose'){
 const registered=await(await call('/register',{method:'POST',body:JSON.stringify({redirect_uris:['https://chat.example/callback']})})).json();
 const verifier='v'.repeat(43),p=new URLSearchParams({client_id:registered.client_id,redirect_uri:'https://chat.example/callback',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url'),resource:publicUrl+'/mcp',scope,state:'state'});
 const auth=await call('/authorize?'+p,{method:'POST',body:new URLSearchParams({secret:ownerSecret}),redirect:'manual'});assert.equal(auth.status,302);const callback=new URL(auth.headers.get('location'));assert.equal(callback.searchParams.get('state'),'state');
 const params=new URLSearchParams({client_id:registered.client_id,grant_type:'authorization_code',redirect_uri:'https://chat.example/callback',code:callback.searchParams.get('code'),code_verifier:verifier,resource:publicUrl+'/mcp'});
 const tokens=await(await call('/token',{method:'POST',body:params})).json();return {tokens,params};
}
const appHeaders={Authorization:'Bearer '+deviceToken,'Content-Type':'application/json'};
async function sync(call){return(await call('/app/snapshot',{method:'PUT',headers:appHeaders,body:JSON.stringify(snapshot)})).json();}
async function rpc(call,token,name,args={}){return(await call('/mcp',{method:'POST',headers:{Authorization:'Bearer '+token,Accept:'application/json, text/event-stream','Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/call',params:{name,arguments:args}})})).json();}
const proposal=revision=>({revision,name:'Test deck',strategy:'Test',energies:['Planta'],cards:snapshot.cards.map(c=>({id:c.id,count:2})),replacements:[]});
test('OAuth + MCP reads + proposal receipt, staleness and deletion',async t=>{
 const call=await fixture(t);assert.equal((await call('/.well-known/openid-configuration')).status,404);assert.equal((await call('/app/proposals')).status,401);assert.equal((await call('/mcp')).status,401);
 const s=await sync(call),{tokens,params}=await authorize(call);assert.ok(tokens.access_token);assert.equal((await call('/token',{method:'POST',body:params})).status,400);
 const summary=await rpc(call,tokens.access_token,'collection_summary');assert.equal(JSON.parse(summary.result.content[0].text).copies,20);
 const search=await rpc(call,tokens.access_token,'search_cards',{limit:2});assert.equal(JSON.parse(search.result.content[0].text).cards.length,2);
 assert.equal((await rpc(call,tokens.access_token,'submit_deck_proposal',proposal(s.revision))).result.isError,undefined);
 let inbox=await(await call('/app/proposals',{headers:appHeaders})).json();assert.equal(inbox.proposals.length,1);
 await sync(call);assert.equal((await rpc(call,tokens.access_token,'submit_deck_proposal',proposal(s.revision))).result.isError,true);
 assert.equal((await call('/app/snapshot',{method:'PUT',headers:{Authorization:'Bearer '+tokens.access_token},body:JSON.stringify(snapshot)})).status,401);
 await call('/app/data',{method:'DELETE',headers:appHeaders});assert.equal((await rpc(call,tokens.access_token,'collection_summary')).result.isError,true);
});
test('reject unknown IDs, counts, names across variants, scope, bad origin and PKCE',async t=>{
 const call=await fixture(t),s=await sync(call),{tokens}=await authorize(call);
 for(const modify of [p=>p.cards[0].id='fake',p=>p.cards[0].count=3,p=>p.cards.pop(),p=>p.energies=['Fake']]){const p=proposal(s.revision);modify(p);assert.equal((await rpc(call,tokens.access_token,'submit_deck_proposal',p)).result.isError,true);}
 const {tokens:read}=await authorize(call,'pocket:read');assert.equal((await rpc(call,read.access_token,'submit_deck_proposal',proposal(s.revision))).result.isError,true);
 assert.equal((await call('/health',{headers:{Origin:'https://evil.example'}})).status,403);
 assert.equal((await call('/authorize?client_id=unknown')).status,400);
 const altered=structuredClone(snapshot);altered.cards[1].rulesName=altered.cards[0].rulesName;const newer=await(await call('/app/snapshot',{method:'PUT',headers:appHeaders,body:JSON.stringify(altered)})).json();assert.equal((await rpc(call,tokens.access_token,'submit_deck_proposal',proposal(newer.revision))).result.isError,true);
});
test('persisted snapshot and inbox survive server restart',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'pocket-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));const file=join(dir,'state.json');const first=await fixture(t,file),s=await sync(first),{tokens}=await authorize(first);await rpc(first,tokens.access_token,'submit_deck_proposal',proposal(s.revision));
 const second=await fixture(t,file);const inbox=await(await second('/app/proposals',{headers:appHeaders})).json();assert.equal(inbox.proposals.length,1);assert.equal(inbox.revision,s.revision);
});
