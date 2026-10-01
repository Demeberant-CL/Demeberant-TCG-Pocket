import {canonical,splitId,parseCsv,exportCsv,deckReferences,validateBackup,mergeBackup,coverage,cumulativeChance,drawChance,energies,rarities} from './core.mjs';

const $=id=>document.getElementById(id), app=$('app'), modal=$('dialog'), modalContent=$('dialog-content');
const KEY='demeberant-pocket-v1';
let catalog=[], section='Colección', filter='Todas', query='', expansion='', rarity='', page=0;
let draft={name:'Mi mazo',strategy:'',cards:[],energies:[]}, editing=-1, deckQuery='', onlyOwned=true;
let peer=null, reserve=2, fileMode='csv', storageBlocked=false, rawStorage='';
let state={inventory:{},decks:[],preferences:{dark:true,language:'es',theme:'dark'}};
const el=(tag,text,cls)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;};
const button=(label,action,cls)=>{const node=el('button',label,cls);node.type='button';node.addEventListener('click',()=>guard(action));return node;};
const text=(parent,message,cls)=>parent.append(el('p',message,cls));
const heading=(parent,title)=>parent.append(el('h1',title));
const panel=parent=>{const node=el('section',undefined,'panel');parent.append(node);return node;};
const toolbar=parent=>{const node=el('div',undefined,'toolbar');parent.append(node);return node;};
const notice=(message,error=false)=>{$('status').textContent=message;$('status').className=error?'error':'';};
async function guard(action){try{await action();}catch(error){notice(error.message||'No se pudo completar la operación.',true);}}
function commit(next){if(storageBlocked)throw new Error('El almacenamiento anterior no se pudo leer. Descarga una copia desde Ajustes antes de reemplazarlo.');localStorage.setItem(KEY,JSON.stringify(next));state=next;applyTheme();}
function applyTheme(){document.body.dataset.theme=state.preferences.theme;}
function openDialog(){modal.showModal();}
$('close-dialog').onclick=()=>modal.close();
function download(name,content,type='text/plain'){const url=URL.createObjectURL(new Blob([content],{type}));const a=el('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);}
function pick(mode){fileMode=mode;$('file').value='';$('file').accept=mode==='csv'||mode==='peer'?'.csv,text/csv':'.json,application/json';$('file').click();}
function cards(){const map=new Map(catalog.map(c=>[c.id,{...c,quantity:0,wishlist:false}]));
  Object.values(state.inventory).forEach(c=>{const known=map.get(c.id);map.set(c.id,{...(known||{id:c.id,name:c.name,rarity:c.rarity,packs:[],category:'unknown',stage:'unknown',evolvesFrom:''}),quantity:c.quantity,wishlist:c.wishlist});});
  return [...map.values()].sort((a,b)=>a.id.localeCompare(b.id));}
function allMap(){return new Map(cards().map(c=>[c.id,c]));}
function updateCard(card,changes){const next={...state,inventory:{...state.inventory}};
  next.inventory[card.id]={id:card.id,name:card.name,rarity:card.rarity,pack:state.inventory[card.id]?.pack||'',quantity:card.quantity,wishlist:card.wishlist,acquiredAt:state.inventory[card.id]?.acquiredAt||Date.now(),...changes};
  commit(next);render();}
function field(parent,label,value,onChange,type='text'){const wrap=el('label',label),input=el('input');input.type=type;input.setAttribute('aria-label',label);input.value=value;input.addEventListener('input',()=>onChange(input.value));wrap.append(input);parent.append(wrap);return input;}
function select(parent,label,options,current,onChange){const wrap=el('label',label),node=el('select');node.setAttribute('aria-label',label);options.forEach(([value,name])=>{const option=el('option',name);option.value=value;node.append(option);});node.value=current;node.onchange=()=>guard(()=>onChange(node.value));wrap.append(node);parent.append(wrap);return node;}
function imageUrl(id,lang='es',high=false){let [set,num]=splitId(id);set=set.replace('PROMO-','P-').replace(/^([AB]\d+)([A-Z]+)$/,(_,a,b)=>a+b.toLowerCase());return 'https://assets.tcgdex.net/'+lang+'/tcgp/'+set+'/'+num+'/'+(high?'high':'low')+'.webp';}
function image(card,high=false){const node=el('img');node.alt=card.name;node.loading='lazy';node.src=imageUrl(card.id,state.preferences.language,high);let retried=false;node.onerror=()=>{if(!retried&&state.preferences.language!=='en'){retried=true;node.src=imageUrl(card.id,'en',high);}else{node.remove();}};return node;}
async function cardDialog(card){
  modalContent.replaceChildren();heading(modalContent,card.name);modalContent.append(image(card,true));text(modalContent,card.id+' · '+card.rarity+' · '+(card.packs.join(', ')||'Sin datos de sobre'));
  const input=field(modalContent,'Copias en mi colección',card.quantity,()=>{},'number');input.min='0';input.max='99999';input.step='1';
  const row=toolbar(modalContent);
  row.append(button('Guardar cantidad',()=>{const q=Number(input.value);if(!input.value||!Number.isInteger(q)||q<0||q>99999)throw new Error('Cantidad no válida.');updateCard(card,{quantity:q});modal.close();notice('Cantidad guardada.');},'primary'));
  const wish=button(card.wishlist?'Quitar de Deseos':'Añadir a Deseos',()=>{updateCard(card,{wishlist:!card.wishlist});card.wishlist=!card.wishlist;wish.textContent=card.wishlist?'Quitar de Deseos':'Añadir a Deseos';});row.append(wish);
  const details=el('div');modalContent.append(details);text(details,'Consultando detalles de TCGdex…','muted');openDialog();
  try {
    const [set,num]=splitId(card.id),apiSet=set.replace('PROMO-','P-').replace(/^([AB]\d+)([A-Z]+)$/,(_,a,b)=>a+b.toLowerCase()),apiId=apiSet+'-'+num;
    const response=await fetch('https://api.tcgdex.net/v2/'+state.preferences.language+'/cards/'+apiId,{signal:AbortSignal.timeout(8000)});
    if(!response.ok)throw new Error('Sin detalles');
    const data=await response.json();if(data.id.toLowerCase()!==apiId.toLowerCase())throw new Error('Respuesta incorrecta');
    details.replaceChildren();text(details,'Fuente: TCGdex','muted');
    if(data.hp!==undefined)text(details,'PS: '+data.hp);text(details,[data.category,data.stage].filter(Boolean).join(' · '));
    if(data.retreat!==undefined)text(details,'Retirada: '+data.retreat);if(data.effect)text(details,data.effect);
    (data.attacks||[]).forEach(a=>text(details,[a.name,a.damage,a.effect].filter(Boolean).join(' · ')));
  }catch{details.replaceChildren();text(details,'Detalles no disponibles. La colección se puede editar sin conexión.','muted');}
}
function renderCollection(){
  const all=cards(),owned=all.filter(c=>c.quantity>0).length,copies=all.reduce((s,c)=>s+c.quantity,0);
  heading(app,'Mi colección');text(app,owned+' de '+all.length+' cartas · '+copies+' copias · '+Math.round(100*owned/(all.length||1))+' %');
  const progress=el('progress');progress.max=all.length||1;progress.value=owned;progress.setAttribute('aria-label','Progreso de colección');app.append(progress);
  const tools=toolbar(app);tools.append(button('Importar CSV',()=>pick('csv')),button('Exportar CSV',()=>download('coleccion-pokemon.csv',exportCsv(all),'text/csv')));
  const input=field(app,'Buscar nombre, expansión o número',query,value=>{query=value;page=0;renderCollectionResults(results);},'search');
  const filters=toolbar(app);
  ['Todas','Tengo','Faltan','Deseos','Ver repetidas'].forEach(name=>{const b=button(name,()=>{filter=name;page=0;render();});b.setAttribute('aria-pressed',String(filter===name));filters.append(b);});
  const advanced=toolbar(app);
  select(advanced,'Expansión',[['','Todas las expansiones'],...[...new Set(all.map(c=>splitId(c.id)[0]))].sort().map(s=>[s,s])],expansion,value=>{expansion=value;page=0;render();});
  select(advanced,'Rareza',[['','Todas las rarezas'],...rarities.map(v=>[v,v])],rarity,value=>{rarity=value;page=0;render();});
  const results=el('div');app.append(results);renderCollectionResults(results);input.id='collection-search';
}
function renderCollectionResults(parent){
  parent.replaceChildren();
  const filtered=cards().filter(c=>(!query||c.name.toLowerCase().includes(query.toLowerCase())||c.id.toLowerCase().includes(query.toLowerCase())||splitId(c.id)[0].toLowerCase()===query.toLowerCase())
    &&(!expansion||splitId(c.id)[0]===expansion)&&(!rarity||c.rarity===rarity)
    &&(filter==='Todas'||filter==='Tengo'&&c.quantity>0||filter==='Faltan'&&!c.quantity||filter==='Deseos'&&c.wishlist||filter==='Ver repetidas'&&c.quantity>1));
  text(parent,filtered.length+' coincidencias','muted');const grid=el('div',undefined,'grid');parent.append(grid);
  filtered.slice(page*80,(page+1)*80).forEach(card=>{const tile=button('',()=>cardDialog({...card}),'tile'+(!card.quantity?' missing':''));tile.setAttribute('aria-label',card.name+' '+card.id+' '+card.quantity+' copias');tile.append(image(card),el('strong',card.name),el('small',card.id+' · '+card.rarity),el('span','x'+card.quantity+(card.wishlist?' · ♥ Deseos':'')));grid.append(tile);});
  if(!filtered.length)text(parent,'No se encontraron cartas que coincidan.');
  const pages=Math.max(1,Math.ceil(filtered.length/80)),nav=toolbar(parent);
  const previous=button('Anterior',()=>{page--;renderCollectionResults(parent);});previous.disabled=page===0;
  const next=button('Siguiente',()=>{page++;renderCollectionResults(parent);});next.disabled=page+1>=pages;
  nav.append(previous,el('span',(page+1)+' / '+pages),next);
}
function editCount(id,count){
  const map=allMap(),card=map.get(id);if(!card)throw new Error('Carta no disponible.');
  if(!Number.isInteger(count)||count<0||count>2)return;
  const next=draft.cards.filter(c=>c.id!==id);if(count)next.push({id,count});
  const total=next.reduce((s,c)=>s+c.count,0),names=new Map();
  next.forEach(c=>{const name=map.get(c.id)?.name.toLowerCase()||c.id;names.set(name,(names.get(name)||0)+c.count);});
  if(total>20||[...names.values()].some(v=>v>2))throw new Error('Máximo de 20 cartas y dos copias por nombre.');
  draft={...draft,cards:next};render();
}
function deckWarnings(){
  const map=allMap(),total=draft.cards.reduce((s,c)=>s+c.count,0),names=new Set(draft.cards.map(c=>map.get(c.id)?.name.toLowerCase()));
  const result=[];if(total!==20)result.push('Borrador: '+total+'/20 cartas.');
  if(!draft.cards.some(c=>map.get(c.id)?.stage==='basic'))result.push('Falta un Pokémon básico con datos disponibles.');
  draft.cards.forEach(c=>{const card=map.get(c.id);if(!card||card.category==='unknown')result.push('Datos sin verificar: '+c.id);else if(card.evolvesFrom&&!names.has(card.evolvesFrom.toLowerCase()))result.push(card.name+' necesita '+card.evolvesFrom+'.');});
  return [...new Set(result)];
}
function deckText(){const map=allMap();return draft.name+'\nEnergías: '+draft.energies.join(', ')+'\n'+draft.cards.map(c=>c.count+'x '+(map.get(c.id)?.name||c.id)+' ('+c.id+')').join('\n')+'\n'+draft.strategy;}
function renderDecks(){
  heading(app,'Mazos');text(app,'Editor manual para todas las expansiones. Puedes guardar borradores y planificar cartas que todavía no tienes.');
  const tools=toolbar(app);tools.append(button('Nuevo mazo',()=>{if(draft.cards.length&&!confirm('¿Crear un mazo nuevo? Los cambios sin guardar se perderán.'))return;draft={name:'Mi mazo',strategy:'',cards:[],energies:[]};editing=-1;render();}),
    button('Guardar',()=>{if(!draft.name.trim()||!draft.cards.length)throw new Error('Añade un nombre y cartas.');
      const record={name:draft.name.trim(),archetype:'Manual',strategy:draft.strategy,cards:JSON.stringify({format:2,cards:draft.cards,energies:draft.energies}),total:draft.cards.reduce((s,c)=>s+c.count,0),createdAt:editing>=0?state.decks[editing].createdAt:Date.now()};
      const decks=[...state.decks];if(editing>=0)decks[editing]=record;else{editing=decks.length;decks.push(record);}commit({...state,decks});notice('Mazo guardado.');render();
    },'primary'),button('Exportar lista',()=>download('mazo.txt',deckText())));
  field(app,'Nombre del mazo',draft.name,value=>{draft.name=value;});
  const notes=el('label','Notas de estrategia'),textarea=el('textarea');textarea.value=draft.strategy;textarea.oninput=()=>{draft.strategy=textarea.value;};notes.append(textarea);app.append(notes);
  const energy=toolbar(app);energies.forEach(name=>{const b=button(name,()=>{const next=draft.energies.includes(name)?draft.energies.filter(v=>v!==name):[...draft.energies,name];if(next.length>3)throw new Error('Máximo de tres energías.');draft.energies=next;render();});b.setAttribute('aria-pressed',String(draft.energies.includes(name)));energy.append(b);});
  text(app,'Energías · máximo tres','muted');deckWarnings().forEach(w=>text(app,w,'error'));
  const map=allMap();
  draft.cards.forEach(c=>{const card=map.get(c.id),row=el('div',undefined,'entry');row.append(el('span',(card?.name||c.id)+' · '+c.id+' · x'+c.count+' · '+Math.max(0,c.count-(card?.quantity||0))+' por conseguir'),button('−',()=>editCount(c.id,c.count-1)),button('+',()=>editCount(c.id,c.count+1)));app.append(row);});
  const candidates=el('div');
  field(app,'Buscar para añadir',deckQuery,value=>{deckQuery=value;renderCandidates(candidates);},'search');
  const toggle=button(onlyOwned?'Solo las que tengo':'Todas las cartas',()=>{onlyOwned=!onlyOwned;render();});app.append(toggle,candidates);renderCandidates(candidates);
  const saved=panel(app);saved.append(el('h2','Mis mazos'));
  if(!state.decks.length)text(saved,'Aún no tienes mazos guardados.');
  state.decks.forEach((d,index)=>{const row=el('div',undefined,'entry');row.append(el('span',d.name+' · '+d.total+'/20'),button('Editar',()=>{
    if(draft.cards.length&&editing!==index&&!confirm('¿Abrir este mazo? Guarda antes los cambios del editor.'))return;
    const refs=deckReferences(d.cards),data=d.cards.trim().startsWith('{')?JSON.parse(d.cards):{energies:[]};
    draft={name:d.name,strategy:d.strategy,cards:refs,energies:data.energies};editing=index;render();
  }));saved.append(row);});
}
function renderCandidates(parent){parent.replaceChildren();const candidates=cards().filter(c=>(!onlyOwned||c.quantity>0)&&(!deckQuery||c.name.toLowerCase().includes(deckQuery.toLowerCase())||c.id.toLowerCase().includes(deckQuery.toLowerCase()))).slice(0,40);
  text(parent,'Hasta 40 coincidencias. Afina la búsqueda para encontrar otra carta.','muted');
  candidates.forEach(c=>{const row=el('div',undefined,'entry'),count=draft.cards.find(d=>d.id===c.id)?.count||0;
    row.append(el('span',c.name+' · '+c.id+' · '+c.quantity+' copias'),button('Añadir',()=>editCount(c.id,count+1)));parent.append(row);});}
function renderTrades(){
  heading(app,'Canjes');text(app,'Prepara propuestas sin descontar ninguna carta. Comprueba en el juego la elegibilidad de cada carta y el coste de canje.');
  select(app,'Copias que quieres conservar',[['1','Una copia'],['2','Dos copias']],String(reserve),v=>{reserve=Number(v);render();});
  const mine=cards(),offering=mine.filter(c=>c.quantity>reserve),wishes=mine.filter(c=>c.wishlist),tools=toolbar(app);
  tools.append(button('Copiar lista de ofertas',async()=>{await navigator.clipboard.writeText(offering.map(c=>c.id+' · '+c.name+' x'+(c.quantity-reserve)).join('\n'));notice('Lista copiada.');}),
    button('Comparar otro CSV',()=>pick('peer')));
  if(peer){const theirs=new Map(peer.map(c=>[c.id,c])),give=offering.filter(c=>theirs.get(c.id)?.quantity===0),receive=mine.filter(c=>!c.quantity&&(theirs.get(c.id)?.quantity||0)>reserve);
    const proposals=panel(app);proposals.append(el('h2','Propuestas por rareza'));
    let found=0;
    rarities.forEach(r=>{const out=give.filter(c=>c.rarity===r),incoming=receive.filter(c=>c.rarity===r);
      if(out.length&&incoming.length){found++;text(proposals,'Rareza '+r);text(proposals,'Puedes ofrecer: '+out.slice(0,15).map(c=>c.id+' '+c.name).join(', '));text(proposals,'Puedes pedir: '+incoming.slice(0,15).map(c=>c.id+' '+c.name).join(', '));}});
    if(!found)text(proposals,'No hay propuestas recíprocas con la misma rareza y esta reserva.');
    text(proposals,'Se requiere un cero explícito para afirmar que al otro usuario le falta una carta. Se comparan cantidades del CSV, sin ejecutar canjes. El CSV de la otra persona no se guarda en tu colección.','muted');
  }
  const list=panel(app);list.append(el('h2','Para ofrecer'));if(!offering.length)text(list,'No hay copias sobrantes.');
  offering.slice(0,150).forEach(c=>text(list,c.id+' · '+c.name+' · '+(c.quantity-reserve)+' disponibles'));
  if(offering.length>150)text(list,'La copia de la lista incluye las '+offering.length+' cartas.');
  const wanted=panel(app);wanted.append(el('h2','Deseos'));if(!wishes.length)text(wanted,'Marca cartas en Deseos desde Colección.');wishes.slice(0,150).forEach(c=>text(wanted,c.id+' · '+c.name));
}
function renderAnalysis(){
  heading(app,'Análisis');text(app,'Cobertura de sobres del catálogo comunitario. No se muestran tasas de apertura sin verificar ni clasificaciones de meta inventadas.');
  const packs=panel(app);packs.append(el('h2','Sobres para completar mi colección'));
  const recommendations=coverage(cards());recommendations.forEach(p=>text(packs,p.set+' · '+p.pack+' · '+p.missing+' faltantes · '+p.wishes+' deseos · '+p.score+' puntos'));
  text(packs,'Puntuación: 1 por carta faltante y 3 adicionales por Deseo. Las promociones se excluyen. Mide cobertura, no probabilidades.','muted');
  const math=panel(app);math.append(el('h2','Calculadora de apertura'));
  const result=el('p'),rate=field(math,'Probabilidad total por sobre (%)','',updateMath,'number'),count=field(math,'Número de sobres',10,updateMath,'number');rate.min='0';rate.max='100';rate.step='any';count.min='0';count.max='100000';count.step='1';math.append(result);
  function updateMath(){const p=Number(rate.value),n=Number(count.value);if(!rate.value){result.textContent='Introduce la tasa total por sobre que muestra el juego.';return;}try{result.textContent='Al menos una: '+(100*cumulativeChance(p/100,n)).toFixed(2)+' %';}catch{result.textContent='Introduce valores válidos.';}}updateMath();
  text(math,'Aperturas independientes con tasa constante. No uses una tasa de una sola ranura. No garantiza resultados.','muted');
  const draw=panel(app);draw.append(el('h2','Robo aleatorio de un mazo de 20'));
  const drawResult=el('p'),targets=field(draw,'Copias objetivo',2,updateDraw,'number'),draws=field(draw,'Cartas robadas',5,updateDraw,'number');
  [targets,draws].forEach(input=>{input.min='0';input.max='20';input.step='1';});draw.append(drawResult);
  function updateDraw(){try{drawResult.textContent='Al menos una: '+(100*drawChance(20,Number(targets.value),Number(draws.value))).toFixed(2)+' %';}catch{drawResult.textContent='Introduce valores válidos.';}}updateDraw();
  text(draw,'No simula la garantía de Pokémon básico en la mano inicial ni efectos de cartas.','muted');
  const link=el('a','Consultar torneos actuales en Limitless');link.href='https://play.limitlesstcg.com/tournaments?game=POCKET';link.target='_blank';link.rel='noopener noreferrer';app.append(link);
}
function backup(){return {format:'demeberant-tcg-pocket-backup',version:1,createdAt:Date.now(),inventory:Object.values(state.inventory),decks:state.decks,preferences:state.preferences};}
function settings(){
  modalContent.replaceChildren();heading(modalContent,'Ajustes');
  const tools=toolbar(modalContent);tools.append(button('Guardar respaldo completo',()=>download('respaldo-tcg-pocket.json',JSON.stringify(backup(),null,2),'application/json')),button('Restaurar respaldo completo',()=>{modal.close();pick('backup');}));
  text(modalContent,'El respaldo JSON incluye colección, Deseos, mazos y ajustes. Es compatible con Android. El CSV contiene cantidades y Deseos.');
  select(modalContent,'Tema',[['dark','Oscuro carbón'],['blue','Azul Pokémon'],['light','Claro']],state.preferences.theme,value=>{commit({...state,preferences:{...state.preferences,theme:value,dark:value==='dark'}});render();});
  select(modalContent,'Idioma de imágenes',[['es','Español'],['en','English'],['ja','日本語']],state.preferences.language,value=>{commit({...state,preferences:{...state.preferences,language:value}});render();});
  text(modalContent,'Los datos de esta web se guardan en este navegador. No se sincronizan automáticamente con Android. Usa un respaldo para trasladarlos.');
  text(modalContent,'Catálogo comunitario: 4317 cartas, 23 expansiones · revisión del 01-10-2026. Fuente flibustier/pokemon-tcg-pocket-database (MIT). Los detalles e imágenes proceden de TCGdex.');
  const source=el('a','Ver fuentes y licencias');source.href='https://github.com/Demeberant-CL/Demeberant-TCG-Pocket/blob/fix/collection-decks-validation-20261001/THIRD_PARTY_NOTICES.md';source.target='_blank';source.rel='noopener noreferrer';modalContent.append(source);
  if(storageBlocked){text(modalContent,'El almacenamiento anterior no se pudo leer. La edición está bloqueada para evitar sobrescribirlo.','error');modalContent.append(button('Descargar copia de recuperación',()=>download('recuperacion-pocket.txt',rawStorage)));
    modalContent.append(button('Reemplazar almacenamiento dañado',()=>{if(!confirm('Esto reemplazará los datos de esta web. Descarga antes la copia de recuperación.'))return;localStorage.setItem(KEY,JSON.stringify(state));storageBlocked=false;modal.close();render();notice('Almacenamiento reiniciado.');}));}
  openDialog();
}
$('settings').onclick=()=>guard(settings);
$('file').onchange=()=>guard(async()=>{
  const file=$('file').files[0];if(!file)return;if(file.size>8_000_000)throw new Error('Archivo demasiado grande.');
  const content=await file.text();
  if(fileMode==='peer'){peer=parseCsv(content);section='Canjes';render();notice('CSV de comparación leído. Tu colección no cambió.');return;}
  if(fileMode==='backup'){
    const data=validateBackup(content);
    if(!confirm('Restaurar '+data.inventory.length+' registros y '+data.decks.length+' mazos. Reemplaza cantidades y Deseos incluidos; conserva las cartas ausentes y los mazos existentes. También aplica los ajustes.'))return;
    commit(mergeBackup(state,data));render();notice('Respaldo restaurado.');return;
  }
  const records=parseCsv(content),next={...state,inventory:{...state.inventory}};
  records.forEach(c=>{const old=state.inventory[c.id];next.inventory[c.id]={id:c.id,name:c.name,rarity:c.rarity,pack:old?.pack||'',quantity:c.quantity,wishlist:c.wishlist??old?.wishlist??false,acquiredAt:old?.acquiredAt||Date.now()};});
  commit(next);page=0;render();notice('Importación exitosa: '+records.length+' cartas. Los registros ausentes se conservaron.');
});
function render(){
  app.replaceChildren();$('nav').replaceChildren();
  ['Colección','Mazos','Canjes','Análisis'].forEach(name=>{const b=button(name,()=>{section=name;render();});b.classList.toggle('selected',section===name);b.setAttribute('aria-current',section===name?'page':'false');$('nav').append(b);});
  if(section==='Colección')renderCollection();else if(section==='Mazos')renderDecks();else if(section==='Canjes')renderTrades();else renderAnalysis();
}
try{
  rawStorage=localStorage.getItem(KEY)||'';
  if(rawStorage){const saved=JSON.parse(rawStorage);const checked=validateBackup(JSON.stringify({format:'demeberant-tcg-pocket-backup',version:1,inventory:Object.values(saved.inventory),decks:saved.decks,preferences:saved.preferences}));state={inventory:Object.fromEntries(checked.inventory.map(c=>[c.id,c])),decks:checked.decks,preferences:checked.preferences};}
}catch(error){storageBlocked=true;notice('No se pudo leer el almacenamiento. Abre Ajustes para recuperar una copia.',true);}
applyTheme();
async function loadCatalog(){
try{const response=await fetch(import.meta.env.BASE_URL+'pocket-catalog.json');if(!response.ok)throw new Error('Catálogo no disponible');const data=await response.json();catalog=data.cards;render();}
catch(error){app.replaceChildren();heading(app,'Catálogo no disponible');text(app,'Vuelve a cargar la página. Tus datos guardados se conservan.');notice(error.message,true);}

}
loadCatalog();
