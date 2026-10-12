import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createConnectorServer} from './connector.mjs';
const deviceToken='device'.repeat(8),ownerSecret='owner'.repeat(8),publicUrl='https://pocket.example';
const snapshot={format:1,cards:Array.from({length:10},(_,i)=>({id:`A1-${String(i+1).padStart(3,'0')}`,name:`Card ${i}`,rulesName:`Card ${i}`,expansion:'A1',owned:2,type:'Planta',category:'pokemon',stage:'basic',evolvesFrom:'',effects:''})),decks:[{id:'1',name:'Saved',data:'{}'}]};
async function fixture(t,stateFile,extras={}){const server=createConnectorServer({deviceToken,ownerSecret,publicUrl,stateFile,...extras});await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));const url=`http://127.0.0.1:${server.address().port}`;return async(path,options={})=>fetch(url+path,options);}
async function authorize(call,scope='pocket:read pocket:propose',origin){
 const registered=await(await call('/register',{method:'POST',body:JSON.stringify({redirect_uris:['https://chat.example/callback']})})).json();
 const verifier='v'.repeat(43),p=new URLSearchParams({client_id:registered.client_id,redirect_uri:'https://chat.example/callback',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url'),resource:publicUrl+'/mcp',scope,state:'state'});
 const form=await(await call('/authorize?'+p)).text();const csrf=form.match(/name="csrf" value="([^"]+)"/)[1];
 const auth=await call('/authorize?'+p,{method:'POST',headers:origin?{Origin:origin}:{},body:new URLSearchParams({secret:ownerSecret,csrf}),redirect:'manual'});assert.equal(auth.status,302);const callback=new URL(auth.headers.get('location'));assert.equal(callback.searchParams.get('state'),'state');
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

test('OAuth form accepts opaque browser origin only with a valid single-use form token',async t=>{
 const call=await fixture(t);
 for(const origin of ['null','https://chatgpt.com']){const {tokens}=await authorize(call,'pocket:read pocket:propose',origin);assert.ok(tokens.access_token);}
 assert.equal((await call('/mcp',{method:'POST',headers:{Origin:'null'},body:'{}'})).status,403);
 assert.equal((await call('/app/proposals',{headers:{Origin:'null',Authorization:'Bearer '+deviceToken}})).status,403);
 const registered=await(await call('/register',{method:'POST',body:JSON.stringify({redirect_uris:['https://chat.example/callback']})})).json();
 const p=new URLSearchParams({client_id:registered.client_id,redirect_uri:'https://chat.example/callback',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update('v'.repeat(43)).digest('base64url'),resource:publicUrl+'/mcp'});
 const form=await(await call('/authorize?'+p)).text(),csrf=form.match(/name="csrf" value="([^"]+)"/)[1];
 const post=(token,origin='null')=>call('/authorize?'+p,{method:'POST',headers:{Origin:origin},body:new URLSearchParams({secret:ownerSecret,csrf:token}),redirect:'manual'});
 assert.equal((await post('fake')).status,403);
 assert.equal((await post(csrf,'https://evil.example')).status,403);
 assert.equal((await post(csrf)).status,302);
 assert.equal((await post(csrf)).status,403);
});

test('registered client survives restart; altered and unregistered clients remain rejected',async t=>{
 const first=await fixture(t),second=await fixture(t);
 const registered=await(await first('/register',{method:'POST',body:JSON.stringify({redirect_uris:['https://chat.example/callback']})})).json();
 const p=new URLSearchParams({client_id:registered.client_id,redirect_uri:'https://chat.example/callback',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update('v'.repeat(43)).digest('base64url'),resource:publicUrl+'/mcp'});
 assert.equal((await second('/authorize?'+p)).status,200);
 p.set('redirect_uri','https://evil.example/callback');assert.equal((await second('/authorize?'+p)).status,400);
 p.set('redirect_uri','https://chat.example/callback');p.set('client_id',registered.client_id+'x');assert.equal((await second('/authorize?'+p)).status,400);
 p.set('client_id','unregistered');assert.equal((await second('/authorize?'+p)).status,400);
 const legacy=await fixture(t,undefined,{legacyClientId:'known-old-client',legacyRedirect:'https://chatgpt.com/connector/oauth/known'});
 p.set('client_id','known-old-client');p.set('redirect_uri','https://chatgpt.com/connector/oauth/known');assert.equal((await legacy('/authorize?'+p)).status,200);
 p.set('redirect_uri','https://chatgpt.com/connector/oauth/other');assert.equal((await legacy('/authorize?'+p)).status,400);
});

test('open authorization form survives restart and failed forms renew without granting access',async t=>{
 const first=await fixture(t),second=await fixture(t);
 const registered=await(await first('/register',{method:'POST',body:JSON.stringify({redirect_uris:['https://chatgpt.com/connector/oauth/test']})})).json();
 const p=new URLSearchParams({client_id:registered.client_id,redirect_uri:'https://chatgpt.com/connector/oauth/test',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update('v'.repeat(43)).digest('base64url'),resource:publicUrl+'/mcp'});
 const page=await first('/authorize?'+p),html=await page.text(),csrf=html.match(/name="csrf" value="([^"]+)"/)[1];
 assert.ok(page.headers.get('content-security-policy').includes("form-action 'self' https://chatgpt.com"));
 const post=(token,secret=ownerSecret)=>second('/authorize?'+p,{method:'POST',headers:{Origin:'null'},body:new URLSearchParams({secret,csrf:token}),redirect:'manual'});
 assert.equal((await post(csrf)).status,302);
 const replay=await post(csrf);assert.equal(replay.status,403);assert.match(await replay.text(),/formulario se renovó/);
 const renewed=await post('missing');assert.equal(renewed.status,403);const retry=(await renewed.text()).match(/name="csrf" value="([^"]+)"/)[1];
 const wrong=await post(retry,'wrong');assert.equal(wrong.status,401);const valid=(await wrong.text()).match(/name="csrf" value="([^"]+)"/)[1];assert.equal((await post(valid)).status,302);
});

