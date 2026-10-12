import {createServer} from 'node:http';
import {randomBytes, timingSafeEqual, createHash, createHmac, createCipheriv, createDecipheriv} from 'node:crypto';
import {readFileSync, writeFileSync, renameSync, mkdirSync} from 'node:fs';
import {dirname} from 'node:path';
import {pathToFileURL} from 'node:url';
const random=()=>randomBytes(32).toString('base64url');
const same=(a,b)=>{const x=Buffer.from(String(a||'')),y=Buffer.from(String(b||''));return x.length===y.length&&timingSafeEqual(x,y);};
const object=(properties,required=Object.keys(properties))=>({type:'object',properties,required,additionalProperties:false});
const text={type:'string',maxLength:5000};
const tools=[
  {name:'collection_summary',description:'Resumen de la colección sincronizada, fecha y mazos guardados.',inputSchema:object({})},
  {name:'search_cards',description:'Busca cartas del catálogo sincronizado por nombre, código o expansión. Incluye cantidades reales y efectos cuando están disponibles; nunca inventes efectos desconocidos.',inputSchema:object({query:{type:'string',maxLength:120},ownedOnly:{type:'boolean'},offset:{type:'integer',minimum:0},limit:{type:'integer',minimum:1,maximum:100}},[])},
  {name:'read_deck',description:'Consulta un mazo guardado mediante su ID o el borrador actual (draft).',inputSchema:object({id:{type:'string',maxLength:100}})},
  {name:'app_diagnostics',description:'Consulta el último informe de diagnóstico enviado por Android, sin datos secretos.',inputSchema:object({})},
  {name:'queue_app_command',description:'Solicita una acción a Android; no se ejecuta hasta que el teléfono la recibe y confirma. Solo consultas de diagnóstico disponibles. Usa revision actual.',inputSchema:object({revision:text,action:{type:'string',enum:['inspect_decks','inspect_collection','inspect_errors']},deckId:{type:'string',maxLength:100},confirmDelete:{type:'boolean'}},['revision','action'])},
  {name:'command_status',description:'Consulta el estado y resultado de una instrucción enviada al teléfono.',inputSchema:object({id:{type:'string',maxLength:100}})},
  {name:'submit_deck_proposal',description:'Envía un mazo de 20 cartas para revisión en Rotom AI TCG Pocket. No modifica la colección ni guarda o reemplaza mazos. Usa IDs y revisión de la colección consultada. Máximo dos copias por nombre y líneas evolutivas completas. replacements debe estar vacío.',inputSchema:object({revision:text,name:{type:'string',maxLength:120},strategy:text,energies:{type:'array',minItems:1,maxItems:3,items:{type:'string',enum:['Planta','Fuego','Agua','Rayo','Psíquico','Lucha','Oscuridad','Metal']}},cards:{type:'array',minItems:10,maxItems:20,items:object({id:{type:'string'},count:{type:'integer',minimum:1,maximum:2}})},replacements:{type:'array',maxItems:0}})}
].map(t=>({...t,securitySchemes:[{type:'oauth2',scopes:[t.name==='submit_deck_proposal'?'pocket:propose':t.name==='queue_app_command'?'pocket:control':'pocket:read']}],_meta:{securitySchemes:[{type:'oauth2',scopes:[t.name==='submit_deck_proposal'?'pocket:propose':t.name==='queue_app_command'?'pocket:control':'pocket:read']}]},annotations:{readOnlyHint:!['submit_deck_proposal','queue_app_command'].includes(t.name),destructiveHint:t.name==='queue_app_command',idempotentHint:!['submit_deck_proposal','queue_app_command'].includes(t.name),openWorldHint:false}}));
export function validateSnapshot(s){
  if(!s||s.format!==1||!Array.isArray(s.cards)||s.cards.length>10000||!Array.isArray(s.decks)||s.decks.length>1000)throw Error('Invalid snapshot');
  const ids=new Set();
  for(const c of s.cards){
    if(!c||typeof c.id!=='string'||c.id.length>100||ids.has(c.id)||!Number.isInteger(c.owned)||c.owned<0||c.owned>100000||!['name','rulesName','expansion','type','category','stage','evolvesFrom','effects'].every(k=>typeof c[k]==='string'&&c[k].length<=10000))throw Error('Invalid card');
    ids.add(c.id);
  }
  for(const d of [...s.decks,...(s.draft?[s.draft]:[])]){
    if(!d||typeof d.id!=='string'||typeof d.name!=='string'||d.name.length>120||typeof d.data!=='string'||d.data.length>100000)throw Error('Invalid deck');
  }
  return s;
}
export function createConnectorServer({deviceToken,ownerSecret,publicUrl,stateFile,clock=Date.now,legacyClientId,legacyRedirect}={}){
  if(!deviceToken||deviceToken.length<32||!ownerSecret||ownerSecret.length<32||same(deviceToken,ownerSecret))throw Error('Two different secrets of at least 32 characters required');
  const origin=new URL(publicUrl);if(origin.protocol!=='https:'||origin.pathname!=='/'||origin.search||origin.hash||origin.username||origin.password)throw Error('Public HTTPS origin required');
  const base=origin.origin;
  let state={snapshot:null,proposals:[],commands:[],diagnostics:null};
  if(stateFile){try{state=JSON.parse(readFileSync(stateFile,'utf8'));if(state.snapshot)validateSnapshot(state.snapshot);if(!Array.isArray(state.proposals)||state.proposals.length>20)throw Error('Invalid state');}catch(e){if(e.code!=='ENOENT')throw e;}}
  state.commands??=[];state.diagnostics??=null;if(!Array.isArray(state.commands)||state.commands.length>100)throw Error('Invalid commands');
  const clients=new Map(),codes=new Map(),tokens=new Map(),rates=new Map(),forms=new Map();
  // Keep OAuth grants encrypted beside the collection, never in plaintext.
  // A durable volume is required to survive replacement of a Render instance.
  const authFile=stateFile?stateFile+'.oauth':null;
  const authKey=createHash('sha256').update('rotom-oauth-storage-v1\\0'+ownerSecret).digest();
  const authMaps={codes,tokens,forms};
  if(authFile){
    try{
      const envelope=JSON.parse(readFileSync(authFile,'utf8'));
      if(envelope.version!==1)throw Error('Unsupported OAuth storage');
      const decipher=createDecipheriv('aes-256-gcm',authKey,Buffer.from(envelope.iv,'base64url'));
      decipher.setAAD(Buffer.from(base));decipher.setAuthTag(Buffer.from(envelope.tag,'base64url'));
      const stored=JSON.parse(Buffer.concat([decipher.update(Buffer.from(envelope.data,'base64url')),decipher.final()]).toString('utf8'));
      for(const [name,map] of Object.entries(authMaps)){
        if(!Array.isArray(stored[name])||stored[name].length>1000)throw Error('Invalid OAuth storage');
        for(const [key,value] of stored[name]){
          if(typeof key!=='string'||!value||!Number.isFinite(value.expires))throw Error('Invalid OAuth grant');
          if(value.expires>clock())map.set(key,value);
        }
      }
    }catch(e){if(e.code!=='ENOENT')throw Error('OAuth storage cannot be read; restore its owner key and public URL.');}
  }
  const saveAuth=()=>{
    if(!authFile)return;
    const stored=Object.fromEntries(Object.entries(authMaps).map(([name,map])=>[name,[...map]]));
    const iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',authKey,iv);cipher.setAAD(Buffer.from(base));
    const data=Buffer.concat([cipher.update(JSON.stringify(stored),'utf8'),cipher.final()]);
    mkdirSync(dirname(authFile),{recursive:true,mode:0o700});
    writeFileSync(authFile+'.tmp',JSON.stringify({version:1,iv:iv.toString('base64url'),tag:cipher.getAuthTag().toString('base64url'),data:data.toString('base64url')}),{mode:0o600});
    renameSync(authFile+'.tmp',authFile);
  };
  // Persist consumption too, so used codes/forms/refresh tokens stay consumed.
  for(const map of Object.values(authMaps)){
    map.set=(key,value)=>{Map.prototype.set.call(map,key,value);saveAuth();return map;};
    map.delete=key=>{const removed=Map.prototype.delete.call(map,key);if(removed)saveAuth();return removed;};
  }
  // Registered public clients carry authenticated metadata so Render restarts do not erase them.
  const clientSignature=payload=>createHmac('sha256',ownerSecret).update('pocket-client-v1.'+payload).digest('base64url');
  const readClient=id=>{
    if(typeof id!=='string'||id.length>16000)return null;
    if(legacyClientId&&id===legacyClientId&&legacyRedirect){
      const redirect=new URL(legacyRedirect);
      if(redirect.origin==='https://chatgpt.com'&&redirect.pathname.startsWith('/connector/oauth/')&&!redirect.search&&!redirect.hash)return {redirects:[legacyRedirect]};
    }
    const parts=id.split('.');if(parts.length!==3||parts[0]!=='pa1'||!same(parts[2],clientSignature(parts[1])))return null;
    try{const row=JSON.parse(Buffer.from(parts[1],'base64url').toString('utf8'));return row.issuer===base&&Array.isArray(row.redirects)?row:null;}catch{return null;}
  };
  const persist=(next)=>{if(stateFile){mkdirSync(dirname(stateFile),{recursive:true,mode:0o700});writeFileSync(stateFile+'.tmp',JSON.stringify(next),{mode:0o600});renameSync(stateFile+'.tmp',stateFile);}state=next;};
  const prune=()=>{for(const map of [clients,codes,tokens,rates,forms])for(const [k,v]of map)if(v.expires<clock())map.delete(k);};
  const metadata={issuer:base,authorization_endpoint:base+'/authorize',token_endpoint:base+'/token',registration_endpoint:base+'/register',response_types_supported:['code'],grant_types_supported:['authorization_code','refresh_token'],token_endpoint_auth_methods_supported:['none'],code_challenge_methods_supported:['S256'],scopes_supported:['pocket:read','pocket:propose','pocket:control']};
  return createServer(async(req,res)=>{
    const reply=(code,value,extra={})=>{res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store',...extra});res.end(value===undefined?'':JSON.stringify(value));};
    try{
      prune();const url=new URL(req.url,base),path=url.pathname;
      const formOrigin=path==='/authorize' && (!req.headers.origin || [base,'null','https://chatgpt.com'].includes(req.headers.origin));
      if(req.headers.origin&&req.headers.origin!==base&&!formOrigin)return reply(403,{error:'Invalid origin'});
      if(req.method==='GET'&&path==='/health')return reply(200,{ok:true});
      if(req.method==='GET'&&['/.well-known/oauth-protected-resource','/.well-known/oauth-protected-resource/mcp'].includes(path))return reply(200,{resource:base+'/mcp',authorization_servers:[base],scopes_supported:metadata.scopes_supported});
      if(req.method==='GET'&&path==='/.well-known/oauth-authorization-server')return reply(200,metadata);
      // Bound authentication attempts and total owner-scoped load. Do not trust forwarding headers.
      const ip=req.socket.remoteAddress||'local',bucket=rates.get(ip)||{count:0,expires:clock()+60000};
      if(rates.size>1000&&!rates.has(ip))return reply(429,{error:'Try later'});
      rates.set(ip,bucket);if(++bucket.count>120)return reply(429,{error:'Try later'});
      const body=async()=>{const chunks=[];let size=0;for await(const c of req){size+=c.length;if(size>8000000)throw Error('Body too large');chunks.push(c);}return Buffer.concat(chunks).toString('utf8');};
      const authorization=(params)=>{const client=readClient(params.get('client_id'));if(!client||params.get('response_type')!=='code'||!client.redirects.includes(params.get('redirect_uri'))||params.get('code_challenge_method')!=='S256'||! /^[A-Za-z0-9_-]{43}$/.test(params.get('code_challenge')||'')||params.get('resource')!==base+'/mcp')throw Error('Invalid authorization');const scope=(params.get('scope')||'pocket:read pocket:propose').split(' ');if(scope.some(s=>!metadata.scopes_supported.includes(s)))throw Error('Invalid scope');return {client:params.get('client_id'),redirect:params.get('redirect_uri'),challenge:params.get('code_challenge'),resource:params.get('resource'),scope:scope.join(' ')};};
      if(path==='/register'&&req.method==='POST'){
        if(clients.size>=100)return reply(429,{error:'Try later'});
        const input=JSON.parse(await body());if(!Array.isArray(input.redirect_uris)||input.redirect_uris.length<1||input.redirect_uris.length>5||input.redirect_uris.some(r=>{try{const u=new URL(r);return r.length>2000||u.protocol!=='https:'||u.username||u.password||u.hash;}catch{return true;}}))return reply(400,{error:'invalid_redirect_uri'});
        const payload=Buffer.from(JSON.stringify({issuer:base,redirects:input.redirect_uris,nonce:random()})).toString('base64url');
        const id='pa1.'+payload+'.'+clientSignature(payload);clients.set(id,{expires:clock()+86400000});return reply(201,{client_id:id,redirect_uris:input.redirect_uris,token_endpoint_auth_method:'none',grant_types:metadata.grant_types_supported,response_types:['code']});
      }
      const formSignature=payload=>createHmac('sha256',ownerSecret).update('pocket-form-v1.'+payload).digest('base64url');
      const showForm=(auth,status=200,message='')=>{
        const payload=Buffer.from(JSON.stringify({binding:JSON.stringify(auth),expires:clock()+600000,nonce:random()})).toString('base64url');
        const csrf=payload+'.'+formSignature(payload);
        const redirectOrigin=new URL(auth.redirect).origin;
        res.writeHead(status,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store',
          'Content-Security-Policy':"default-src 'none'; form-action 'self' "+redirectOrigin+"; frame-ancestors 'none'",'X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'});
        return res.end('<!doctype html><html lang="es"><meta name="viewport" content="width=device-width"><title>Conectar Rotom AI TCG Pocket</title><h1>Conectar Rotom AI TCG Pocket</h1><p>Permite consultar tu colección sincronizada y enviar propuestas pendientes de revisión. No permite modificar tus cartas ni guardar mazos.</p><p>Destino: '+redirectOrigin+'</p><p>'+message+'</p><form method="post"><input type="hidden" name="csrf" value="'+csrf+'"><label>Clave del propietario <input name="secret" type="password" required autocomplete="off"></label><button>Autorizar conexión</button></form></html>');
      };
      if(path==='/authorize'&&req.method==='GET')return showForm(authorization(url.searchParams));
      if(path==='/authorize'&&req.method==='POST'){
        const auth=authorization(url.searchParams),input=new URLSearchParams(await body());
        const csrf=input.get('csrf')||'',parts=csrf.split('.');let form;
        if(csrf.length<=30000&&parts.length===2&&same(parts[1],formSignature(parts[0]))){
          try{form=JSON.parse(Buffer.from(parts[0],'base64url').toString('utf8'));}catch{}
        }
        if(!form||!Number.isFinite(form.expires)||form.expires<clock()||!same(form.binding,JSON.stringify(auth))||forms.has(csrf))
          return showForm(auth,403,'El formulario se renovó. Introduce la clave del propietario y vuelve a autorizar.');
        if(forms.size>=200)return reply(429,{error:'Try later'});
        forms.set(csrf,{expires:form.expires});
        if(!same(input.get('secret'),ownerSecret))return showForm(auth,401,'La clave no es válida. Usa la clave del propietario del archivo de configuración.');
        const code=random();codes.set(code,{...auth,expires:clock()+300000});const dest=new URL(auth.redirect);dest.searchParams.set('code',code);if(url.searchParams.has('state'))dest.searchParams.set('state',url.searchParams.get('state'));res.writeHead(302,{Location:dest.href,'Cache-Control':'no-store'});return res.end();
      }
      if(path==='/token'&&req.method==='POST'){
        const p=new URLSearchParams(await body());let grant;
        if(p.get('grant_type')==='authorization_code'){
          grant=codes.get(p.get('code'));codes.delete(p.get('code'));
          const verifier=p.get('code_verifier')||'';
          if(!grant||verifier.length<43||verifier.length>128||grant.redirect!==p.get('redirect_uri')||!same(grant.challenge,createHash('sha256').update(verifier).digest('base64url')))return reply(400,{error:'invalid_grant'});
        }else if(p.get('grant_type')==='refresh_token'){
          grant=tokens.get(p.get('refresh_token'));if(!grant||grant.kind!=='refresh')return reply(400,{error:'invalid_grant'});
        }else return reply(400,{error:'unsupported_grant_type'});
        if(grant.client!==p.get('client_id')||grant.resource!==p.get('resource'))return reply(400,{error:'invalid_grant'});
        if(p.get('grant_type')==='refresh_token')tokens.delete(p.get('refresh_token'));
        if(tokens.size>200)return reply(429,{error:'Try later'});
        const access=random(),refresh=random();tokens.set(access,{...grant,kind:'access',expires:clock()+3600000});tokens.set(refresh,{...grant,kind:'refresh',expires:clock()+86400000});
        return reply(200,{access_token:access,refresh_token:refresh,token_type:'Bearer',expires_in:3600,scope:grant.scope});
      }
      const header=req.headers.authorization||'',bearer=header.startsWith('Bearer ')?header.slice(7):'';
      if(path.startsWith('/app/')){
        if(!same(bearer,deviceToken))return reply(401,{error:'Unauthorized'});
        if(path==='/app/snapshot'&&req.method==='PUT'){
          const snapshot={...validateSnapshot(JSON.parse(await body())),revision:random(),syncedAt:new Date(clock()).toISOString()};
          persist({...state,snapshot,proposals:state.proposals,commands:state.commands.map(cmd=>cmd.status==='pending'?{...cmd,status:'rejected',message:'Snapshot updated; request again using current revision',finishedAt:new Date(clock()).toISOString()}:cmd)});return reply(200,{revision:snapshot.revision,syncedAt:snapshot.syncedAt});
        }
        if(path==='/app/commands'&&req.method==='GET')return reply(200,{commands:state.commands.filter(c=>c.status==='pending').map(({id,revision,action,deckId,createdAt})=>({id,revision,action,deckId,createdAt}))});
        if(path==='/app/commands/result'&&req.method==='POST'){
          const input=JSON.parse(await body());const cmd=state.commands.find(c=>c.id===input.id);
          if(!cmd||cmd.status!=='pending'||!['completed','failed','rejected'].includes(input.status)||typeof input.message!=='string'||input.message.length>2000)return reply(400,{error:'Invalid command result'});
          persist({...state,commands:state.commands.map(c=>c.id===cmd.id?{...c,status:input.status,message:input.message,finishedAt:new Date(clock()).toISOString()}:c)});return reply(200,{ok:true});
        }
        if(path==='/app/diagnostics'&&req.method==='PUT'){
          const input=JSON.parse(await body());if(!input||typeof input.version!=='string'||input.version.length>100||!Array.isArray(input.errors)||input.errors.length>50||input.errors.some(e=>typeof e!=='string'||e.length>1000))return reply(400,{error:'Invalid diagnostics'});
          persist({...state,diagnostics:{version:input.version,errors:input.errors,syncedAt:new Date(clock()).toISOString()}});return reply(200,{ok:true});
        }
        if(path==='/app/proposals'&&req.method==='GET')return reply(200,{revision:state.snapshot?.revision,proposals:state.proposals});
        if(path==='/app/data'&&req.method==='DELETE'){persist({snapshot:null,proposals:[],commands:[],diagnostics:null});return reply(200,{deleted:true});}
        return reply(404,{error:'Not found'});
      }
      if(path!=='/mcp')return reply(404,{error:'Not found'});
      const grant=tokens.get(bearer);if(!grant||grant.kind!=='access')return reply(401,{error:'Unauthorized'},{'WWW-Authenticate':`Bearer resource_metadata="${base}/.well-known/oauth-protected-resource"`});
      if(req.method!=='POST')return reply(405,{error:'Use POST'},{Allow:'POST'});
      if(!String(req.headers.accept||'').includes('application/json')||!String(req.headers.accept||'').includes('text/event-stream'))return reply(406,{error:'Accept JSON and SSE required'});
      if(req.headers['mcp-protocol-version']&&!['2025-03-26','2025-06-18','2025-11-25'].includes(req.headers['mcp-protocol-version']))return reply(400,{error:'Unsupported protocol version'});
      if(!String(req.headers['content-type']||'').startsWith('application/json'))return reply(415,{error:'Use application/json'});
      const rpc=JSON.parse(await body());if(rpc.jsonrpc!=='2.0'||typeof rpc.method!=='string'||(rpc.id!==undefined&&typeof rpc.id!=='string'&&typeof rpc.id!=='number'))return reply(400,{error:'Invalid JSON-RPC'});
      if(rpc.id===undefined){if(rpc.method.startsWith('notifications/'))return reply(202);return reply(400,{error:'Missing ID'});}
      const result=value=>reply(200,{jsonrpc:'2.0',id:rpc.id,result:value});
      if(rpc.method==='initialize')return result({protocolVersion:['2025-03-26','2025-06-18','2025-11-25'].includes(rpc.params?.protocolVersion)?rpc.params.protocolVersion:'2025-11-25',capabilities:{tools:{}},serverInfo:{name:'Rotom AI TCG Pocket',version:'1.0.0'},instructions:'Los datos son una copia sincronizada del teléfono. Comprueba syncedAt. Las propuestas necesitan revisión y validación Android; no se aplican automáticamente.'});
      if(rpc.method==='ping')return result({});
      if(rpc.method==='tools/list')return result({tools});
      if(rpc.method!=='tools/call')return reply(200,{jsonrpc:'2.0',id:rpc.id,error:{code:-32601,message:'Method not found'}});
      const name=rpc.params?.name,args=rpc.params?.arguments||{},s=state.snapshot;
      const toolResult=value=>result({content:[{type:'text',text:JSON.stringify(value)}]});
      try{
        if(!tools.some(t=>t.name===name))throw Error('Unknown tool');
        const permission=name==='submit_deck_proposal'?'pocket:propose':name==='queue_app_command'?'pocket:control':'pocket:read';if(!grant.scope.split(' ').includes(permission))throw Error('Insufficient scope');
        if(name==='app_diagnostics')return toolResult({diagnostics:state.diagnostics||null,syncedAt:s?.syncedAt||null});
        if(name==='command_status'){const cmd=state.commands.find(c=>c.id===args.id);if(!cmd)throw Error('Command not found');return toolResult(cmd);}
        if(!s)throw Error('Sin colección: sincroniza desde Rotom AI TCG Pocket.');
        if(name==='queue_app_command'){
          if(args.revision!==s.revision)throw Error('La colección cambió. Consúltala otra vez.');
          const allowed=['inspect_decks','inspect_collection','inspect_errors'];
          if(!allowed.includes(args.action)||state.commands.filter(c=>c.status==='pending').length>=20)throw Error('Invalid command or full queue');
          if(args.deckId!==undefined||args.confirmDelete!==undefined)throw Error('Unsupported command arguments');
          const cmd={id:random(),revision:s.revision,action:args.action,deckId:args.deckId||null,status:'pending',createdAt:new Date(clock()).toISOString()};
          persist({...state,commands:[...state.commands.slice(-99),cmd]});return toolResult({id:cmd.id,status:'pending',message:'Android debe recibir y procesar esta instrucción. No se ha ejecutado aún.'});
        }
        if(name==='collection_summary')return toolResult({revision:s.revision,syncedAt:s.syncedAt,distinct:s.cards.filter(c=>c.owned>0).length,copies:s.cards.reduce((n,c)=>n+c.owned,0),catalog:s.cards.length,decks:s.decks.map(({id,name})=>({id,name})),draft:s.draft?{id:'draft',name:s.draft.name}:null});
        if(name==='search_cards'){
          if(typeof(args.query??'')!=='string'||(args.query||'').length>120||!Number.isInteger(args.offset??0)||(args.offset??0)<0||!Number.isInteger(args.limit??50)||(args.limit??50)<1||(args.limit??50)>100||('ownedOnly'in args&&typeof args.ownedOnly!=='boolean'))throw Error('Invalid search');
          const q=(args.query||'').toLocaleLowerCase(),rows=s.cards.filter(c=>(!args.ownedOnly||c.owned>0)&&[c.id,c.name,c.expansion].some(v=>v.toLocaleLowerCase().includes(q)));
          return toolResult({revision:s.revision,syncedAt:s.syncedAt,total:rows.length,cards:rows.slice(args.offset??0,(args.offset??0)+(args.limit??50))});
        }
        if(name==='read_deck'){const d=args.id==='draft'?s.draft:s.decks.find(d=>d.id===args.id);if(!d)throw Error('Deck not found');return toolResult({revision:s.revision,syncedAt:s.syncedAt,deck:d});}
        if(name==='submit_deck_proposal'){
          if(args.revision!==s.revision)throw Error('La colección cambió. Consúltala otra vez.');
          if(typeof args.name!=='string'||!args.name.trim()||args.name.length>120||typeof args.strategy!=='string'||args.strategy.length>5000||!Array.isArray(args.cards)||args.cards.length>20||!Array.isArray(args.replacements)||args.replacements.length||!Array.isArray(args.energies)||args.energies.length<1||args.energies.length>3||new Set(args.energies).size!==args.energies.length||args.energies.some(e=>!tools.find(t=>t.name==='submit_deck_proposal').inputSchema.properties.energies.items.enum.includes(e)))throw Error('Invalid proposal');
          const seen=new Set(),names=new Map();let count=0;
          for(const row of args.cards){const card=s.cards.find(c=>c.id===row.id);if(!card||seen.has(row.id)||!Number.isInteger(row.count)||row.count<1||row.count>2||row.count>card.owned)throw Error('Invalid IDs or unavailable quantities');seen.add(row.id);count+=row.count;const key=card.rulesName||card.name;names.set(key,(names.get(key)||0)+row.count);}
          if(count!==20||[...names.values()].some(n=>n>2))throw Error('20 cartas; máximo dos copias por nombre.');
          const selected=args.cards.map(row=>s.cards.find(c=>c.id===row.id));
          if(!selected.some(c=>c.category==='pokemon'&&c.stage==='basic'))throw Error('Falta un Pokémon básico verificado.');
          for(const c of selected){
            if(!['pokemon','item','supporter','tool','Fossil','trainer'].includes(c.category))throw Error('Categoría sin verificar.');
            if(c.category==='pokemon'&&!['basic','1','2'].includes(c.stage))throw Error('Etapa sin verificar.');
            if(c.category==='pokemon'&&c.stage!=='basic'&&(!c.evolvesFrom||!selected.some(parent=>(parent.rulesName||parent.name).toLocaleLowerCase()===c.evolvesFrom.toLocaleLowerCase())))throw Error('Falta una preevolución verificada.');
          }
          if(state.proposals.length>=20)throw Error('Buzón lleno. Elimina la copia del servidor desde Android y vuelve a sincronizar para iniciar otra revisión.');
          const id=random();persist({...state,proposals:[...state.proposals,{id,revision:s.revision,createdAt:new Date(clock()).toISOString(),deck:{name:args.name,strategy:args.strategy,energies:args.energies,cards:args.cards,replacements:[]}}]});
          return toolResult({id,status:'pending_review',message:'Enviada. En Rotom AI TCG Pocket pulsa Recibir propuestas y revisa el mazo.'});
        }
      }catch(e){return result({isError:true,content:[{type:'text',text:e.message}]});}
    }catch{reply(400,{error:req.url?.startsWith('/authorize')?'La conexión no está registrada o sus parámetros no son válidos. Inicia una nueva conexión desde ChatGPT.':'Invalid request'});}
  });
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const server=createConnectorServer({deviceToken:process.env.POCKET_DEVICE_TOKEN,ownerSecret:process.env.POCKET_OWNER_SECRET,publicUrl:process.env.POCKET_PUBLIC_URL,stateFile:process.env.POCKET_STATE_FILE||'./data/pocket.json',legacyClientId:process.env.POCKET_LEGACY_CLIENT_ID,legacyRedirect:process.env.POCKET_LEGACY_REDIRECT});
  server.listen(Number(process.env.PORT||8080),process.env.HOST||'127.0.0.1');
}

