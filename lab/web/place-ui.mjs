import {BUILDING_ACTIONS,placeBuildings,placeUnits,homeFor,validHome,buildingCare,currentBuildingCare} from './places.mjs';

export function createPlaceUi({$,esc,time,store,identity,snapshot,saves,changed,selectResident}){
 let selected=null,editing=null,editingUnit=null,context='',currentGroup='';
 const key=(kind,target='')=>{const s=snapshot();return `place:${s.group}:${kind==='CARE'?s.incidentId:''}:${kind}:${target}`;};
 const resetCare=()=>{for(const field of ['building-scope','building-unresolved','building-note'])$(field).value='';$('building-action').value='CHECKING';$('building-correction').value='';};
 const homeLabel=(rows,person)=>{const h=homeFor(rows,person);if(!h)return '建物・住戸は未登録';if(!h.buildingId)return '関連付けなし';if(!validHome(rows,person))return '建物・住戸の詳細を受信待ち';return placeBuildings(rows).find(b=>b.buildingId===h.buildingId).label+' / '+(placeUnits(rows,h.buildingId).find(u=>u.unitId===h.unitId)?.label||'住戸未指定');};
 function feedback(){
  const s=snapshot(),own=s.residents.some(r=>r.originId===identity()),busy=saves.busy;
  for(const el of $('buildings').querySelectorAll('button,input,textarea,select'))el.disabled=busy;
  $('building-open').disabled=!own||busy;$('building-unlink').disabled=!own||busy||!homeFor(s.rows,identity())?.buildingId;
  for(const el of $('building-form').querySelectorAll('button,input'))el.disabled=!own||busy;
  for(const el of $('building-care-form').querySelectorAll('button,input,textarea,select'))el.disabled=!own||!s.view||s.view.closed||busy;
  for(const id of ['home-save','unit-save'])$(id).disabled=!own||busy;
  for(const [id,k]of [['building-save-feedback',key('BUILDING',editing||'')],['home-feedback',key('HOME')],['home-detail-feedback',key('HOME')],['unit-feedback',key('UNIT',selected||'')],['building-care-feedback',key('CARE',selected||'')]]){const f=saves.get(k);$(id).textContent=f.text;$(id).dataset.status=f.status;$(id).setAttribute('aria-busy',String(f.status==='pending'));}
 }
 function render(){
  const s=snapshot(),nextContext=s.group+'|'+s.incidentId;
  if(currentGroup!==s.group){selected=null;editing=null;editingUnit=null;$('unit-name').value='';$('building-form').hidden=true;currentGroup=s.group;}
  if(context!==nextContext){resetCare();context=nextContext;}
  const buildings=placeBuildings(s.rows);$('home-current').textContent='自分の登録：'+homeLabel(s.rows,identity());
  $('building-list').innerHTML=buildings.length?buildings.map(b=>{const members=s.residents.filter(r=>validHome(s.rows,r.originId)?.buildingId===b.buildingId),care=currentBuildingCare(buildingCare(s.rows,b.buildingId,s.incidentId));return `<button class="resident" data-building="${b.buildingId}"><strong>${esc(b.label)}</strong><span class="building-status">${esc(care.map(e=>BUILDING_ACTIONS[e.action]).join(' / ')||'建物の対応記録なし')}</span><small>登録住民 ${members.length}人・SOS ${members.filter(r=>r.safety==='SOS').length}人</small><small>対応範囲・未確認事項は詳細で確認</small></button>`;}).join(''):'<p>建物はまだありません。登録後に自分の住居を選択してください。</p>';
  for(const button of $('building-list').querySelectorAll('[data-building]'))button.onclick=()=>{if(selected!==button.dataset.building)resetCare();selected=button.dataset.building;editingUnit=null;$('unit-name').value='';render();$('building-detail').scrollIntoView({block:'start'});};
  const b=buildings.find(x=>x.buildingId===selected);$('building-detail').hidden=!b;if(!b){feedback();return;}
  $('building-name').textContent=b.label;$('building-info').textContent=`登録建物：${b.latitude}, ${b.longitude}／登録者：${b.authorName}`;$('building-edit').hidden=b.authorId!==identity();
  const units=placeUnits(s.rows,selected),old=$('home-unit').value;$('home-unit').innerHTML='<option value="">住戸未指定</option>'+units.map(u=>`<option value="${u.unitId}">${esc(u.label)}</option>`).join('');if(units.some(u=>u.unitId===old))$('home-unit').value=old;
  $('unit-edits').innerHTML=units.filter(u=>u.authorId===identity()).map(u=>`<button type="button" class="secondary" data-edit-unit="${u.unitId}">${esc(u.label)} の住戸名を訂正</button>`).join('');
  for(const button of $('unit-edits').querySelectorAll('[data-edit-unit]'))button.onclick=()=>{const unit=units.find(u=>u.unitId===button.dataset.editUnit);editingUnit=unit.unitId;$('unit-name').value=unit.label;$('unit-save').textContent='住戸の訂正を保存';$('unit-name').focus();};
  $('unit-save').textContent=editingUnit?'住戸の訂正を保存':'住戸を追加';
  const members=s.residents.filter(r=>validHome(s.rows,r.originId)?.buildingId===selected);
  $('building-members').innerHTML=members.length?members.map(r=>`<button class="resident" data-member="${r.originId}"><strong>${esc(r.name)}・${esc(r.safety==='SAFE'?'無事':r.safety==='SOS'?'SOS':'未報告')}</strong><small>${esc(homeLabel(s.rows,r.originId))}・${esc(r.floor)}・寝室：${esc(r.room)}</small></button>`).join(''):'<p>関連付け済みの住民情報はありません。空き家とは判定しません。</p>';
  for(const button of $('building-members').querySelectorAll('[data-member]'))button.onclick=()=>selectResident(button.dataset.member);
  const history=buildingCare(s.rows,selected,s.incidentId).sort((a,b)=>b.createdAt-a.createdAt||a.eventId.localeCompare(b.eventId)),heads=currentBuildingCare(history),active=new Set(heads.map(e=>e.eventId));
  $('building-history').innerHTML=history.length?history.map(e=>`<div class="event"><strong>${esc(BUILDING_ACTIONS[e.action])}${active.has(e.eventId)?'':'（訂正前）'}</strong><p>確認範囲：${esc(e.scope)}<br>未確認事項：${esc(e.unresolved)}</p><p>${esc(e.note)}</p><small>${esc(e.authorName)} · ${esc(time(e.createdAt))}</small></div>`).join(''):'<p>この災害の建物対応記録はありません</p>';
  if(!saves.busy){const correction=$('building-correction').value;$('building-correction').innerHTML='<option value="">新しい記録</option>'+heads.filter(e=>e.authorId===identity()).map(e=>`<option value="${e.eventId}">${esc(BUILDING_ACTIONS[e.action])}・${esc(time(e.createdAt))}を訂正</option>`).join('');if(heads.some(e=>e.eventId===correction))$('building-correction').value=correction;}
  $('building-care-form').hidden=!s.view||s.view.closed||!s.residents.some(r=>r.originId===identity());feedback();
 }
 async function save(kind,data,label,onSaved=()=>{}){
  const s=snapshot(),authorId=identity(),target=kind==='CARE'||kind==='UNIT'?data.buildingId:kind==='BUILDING'?data.buildingId||'':'';
  const operationKey=key(kind,target),g=s.group,incidentId=s.incidentId;
  await saves.run(operationKey,async()=>{
   const own=await store().get(g,authorId);if(!own)throw Error('先に自分の情報を登録してください');const eventId=crypto.randomUUID();
   const candidate={groupId:g,eventId,authorId,authorName:own.name,createdAt:Date.now(),kind,...data};
   if(kind==='BUILDING')candidate.buildingId=data.buildingId||eventId;if(kind==='UNIT')candidate.unitId=data.unitId||eventId;if(kind==='CARE')candidate.incidentId=incidentId;
   const saved=await store().appendPlace(candidate);onSaved(saved);
  },changed,label);await changed();
 }
 const run=fn=>{try{return Promise.resolve(fn()).catch(e=>{$('message').textContent=e.message;});}catch(e){$('message').textContent=e.message;}};
 function openForm(b=null){editing=b?.buildingId||null;const own=snapshot().residents.find(r=>r.originId===identity());$('building-label').value=b?.label||'';$('building-lat').value=b?.latitude??own?.latitude??'';$('building-lon').value=b?.longitude??own?.longitude??'';$('building-form').hidden=false;feedback();}
 $('building-open').onclick=()=>openForm();$('building-edit').onclick=()=>openForm(placeBuildings(snapshot().rows).find(b=>b.buildingId===selected));$('building-cancel').onclick=()=>{$('building-form').hidden=true;};
 $('building-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const data={buildingId:editing,label:$('building-label').value.trim(),latitude:Number($('building-lat').value),longitude:Number($('building-lon').value)};run(()=>save('BUILDING',data,'建物情報',saved=>{selected=saved.buildingId;$('building-form').hidden=true;}));};
 $('unit-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const data={buildingId:selected,unitId:editingUnit,label:$('unit-name').value.trim()};run(()=>save('UNIT',data,'住戸',()=>{editingUnit=null;$('unit-name').value='';}));};
 $('unit-cancel').onclick=()=>{editingUnit=null;$('unit-name').value='';$('unit-save').textContent='住戸を追加';};
 $('home-save').onclick=()=>{const data={buildingId:selected,unitId:$('home-unit').value||null};run(()=>save('HOME',data,'自分の住居'));};
 $('building-unlink').onclick=()=>run(()=>save('HOME',{buildingId:null,unitId:null},'住居の関連付け解除'));
 $('building-correction').onchange=()=>{const e=snapshot().rows.find(x=>x.eventId===$('building-correction').value);if(!e){resetCare();return;}$('building-action').value=e.action;$('building-scope').value=e.scope;$('building-unresolved').value=e.unresolved;$('building-note').value=e.note;};
 $('building-care-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const data={buildingId:selected,action:$('building-action').value,scope:$('building-scope').value.trim(),unresolved:$('building-unresolved').value.trim(),note:$('building-note').value.trim(),supersedes:$('building-correction').value||null};run(()=>save('CARE',data,'建物の対応記録',saved=>{$('building-correction').innerHTML+=`<option value="${saved.eventId}">保存した記録を訂正</option>`;$('building-correction').value=saved.eventId;}));};
 return {render,feedback,homeLabel};
}