test('remote command queue is scoped and requires Android acknowledgment',async t=>{
 const call=await fixture(t),s=await sync(call);
 const {tokens}=await authorize(call,'pocket:read pocket:control');
 const {tokens:read}=await authorize(call,'pocket:read');
 const denied=await rpc(call,read.access_token,'queue_app_command',{revision:s.revision,action:'inspect_errors'});
 assert.equal(denied.result.isError,true);
 const stale=await rpc(call,tokens.access_token,'queue_app_command',{revision:'outdated',action:'inspect_errors'});
 assert.equal(stale.result.isError,true);
 const queued=await rpc(call,tokens.access_token,'queue_app_command',{revision:s.revision,action:'inspect_errors'});
 const command=JSON.parse(queued.result.content[0].text);
 assert.equal(command.status,'pending');
 const pending=await(await call('/app/commands',{headers:appHeaders})).json();
 assert.equal(pending.commands.length,1);
 assert.equal((await call('/app/commands')).status,401);
 const ack=await call('/app/commands/result',{method:'POST',headers:appHeaders,body:JSON.stringify({id:command.id,status:'completed',message:'Inspected'})});
 assert.equal(ack.status,200);
 const status=await rpc(call,tokens.access_token,'command_status',{id:command.id});
 assert.equal(JSON.parse(status.result.content[0].text).status,'completed');
 const again=await call('/app/commands/result',{method:'POST',headers:appHeaders,body:JSON.stringify({id:command.id,status:'completed',message:'Replay'})});
 assert.equal(again.status,400);
});
test('diagnostics are sanitized and proposals survive resync',async t=>{
 const call=await fixture(t),s=await sync(call),{tokens}=await authorize(call);
 await rpc(call,tokens.access_token,'submit_deck_proposal',proposal(s.revision));
 await sync(call);
 const inbox=await(await call('/app/proposals',{headers:appHeaders})).json();
 assert.equal(inbox.proposals.length,1);
 assert.equal((await call('/app/diagnostics',{method:'PUT',headers:appHeaders,body:JSON.stringify({version:'1.0',errors:['Sync failed']})})).status,200);
 const report=await rpc(call,tokens.access_token,'app_diagnostics');
 assert.deepEqual(JSON.parse(report.result.content[0].text).diagnostics.errors,['Sync failed']);
});

test('snapshot refresh rejects stale queued commands',async t=>{
 const call=await fixture(t),s=await sync(call),{tokens}=await authorize(call,'pocket:read pocket:control');
 const queued=await rpc(call,tokens.access_token,'queue_app_command',{revision:s.revision,action:'inspect_collection'});
 const id=JSON.parse(queued.result.content[0].text).id;
 await sync(call);
 const pending=await(await call('/app/commands',{headers:appHeaders})).json();
 assert.equal(pending.commands.length,0);
 const status=await rpc(call,tokens.access_token,'command_status',{id});
 assert.equal(JSON.parse(status.result.content[0].text).status,'rejected');
});
test('delete deck command requires explicit confirmation',async t=>{
 const call=await fixture(t),s=await sync(call),{tokens}=await authorize(call,'pocket:read pocket:control');
 const denied=await rpc(call,tokens.access_token,'queue_app_command',{revision:s.revision,action:'delete_deck',deckId:'1'});
 assert.equal(denied.result.isError,true);
 const allowed=await rpc(call,tokens.access_token,'queue_app_command',{revision:s.revision,action:'delete_deck',deckId:'1',confirmDelete:true});
 assert.equal(JSON.parse(allowed.result.content[0].text).status,'pending');
});
