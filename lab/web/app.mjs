import {Store,SyncEngine,ACTIONS,currentEvents,incidentView,incidentOptions} from './sync.mjs';
import {SaveFeedback,ConnectionFeedback} from './feedback.mjs';
import {appendScopedIncident} from './incidents.mjs';
import {createPlaceUi} from './place-ui.mjs';
import {managedIncidentOptions,canonicalIncidentId,memberFor,quakeStates} from './managed-records.mjs';
const $=id=>document.getElementById(id), esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const labels={UNREPORTED:'未報告',SAFE:'無事',SOS:'SOS'},time=x=>x?new Date(x).toLocaleString('ja-JP'):'今回未回答';
const profile=new URL(location.href).searchParams.get('profile')==='C'?'C':'B';
let store,id,group='demo',records=[],events=[],selected=null,socket=null,engine=null,generation=0,queue=Promise.resolve(),queued=0,receipts=0,dirty=false;
let syncError='';
let incidentRows=[],incidentId='',view=null,refreshRevision=0;
let placeRows=[],roster=null;
const canEnd=()=>roster?memberFor(roster,id).role==='ADMIN':view?.start.authorId===id;
const canAnswer=()=>!!view&&!view.closed;
const careKey=()=>`care:${group}:${incidentId}:${selected}`;
const incidentScope=()=>({groupId:group,incidentId,authorId:id});
const saves=new SaveFeedback(renderFeedback);
const link=new ConnectionFeedback(renderFeedback,()=>closeTransport());
const placeUI=createPlaceUi({$,esc,time,store:()=>store,identity:()=>id,snapshot:()=>({group,incidentId,view,residents:records,rows:placeRows}),saves,changed,selectResident:person=>{selected=person;tab('residents');renderDetail();renderFeedback();}});
function renderFeedback(){
 const s=link.state,own=records.some(r=>r.originId===id),busy=saves.busy;
 for(const key of ['profile','safety','care','incident']){const f=saves.get(key==='care'?careKey():key);const el=$(key+'-feedback');el.textContent=f.text;el.dataset.status=f.status;el.setAttribute('aria-busy',String(f.status==='pending'));}
 for(const form of ['profile','care-form'])for(const el of $(form).querySelectorAll('input,textarea,select,button'))el.disabled=busy;
 $('safe').disabled=$('sos').disabled=busy||!own||!canAnswer();
 $('incident-select').disabled=busy;$('incident-open').disabled=busy||!own;
 for(const el of $('incident-form').querySelectorAll('input,button'))el.disabled=busy||!own;
 $('incident-end').disabled=busy||!canAnswer()||!canEnd();
 $('care-save').disabled=busy||!canAnswer();
 $('profile-save').textContent=saves.get('profile').status==='pending'?'保存しています…':'登録情報を保存';
 $('care-save').textContent=saves.get(careKey()).status==='pending'?'保存しています…':'対応記録を保存';
 $('network').textContent=s.text;
 $('sharing').textContent=s.text;
 $('connection-state').textContent=s.text;
 $('connection-error').textContent=s.error||'';
 $('sync-error').textContent=syncError;
 $('connect-start').disabled=link.active||busy||!own;
 $('connect-start').textContent=s.status==='connecting'?'接続しています…':link.active?'接続済み':'実験通信を開始';
 for(const el of document.querySelectorAll('[data-stop]'))el.disabled=!link.active;
 $('token').disabled=$('role').disabled=link.active||busy;
 $('route').textContent=s.pendingPhase?`${s.pendingPhase==='AB'?'A ↔ B':'B ↔ C'}へ切替中…（確認済み：${s.phase}）`:s.phase?`${link.active?'現在の経路':'最後に確認した経路'}：${s.phase==='AB'?'A ↔ B':'B ↔ C'}`:'経路は接続後に確認します';
 for(const phase of ['AB','BC']){const b=$('phase-'+phase.toLowerCase());b.disabled=$('role').value!=='B'||!['waiting','connected'].includes(s.status)||!!s.pendingPhase||s.phase===phase;b.setAttribute('aria-pressed',String(s.phase===phase));}
 $('route-help').textContent=$('role').value==='B'?'B ↔ Cへ切り替える前に、Android Aの実験通信を停止してください。':'経路の切替はiPhoneなど役割Bの端末から行います。';
 for(const el of $('list').querySelectorAll('button'))el.disabled=busy;
 placeUI.feedback();
}
const message=text=>{$('message').textContent=text;};
function task(fn){return Promise.resolve().then(fn).catch(e=>message(e.message||'操作できませんでした'));}
function tab(name){for(const x of ['safety','residents','buildings','settings'])$(x).hidden=x!==name;document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===name)));}
document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>tab(b.dataset.tab));
function badge(r){return `<span class="badge ${r.safety}">${labels[r.safety]}</span>`;}
function statusFor(subject){const active=currentEvents(events.filter(e=>e.subjectId===subject));return active.length?active.map(e=>ACTIONS[e.action]).join(' / '):'対応記録なし';}
async function refresh(){
 const epoch=++refreshRevision,g=group;const [raw,legacy,journal,savedSelection,places,participants,quakes]=await Promise.all([store.all(g),store.events(g),store.incidents(g),store.resolveIncidentSelection(g),store.places(g),store.roster(g),store.quakeEvents(g)]);if(epoch!==refreshRevision||g!==group)return;
 placeRows=places.map(e=>({...e,incidentId:canonicalIncidentId(participants,quakes,e.incidentId)??null}));roster=participants;const selection=canonicalIncidentId(roster,quakes,savedSelection);
 incidentRows=journal;const options=managedIncidentOptions(journal,roster,quakes),active=options.filter(v=>!v.closed);
 const next=selection==='legacy'||options.some(v=>v.start.eventId===selection)?selection:'';
 if(next!==incidentId){incidentId=next;selected=null;$('note').value='';$('action').value='CHECKING';$('supersedes').value='';saves.clearOtherResults();}
 view=options.find(v=>v.start.eventId===incidentId)||null;
 records=incidentId==='legacy'?raw:raw.map(r=>({...r,safety:view?.answers[r.originId]?.safety||'UNREPORTED',updatedAt:view?.answers[r.originId]?.createdAt||0}));
 events=incidentId==='legacy'?legacy:view?.care||[];
 $('incident-select').innerHTML='<option value="">災害を選択してください</option>'+options.map(v=>`<option value="${v.start.eventId}">${esc(v.start.training?'訓練':'地震')}：${esc(v.start.title)}・${esc(v.start.area)}${v.closed?'（終了）':''} [${v.start.eventId.slice(0,8)}]</option>`).join('')+'<option value="legacy">過去の情報（災害未分類・閲覧のみ）</option>';$('incident-select').value=incidentId;
 $('incident-state').textContent=view?`${view.start.training?'訓練':'地震'}・${view.closed?'終了・閲覧のみ':'安否確認中'}：${view.start.title}／${view.start.area}。開始：${time(view.start.createdAt)}、${view.start.authorName}${!view.closed&&!canEnd()?(roster?'。終了は管理者が行います。':'。終了できるのは開始した人だけです。'):''}`:incidentId==='legacy'?'以前の安否・対応記録です。今回の地震への回答ではありません。':active.length>1?'複数の安否確認があります。対象を選択してください。':'安否確認は始まっていません。「地震発生」から開始できます。';
 const own=records.find(r=>r.originId===id);
 $('own').innerHTML=own?`<strong>${esc(own.name)}</strong> ${badge(own)}<p>${own.updatedAt?'安否更新 '+esc(time(own.updatedAt)):'今回の回答はまだありません'}</p>`:'先に「登録・接続」で自分の情報を登録してください。';
 if(own&&!dirty){$('name').value=own.name;$('lat').value=own.latitude;$('lon').value=own.longitude;$('floor').value=own.floor;$('room').value=own.room;}
 $('count').textContent=`${records.length}人`;
 const shown=records.filter(r=>$('filter').value==='ALL'||r.safety===$('filter').value).sort((a,b)=>(a.safety==='SOS'?-1:0)-(b.safety==='SOS'?-1:0)||a.name.localeCompare(b.name,'ja'));
 $('list').innerHTML=shown.length?shown.map(r=>`<button class="resident" data-person="${esc(r.originId)}"><strong>${esc(r.name)}${r.originId===id?'（自分）':''}</strong> ${badge(r)}<small>登録住居 · ${esc(r.floor||'階数未登録')}</small><small>${esc(statusFor(r.originId))}</small><small>安否更新 ${esc(time(r.updatedAt))}</small></button>`).join(''):'<p>該当する住民情報がありません。</p>';
 for(const button of $('list').querySelectorAll('[data-person]'))button.onclick=()=>{selected=button.dataset.person;renderDetail();renderFeedback();$('detail').scrollIntoView({block:'start'});};
 renderDetail();placeUI.render();renderFeedback();
}
function renderDetail(){const r=records.find(r=>r.originId===selected);$('detail').hidden=!r;if(!r)return;
 $('detail-name').textContent=r.name;$('detail-info').textContent=`安否：${labels[r.safety]}\n登録住居：${r.latitude}, ${r.longitude}\n${placeUI.homeLabel(placeRows,r.originId)}\n階数：${r.floor||'未登録'}\n寝る部屋：${r.room||'未登録'}\n安否更新：${time(r.updatedAt)}`;
 const history=events.filter(e=>e.subjectId===selected).sort((a,b)=>b.createdAt-a.createdAt||a.eventId.localeCompare(b.eventId)),heads=currentEvents(history),active=new Set(heads.map(e=>e.eventId));
 $('history').innerHTML=history.length?history.map(e=>`<div class="event"><strong>${esc(ACTIONS[e.action])}${active.has(e.eventId)?'':'（訂正前）'}</strong><p>${esc(e.note||'詳細なし')}</p><small>${esc(e.authorName)} · ${esc(time(e.createdAt))}${e.supersedes?' · 訂正記録':''}<br>記録ID ${esc(e.eventId)}</small></div>`).join(''):'<p>まだ対応記録はありません。</p>';
 if(saves.busy)return;
 const old=$('supersedes').value;$('supersedes').innerHTML='<option value="">新しい記録</option>'+heads.filter(e=>e.authorId===id).map(e=>`<option value="${e.eventId}">${esc(time(e.createdAt))} ${esc(ACTIONS[e.action])} を訂正</option>`).join('');
 if([...$('supersedes').options].some(o=>o.value===old))$('supersedes').value=old;
 $('care-form').hidden=!records.some(x=>x.originId===id)||!canAnswer();
}
function closeTransport(){
 generation++;const old=socket;socket=null;engine=null;queue=Promise.resolve();queued=0;old?.close();
}
function stop(reason='共有停止中'){closeTransport();link.stop(reason);}
function connect(){
 if(link.active||saves.busy)return;
 if(!records.some(r=>r.originId===id))throw Error('先に自分の情報を登録してください');
 closeTransport();syncError='';const gen=generation,epoch=link.start();receipts=0;$('receipts').textContent='今回の接続中に相手が保存を確認：0件';
 let ws;try{ws=new WebSocket(`${location.origin.replace('https:','wss:')}/socket`);}catch{link.fail(epoch,'接続を開始できません。設定を確認して再試行してください。');return;}socket=ws;
 const token=$('token').value.trim();sessionStorage.setItem('pland-token',token);
 const sync=new SyncEngine(group,id,store,(to,body)=>{if(gen!==generation||ws.readyState!==WebSocket.OPEN)throw Error('接続が終了しました');ws.send(JSON.stringify({kind:'data',to,body}));},error=>{if(gen===generation){syncError=`通信エラー：${error}。保存済み情報は残ります。通信を停止して再接続してください。`;renderFeedback();}},()=>{if(gen===generation)$('receipts').textContent=`今回の接続中に相手が保存を確認：${++receipts}件`;});engine=sync;
 ws.onopen=()=>{if(gen===generation)ws.send(JSON.stringify({kind:'join',role:$('role').value,group,token}));};
 ws.onmessage=e=>{if(gen!==generation)return;if(++queued>256){stop('受信が混み合ったため停止');message('再接続してください');return;}
  queue=queue.then(async()=>{if(gen!==generation)return;const m=JSON.parse(e.data);
   if(m.kind==='peer-up')await sync.connected(m.peer);
   if(m.kind==='peer-down')sync.disconnected(m.peer);
   if(m.kind==='data')await sync.receive(m.from,m.body);
   if(m.kind==='error')link.rejectPhase(epoch,m.message);
   if(m.kind==='joined')link.joined(epoch,m.phase);
   if(m.kind==='phase')link.confirmPhase(epoch,m.phase);
   if(gen===generation){link.peers(epoch,sync.peers.size);await refresh();}
  }).catch(e=>{if(gen===generation){syncError=e.message;renderFeedback();}}).finally(()=>{if(gen===generation)queued--;});
 };
 ws.onclose=()=>{if(gen===generation){closeTransport();link.fail(epoch,'接続が終了しました。接続コード・役割・サーバーを確認して再接続してください。');}};
 ws.onerror=()=>{if(gen===generation){closeTransport();link.fail(epoch,'接続できません。証明書、同じWi-Fi、PCの実験サーバーを確認して再試行してください。');}};
}
async function changed(){await refresh();if(engine)await engine.changed();}
$('filter').onchange=()=>task(refresh);
$('profile').oninput=()=>{dirty=true;};
$('profile').onsubmit=e=>{e.preventDefault();if(saves.busy)return;task(async()=>{
 await saves.run('profile',async()=>{
 const nextGroup=$('group').value.trim();const previous=await store.get(nextGroup,id);
 const candidate={groupId:nextGroup,originId:id,revision:1,updatedAt:0,name:$('name').value.trim(),latitude:Number($('lat').value),longitude:Number($('lon').value),floor:$('floor').value.trim(),room:$('room').value.trim(),safety:previous?.safety||'UNREPORTED'};
 await store.saveProfile(candidate);if(group!==nextGroup){stop('グループを変更したため停止');saves.clearOtherResults();}group=nextGroup;selected=null;dirty=false;
 },changed,'登録情報');await refresh();
 });};
