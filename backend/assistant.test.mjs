import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {schema,validateInput,runAssistant,createAssistantServer} from './assistant.mjs';

const card={id:'A1-001',name:'Bulbasaur',quantity:2,type:'Planta',category:'pokemon',stage:'basic',evolvesFrom:'',effects:'Heal 10 damage.'};
const input={mode:'generate',meta:'Fuente, fecha',goal:'Control',cards:[card],target:[]};
const proposal={name:'Borrador',strategy:'Revisar',energies:['Planta'],cards:[],replacements:[]};
const response=body=>new Response(JSON.stringify(body),{status:200});
test('schema is strict recursively without oversized ID enums',()=>{
 const value=schema(['A1-001']);assert.equal(value.additionalProperties,false);
 assert.equal(value.properties.cards.items.additionalProperties,false);
 assert.equal(value.properties.replacements.items.additionalProperties,false);
 assert.equal(value.properties.cards.items.properties.id.type,'string');
});
test('input validation rejects duplicate IDs, quantities and bad targets',()=>{
 assert.deepEqual(validateInput(input),['A1-001']);
 assert.throws(()=>validateInput({...input,cards:[card,card]}));
 assert.throws(()=>validateInput({...input,cards:[{...card,quantity:-1}]}));
 assert.throws(()=>validateInput({...input,mode:'replacements',target:[{id:card.id,count:2}]}));
});
test('provider request uses Responses strict schema and no retention store',async()=>{
 let sent;
 const result=await runAssistant(input,{apiKey:'TEST_SECRET',fetchImpl:async(url,options)=>{
   assert.equal(url,'https://api.openai.com/v1/responses');sent=JSON.parse(options.body);
   assert.equal(options.headers.Authorization,'Bearer TEST_SECRET');
   return response({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify(proposal)}]}]});
 }});
 assert.deepEqual(result,proposal);assert.equal(sent.store,false);assert.equal(sent.text.format.strict,true);assert.equal(sent.text.format.type,'json_schema');
});
test('refusals and truncated responses are not converted to decks',async()=>{
 for(const body of [{status:'incomplete',output:[]},{status:'completed',output:[{content:[{type:'refusal',refusal:'No'}]}]}])
   await assert.rejects(()=>runAssistant(input,{apiKey:'secret',fetchImpl:async()=>response(body)}));
});
test('HTTP provider failures are handled without returning payloads',async()=>{
 await assert.rejects(()=>runAssistant(input,{apiKey:'secret',fetchImpl:async()=>new Response('private error',{status:429})}),e=>e.status===429&&!e.message.includes('private'));
});
test('server enforces authentication and hides provider credentials',async()=>{
 const server=createAssistantServer({apiKey:'provider-key',accessToken:'session-token',fetchImpl:async()=>response({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify(proposal)}]}]})});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 const url='http://127.0.0.1:'+server.address().port+'/assist';
 try{
   assert.equal((await fetch(url,{method:'POST',body:JSON.stringify(input)})).status,401);
   const valid=await fetch(url,{method:'POST',headers:{Authorization:'Bearer session-token'},body:JSON.stringify(input)});
   assert.equal(valid.status,200);assert.deepEqual(await valid.json(),proposal);
   const invalid=await fetch(url,{method:'POST',headers:{Authorization:'Bearer session-token'},body:'{"mode":"oops"}'});
   assert.equal(invalid.status,400);
 }finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
test('server retains accented effects across UTF8 input',async()=>{
 const server=createAssistantServer({apiKey:'x',accessToken:'y',fetchImpl:async(_,options)=>{
   const request=JSON.parse(options.body);assert.ok(request.input.includes('energía'));
   return response({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify(proposal)}]}]});
 }});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 try{const result=await fetch('http://127.0.0.1:'+server.address().port+'/assist',{method:'POST',headers:{Authorization:'Bearer y'},body:JSON.stringify({...input,goal:'Acelerar energía'})});assert.equal(result.status,200);}
 finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
