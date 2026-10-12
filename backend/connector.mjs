import {createServer} from 'node:http';
import {randomBytes, timingSafeEqual, createHash} from 'node:crypto';
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
  {name:'submit_deck_proposal',description:'Envía un mazo de 20 cartas para revisión en Pocket Atlas. No modifica la colección ni guarda o reemplaza mazos. Usa IDs y revisión de la colección consultada. Máximo dos copias por nombre y líneas evolutivas completas. replacements debe estar vacío.',inputSchema:object({revision:text,name:{type:'string',maxLength:120},strategy:text,energies:{type:'array',minItems:1,maxItems:3,items:{type:'string',enum:['Planta','Fuego','Agua','Rayo','Psíquico','Lucha','Oscuridad','Metal']}},cards:{type:'array',minItems:10,maxItems:20,items:object({id:{type:'string'},count:{type:'integer',minimum:1,maximum:2}})},replacements:{type:'array',maxItems:0}})}
].map(t=>({...t,securitySchemes:[{type:'oauth2',scopes:[t.name==='submit_deck_proposal'?'pocket:propose':'pocket:read']}],_meta:{securitySchemes:[{type:'oauth2',scopes:[t.name==='submit_deck_proposal'?'pocket:propose':'pocket:read']}]},annotations:{readOnlyHint:t.name!=='submit_deck_proposal',destructiveHint:false,idempotentHint:t.name!=='submit_deck_proposal',openWorldHint:false}}));
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
export function createConnectorServer({deviceToken,ownerSecret,publicUrl,stateFile,clock=Date.now}={}){
  if(!deviceToken||deviceToken.length<32||!ownerSecret||ownerSecret.length<32||same(deviceToken,ownerSecret))throw Error('Two different secrets of at least 32 characters required');
  const origin=new URL(publicUrl);if(origin.protocol!=='https:'||origin.pathname!=='/'||origin.search||origin.hash||origin.username||origin.password)throw Error('Public HTTPS origin required');
  const base=origin.origin;
  let state={snapshot:null,proposals:[]};
  if(stateFile){try{state=JSON.parse(readFileSync(stateFile,'utf8'));if(state.snapshot)validateSnapshot(state.snapshot);if(!Array.isArray(state.proposals)||state.proposals.length>20)throw Error('Invalid state');}catch(e){if(e.code!=='ENOENT')throw e;}}
  const clients=new Map(),codes=new Map(),tokens=new Map(),rates=new Map();
  const persist=(next)=>{if(stateFile){mkdirSync(dirname(stateFile),{recursive:true,mode:0o700});writeFileSync(stateFile+'.tmp',JSON.stringify(next),{mode:0o600});renameSync(stateFile+'.tmp',stateFile);}state=next;};
  const prune=()=>{for(const map of [clients,codes,tokens,rates])for(const [k,v]of map)if(v.expires<clock())map.delete(k);};
  const metadata={issuer:base,authorization_endpoint:base+'/authorize',token_endpoint:base+'/token',registration_endpoint:base+'/register',response_types_supported:['code'],grant_types_supported:['authorization_code','refresh_token'],token_endpoint_auth_methods_supported:['none'],code_challenge_methods_supported:['S256'],scopes_supported:['pocket:read','pocket:propose']};
  return createServer(async(req,res)=>{
    const reply=(code,value,extra={})=>{res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store',...extra});res.end(value===undefined?'':JSON.stringify(value));};
    try{
      prune();const url=new URL(req.url,base),path=url.pathname;
      if(req.headers.origin&&req.headers.origin!==base)return reply(403,{error:'Invalid origin'});
      if(req.method==='GET'&&path==='/health')return reply(200,{ok:true});
      if(req.method==='GET'&&['/.well-known/oauth-protected-resource','/.well-known/oauth-protected-resource/mcp'].includes(path))return reply(200,{resource:base+'/mcp',authorization_servers:[base],scopes_supported:metadata.scopes_supported});
      if(req.method==='GET'&&['/.well-known/oauth-authorization-server','/.well-known/openid-configuration'].includes(path))return reply(200,metadata);
      // Bound authentication attempts and total owner-scoped load. Do not trust forwarding headers.
      const ip=req.socket.remoteAddress||'local',bucket=rates.get(ip)||{count:0,expires:clock()+60000};
      if(rates.size>1000&&!rates.has(ip))return reply(429,{error:'Try later'});
      rates.set(ip,bucket);if(++bucket.count>120)return reply(429,{error:'Try later'});
      const body=async()=>{const chunks=[];let size=0;for await(const c of req){size+=c.length;if(size>8000000)throw Error('Body too large');chunks.push(c);}return Buffer.concat(chunks).toString('utf8');};
      const authorization=(params)=>{const client=clients.get(params.get('client_id'));if(!client||params.get('response_type')!=='code'||!client.redirects.includes(params.get('redirect_uri'))||params.get('code_challenge_method')!=='S256'||! /^[A-Za-z0-9_-]{43}$/.test(params.get('code_challenge')||'')||params.get('resource')!==base+'/mcp')throw Error('Invalid authorization');const scope=(params.get('scope')||'pocket:read pocket:propose').split(' ');if(scope.some(s=>!metadata.scopes_supported.includes(s)))throw Error('Invalid scope');return {client:params.get('client_id'),redirect:params.get('redirect_uri'),challenge:params.get('code_challenge'),resource:params.get('resource'),scope:scope.join(' ')};};
      if(path==='/register'&&req.method==='POST'){
        if(clients.size>=100)return reply(429,{error:'Try later'});
        const input=JSON.parse(await body());if(!Array.isArray(input.redirect_uris)||input.redirect_uris.length<1||input.redirect_uris.length>5||input.redirect_uris.some(r=>{try{const u=new URL(r);return r.length>2000||u.protocol!=='https:'||u.username||u.password||u.hash;}catch{return true;}}))return reply(400,{error:'invalid_redirect_uri'});
        const id=random();clients.set(id,{redirects:input.redirect_uris,expires:clock()+86400000});return reply(201,{client_id:id,redirect_uris:input.redirect_uris,token_endpoint_auth_method:'none',grant_types:metadata.grant_types_supported,response_types:['code']});
      }
      if(path==='/authorize'&&req.method==='GET'){
        authorization(url.searchParams);
        // Fixed HTML: untrusted query data is retained in the URL, never interpolated into HTML.
        res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'none'; form-action 'self'; frame-ancestors 'none'",'X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'});
        return res.end('<!doctype html><html lang="es"><meta name="viewport" content="width=device-width"><title>Conectar Pocket Atlas</title><h1>Conectar Pocket Atlas</h1><p>Permite consultar tu colección sincronizada y enviar propuestas pendientes de revisión. No permite modificar tus cartas ni guardar mazos.</p><form method="post"><label>Clave del propietario <input name="secret" type="password" required autocomplete="off"></label><button>Autorizar conexión</button></form></html>');
      }
      if(path==='/authorize'&&req.method==='POST'){
        const auth=authorization(url.searchParams),input=new URLSearchParams(await body());if(!same(input.get('secret'),ownerSecret))return reply(401,{error:'Unauthorized'});
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
          persist({snapshot,proposals:[]});return reply(200,{revision:snapshot.revision,syncedAt:snapshot.syncedAt});
        }
        if(path==='/app/proposals'&&req.method==='GET')return reply(200,{revision:state.snapshot?.revision,proposals:state.proposals});
        if(path==='/app/data'&&req.method==='DELETE'){persist({snapshot:null,proposals:[]});return reply(200,{deleted:true});}
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
      if(rpc.method==='initialize')return result({protocolVersion:['2025-03-26','2025-06-18','2025-11-25'].includes(rpc.params?.protocolVersion)?rpc.params.protocolVersion:'2025-11-25',capabilities:{tools:{}},serverInfo:{name:'Pocket Atlas',version:'1.0.0'},instructions:'Los datos son una copia sincronizada del teléfono. Comprueba syncedAt. Las propuestas necesitan revisión y validación Android; no se aplican automáticamente.'});
      if(rpc.method==='ping')return result({});
      if(rpc.method==='tools/list')return result({tools});
      if(rpc.method!=='tools/call')return reply(200,{jsonrpc:'2.0',id:rpc.id,error:{code:-32601,message:'Method not found'}});
      const name=rpc.params?.name,args=rpc.params?.arguments||{},s=state.snapshot;
      const toolResult=value=>result({content:[{type:'text',text:JSON.stringify(value)}]});
      try{
        if(!tools.some(t=>t.name===name))throw Error('Unknown tool');
        if(!s)throw Error('Sin colección: sincroniza desde Pocket Atlas.');
        const permission=name==='submit_deck_proposal'?'pocket:propose':'pocket:read';if(!grant.scope.split(' ').includes(permission))throw Error('Insufficient scope');
        if(name==='collection_summary')return toolResult({revision:s.revision,syncedAt:s.syncedAt,distinct:s.cards.filter(c=>c.owned>0).length,copies:s.cards.reduce((n,c)=>n+c.owned,0),catalog:s.cards.length,decks:s.decks.map(({id,name})=>({id,name})),draft:s.draft?{id:'draft',name:s.draft.name}:null});
        if(name==='search_cards'){
          if(typeof(args.query??'')!=='string'||(args.query||'').length>120||!Number.isInteger(args.offset??0)||(args.offset??0)<0||!Number.isInteger(args.limit??50)||(args.limit??50)<1||(args.limit??50)>100||('ownedOnly'in args&&typeof args.ownedOnly!=='boolean'))throw Error('Invalid search');
          const q=(args.query||'').toLocaleLowerCase(),rows=s.cards.filter(c=>(!args.ownedOnly||c.owned>0)&&[c.id,c.name,c.expansion].some(v=>v.toLocaleLowerCase().includes(q)));
          return toolResult({revision:s.revision,syncedAt:s.syncedAt,total:rows.length,cards:rows.slice(args.offset??0,(args.offset??0)+(args.limit??50))});
        }
        if(name==='read_deck'){const d=args.id==='draft'?s.draft:s.decks.find(d=>d.id===args.id);if(!d)throw Error('Deck not found');return toolResult({revision:s.revision,syncedAt:s.syncedAt,deck:d});}
        if(name==='submit_deck_proposal'){
          if(args.revision!==s.revision)throw Error('La colección cambió. Consúltala otra vez.');
          if(typeof args.name!=='string'||!args.name.trim()||args.name.length>120||typeof args.strategy!=='string'||args.strategy.length>5000||!Array.isArray(args.cards)||args.cards.length>20||!Array.isArray(args.replacements)||args.replacements.length||!Array.isArray(args.energies)||args.energies.length<1||args.energies.length>3||new Set(args.energies).size!==args.energies.length||args.energies.some(e=>!tools[3].inputSchema.properties.energies.items.enum.includes(e)))throw Error('Invalid proposal');
          const seen=new Set(),names=new Map();let count=0;
          for(const row of args.cards){const card=s.cards.find(c=>c.id===row.id);if(!card||seen.has(row.id)||!Number.isInteger(row.count)||row.count<1||row.count>2||row.count>card.owned)throw Error('Invalid IDs or unavailable quantities');seen.add(row.id);count+=row.count;const key=card.rulesName||card.name;names.set(key,(names.get(key)||0)+row.count);}
          if(count!==20||[...names.values()].some(n=>n>2))throw Error('20 cartas; máximo dos copias por nombre.');
          if(state.proposals.length>=20)throw Error('Buzón lleno. Sincroniza para iniciar otra revisión.');
          const id=random();persist({...state,proposals:[...state.proposals,{id,revision:s.revision,createdAt:new Date(clock()).toISOString(),deck:{name:args.name,strategy:args.strategy,energies:args.energies,cards:args.cards,replacements:[]}}]});
          return toolResult({id,status:'pending_review',message:'Enviada. En Pocket Atlas pulsa Recibir propuestas y revisa el mazo.'});
        }
      }catch(e){return result({isError:true,content:[{type:'text',text:e.message}]});}
    }catch{reply(400,{error:'Invalid request'});}
  });
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const server=createConnectorServer({deviceToken:process.env.POCKET_DEVICE_TOKEN,ownerSecret:process.env.POCKET_OWNER_SECRET,publicUrl:process.env.POCKET_PUBLIC_URL,stateFile:process.env.POCKET_STATE_FILE||'./data/pocket.json'});
  server.listen(Number(process.env.PORT||8080),process.env.HOST||'127.0.0.1');
}