for(const [button,safety] of [['safe','SAFE'],['sos','SOS']])$(button).onclick=()=>{const scope=incidentScope();return task(async()=>{await saves.run('safety',()=>appendScopedIncident(store,scope,'ANSWER',{safety}),changed,labels[safety]);await refresh();});};
$('care-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const scope=incidentScope(),key=careKey(),subject=selected,action=$('action').value,note=$('note').value.trim(),supersedes=$('supersedes').value||null;task(async()=>{await saves.run(key,async()=>{if(!subject||!records.some(r=>r.originId===subject))throw Error('対象住民の登録を確認してください');
 await appendScopedIncident(store,scope,'CARE',{subjectId:subject,action,note,supersedes});$('note').value='';$('supersedes').value='';
 },changed,'対応記録');await refresh();});};
$('incident-select').onchange=()=>task(async()=>{if(saves.busy)return;await store.update('meta','incident:'+group,()=>$('incident-select').value);await refresh();});
$('incident-open').onclick=()=>{$('incident-form').hidden=!$('incident-form').hidden;};
$('incident-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;task(async()=>{let created;await saves.run('incident',async()=>{
 const own=await store.get(group,id);if(!own)throw Error('先に登録してください');const eventId=crypto.randomUUID();
 created={groupId:group,eventId,incidentId:eventId,authorId:id,authorName:own.name,createdAt:Date.now(),sequence:1,kind:'START',title:$('incident-title').value.trim(),area:$('incident-area').value.trim(),training:$('incident-training').checked};if(await store.roster(group)){await store.commandQuake({groupId:group,eventId,actorDeviceId:id,operatedAt:created.createdAt,action:'REPORT',training:created.training,title:created.title,area:created.area});const states=quakeStates(await store.roster(group),await store.quakeEvents(group));created.eventId=states.find(s=>!s.closed&&s.formation.training===created.training)?.formation.incidentId||'';}else await store.mergeIncident(created);
 },async()=>{await store.update('meta','incident:'+group,()=>created.eventId);$('incident-form').hidden=true;await changed();},'地震発生・安否確認の開始');await refresh();});};
