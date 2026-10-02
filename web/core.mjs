export const energies = ['Planta','Fuego','Agua','Rayo','Psíquico','Lucha','Oscuridad','Metal'];
export const rarities = ['♦','♦♦','♦♦♦','♦♦♦♦','★','★★','★★★','♛','✷','✷✷'];
const ensure = (ok, message) => { if (!ok) throw new Error(message); };
export function canonical(value) {
  const match = String(value).trim().toUpperCase().match(/^((?:[AB]\d+[A-Z]*|PROMO-[AB]))-(\d{1,3})$/);
  ensure(match && Number(match[2]) > 0, 'Código de carta no válido.');
  return match[1] + '-' + match[2].padStart(3,'0');
}
export function splitId(value) { const id = canonical(value); return [id.slice(0,id.lastIndexOf('-')),id.slice(-3)]; }
export function parseCsv(text) {
  ensure(typeof text === 'string' && text.length <= 8_000_000 && text.trim(), 'CSV vacío o demasiado grande.');
  text = text.replace(/^\uFEFF/,'');
  const header = text.split(/\r?\n/,1)[0];
  const delimiter = (header.match(/;/g)||[]).length > (header.match(/,/g)||[]).length ? ';' : ',';
  const rows = [], row = [];
  let field='', quoted=false, closed=false;
  const finishField=()=>{row.push(field);field='';closed=false;};
  const finishRow=()=>{finishField();if(row.some(v=>v.trim()))rows.push([...row]);row.length=0;};
  for(let i=0;i<text.length;i++){
    const ch=text[i];
    if(quoted){ if(ch==='"'){if(text[i+1]==='"'){field+='"';i++;}else{quoted=false;closed=true;}}else field+=ch; }
    else if(ch==='"'){ensure(!field&&!closed,'Comillas CSV no válidas.');quoted=true;}
    else if(ch===delimiter)finishField();
    else if(ch==='\n'||ch==='\r'){if(ch==='\r'&&text[i+1]==='\n')i++;finishRow();}
    else {ensure(!closed||/\s/.test(ch),'Contenido después de comillas CSV.');if(!closed)field+=ch;}
  }
  ensure(!quoted,'Comillas sin cerrar.');
  if(field||row.length||closed)finishRow();
  ensure(rows.length>1,'El CSV no contiene cartas.');
  const normalize=v=>v.trim().toLowerCase().normalize('NFD').replace(/\p{M}/gu,'');
  const names=rows.shift().map(normalize);
  const column=(...aliases)=>names.findIndex(v=>aliases.includes(v));
  const set=column('set','expansion','coleccion'), id=column('id','card_id','codigo','numero'),
    name=column('nombre','name'), rarity=column('rareza','rarity'), qty=column('cantidad','quantity','count','copias'),
    wish=column('deseos','wishlist','favorito','favorita');
  ensure([set,id,name,rarity,qty].every(v=>v>=0),'Faltan columnas: Set, ID, Nombre, Rareza y Cantidad.');
  const seen=new Set();
  return rows.map((r,i)=>{
    ensure(r.length===names.length,'Fila '+(i+2)+': campos incorrectos.');
    const code=canonical(r[set]+'-'+r[id]);
    ensure(!seen.has(code),'Carta duplicada: '+code);seen.add(code);
    const quantity=Number(r[qty].trim());
    ensure(/^\d+$/.test(r[qty].trim())&&Number.isSafeInteger(quantity)&&quantity<=2147483647,'Cantidad no válida.');
    ensure(r[name].trim()&&rarities.includes(r[rarity].trim()),'Nombre o rareza no válido.');
    let wishlist;
    if(wish>=0){const flag=normalize(r[wish]);ensure(['si','true','1','no','false','0',''].includes(flag),'Deseos no válido.');wishlist=['si','true','1'].includes(flag);}
    return {id:code,name:r[name].trim(),rarity:r[rarity].trim(),quantity,wishlist};
  });
}
export function exportCsv(cards) {
  const quote=v=>'"'+String(v).replaceAll('"','""')+'"';
  return [['Set','ID','Nombre','Rareza','Cantidad','Registrada','Deseos'],...cards.map(c=>{
    const [set,number]=splitId(c.id);
    return [set,Number(number),c.name,c.rarity,c.quantity,c.quantity>0?'sí':'no',c.wishlist?'sí':'no'];
  })].map(row=>row.map(quote).join(',')).join('\r\n');
}
export function deckReferences(text) {
  let refs;
  if(String(text).trim().startsWith('{')){
    const data=JSON.parse(text);ensure(data.format===2&&Array.isArray(data.cards),'Formato de mazo no compatible.');
    ensure(Array.isArray(data.energies)&&data.energies.length<=3&&new Set(data.energies).size===data.energies.length&&data.energies.every(v=>energies.includes(v)),'Energías no válidas.');
    refs=data.cards.map(c=>({id:canonical(c.id),count:c.count}));
  }else refs=String(text).split(';').filter(Boolean).map(v=>{const p=v.split(':');ensure(p.length===2,'Carta de mazo no válida.');return{id:canonical(p[0]),count:Number(p[1])};});
  ensure(refs.every(c=>Number.isInteger(c.count)&&c.count>=1&&c.count<=2)&&refs.reduce((s,c)=>s+c.count,0)<=20&&new Set(refs.map(c=>c.id)).size===refs.length,'Cantidades de mazo no válidas.');
  return refs;
}
export function validateBackup(text) {
  ensure(typeof text==='string'&&text.length<=8_000_000,'Respaldo demasiado grande.');
  const data=JSON.parse(text);
  ensure(data.format==='demeberant-tcg-pocket-backup'&&data.version===1&&Array.isArray(data.inventory)&&Array.isArray(data.decks),'Formato de respaldo no compatible.');
  const p=data.preferences;
  ensure(p&&['dark','blue','light','system'].includes(p.theme)&&(p.dark===undefined||typeof p.dark==='boolean')&&(p.language===undefined||['es','en','ja'].includes(p.language)),'Ajustes no válidos.');
  // Adapt Android theme-only backups to the existing web settings model.
  data.preferences={dark:p.dark??p.theme==='dark',language:p.language??'es',theme:p.theme==='system'?'dark':p.theme};
  const cards=data.inventory.map(c=>{
    ensure(typeof c.name==='string'&&c.name.trim()&&rarities.includes(c.rarity)&&typeof c.pack==='string'&&Number.isInteger(c.quantity)&&c.quantity>=0&&c.quantity<=2147483647&&typeof c.wishlist==='boolean'&&Number.isSafeInteger(c.acquiredAt),'Registro no válido.');
    return {...c,id:canonical(c.id)};
  });
  ensure(new Set(cards.map(c=>c.id)).size===cards.length,'Cartas duplicadas.');
  const decks=data.decks.map(d=>{
    ensure(typeof d.name==='string'&&d.name.trim()&&typeof d.archetype==='string'&&typeof d.strategy==='string'&&typeof d.cards==='string'&&Number.isSafeInteger(d.createdAt),'Mazo no válido.');
    const refs=deckReferences(d.cards);
    ensure(d.total===refs.reduce((s,c)=>s+c.count,0),'Total de mazo no válido.');
    return {...d};
  });
  return {...data,inventory:cards,decks};
}
export function mergeBackup(state, backup) {
  const inventory={...state.inventory};
  backup.inventory.forEach(c=>{inventory[c.id]={...c};});
  const decks=[...state.decks];
  const signature=d=>JSON.stringify([d.name,d.cards,d.strategy]);
  const seen=new Set(decks.map(signature));
  backup.decks.forEach(d=>{if(!seen.has(signature(d))){decks.push({...d});seen.add(signature(d));}});
  return {inventory,decks,preferences:{...backup.preferences}};
}
export function coverage(cards, targets=new Set()) {
  const groups=new Map();
  cards.filter(c=>!splitId(c.id)[0].startsWith('PROMO-')).forEach(c=>(c.packs||[]).forEach(pack=>{
    const set=splitId(c.id)[0],key=set+':'+pack;
    if(!groups.has(key))groups.set(key,{set,pack,missing:0,wishes:0,targets:[],score:0});
    if(!c.quantity){const g=groups.get(key);g.missing++;if(c.wishlist)g.wishes++;if(targets.has(c.id))g.targets.push(c.id);}
  }));
  return [...groups.values()].map(g=>({...g,score:g.missing+3*g.wishes+5*g.targets.length})).filter(g=>g.score>0).sort((a,b)=>b.score-a.score||a.set.localeCompare(b.set)||a.pack.localeCompare(b.pack));
}
export function cumulativeChance(p,n){ensure(Number.isFinite(p)&&p>=0&&p<=1&&Number.isInteger(n)&&n>=0,'Probabilidad no válida.');return 1-(1-p)**n;}
export function drawChance(deck,targets,draws){
  ensure(Number.isInteger(deck)&&deck>0&&deck<=100&&Number.isInteger(targets)&&targets>=0&&targets<=deck&&Number.isInteger(draws)&&draws>=0&&draws<=deck,'Robo no válido.');
  if(draws>deck-targets)return 1;
  let none=1;for(let i=0;i<draws;i++)none*=(deck-targets-i)/(deck-i);return 1-none;
}