$('incident-end').onclick=()=>{const scope=incidentScope();return task(async()=>{await saves.run('incident',async()=>{const r=await store.roster(scope.groupId);if(r){const state=quakeStates(r,await store.quakeEvents(scope.groupId)).find(s=>s.incidentIds.includes(scope.incidentId));if(!state)throw Error('開始中の災害を選んでください');await store.commandQuake({groupId:scope.groupId,eventId:crypto.randomUUID(),actorDeviceId:scope.authorId,operatedAt:Date.now(),action:'END',training:state.formation.training,targetId:scope.incidentId});}else await appendScopedIncident(store,scope,'END');},changed,'安否確認の終了');await refresh();});};
$('connect').onsubmit=e=>{e.preventDefault();task(connect);};for(const b of document.querySelectorAll('[data-stop]'))b.onclick=()=>stop();$('role').onchange=renderFeedback;
for(const phase of ['AB','BC'])$('phase-'+phase.toLowerCase()).onclick=()=>task(()=>{if(!link.requestPhase($('role').value,phase))return;try{socket.send(JSON.stringify({kind:'phase',phase}));}catch{stop('接続が終了しました。再接続して経路を確認してください。');}});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&socket)stop('画面を閉じたため停止');});
window.addEventListener('pagehide',()=>stop());
await task(async()=>{
 store=await Store.open('pland-lab-'+profile);id=await store.identity();group=(await store.read('meta','group'))||'demo';$('group').value=group;$('role').value=profile;
 $('token').value=location.hash.slice(1)||sessionStorage.getItem('pland-token')||'';if(location.hash)history.replaceState(null,'',location.pathname+location.search);
 await refresh();if(!records.some(r=>r.originId===id))tab('settings');
 if('serviceWorker'in navigator){await navigator.serviceWorker.register('/sw.js');await navigator.serviceWorker.ready;$('offline').textContent='オフライン起動の準備ができました。通信には同じWi-Fiと実験サーバーが必要です。';}
});
