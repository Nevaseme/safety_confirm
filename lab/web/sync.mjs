import {validateIncident,normalizeIncident,sameIncident,incidentView,incidentOptions} from './incidents.mjs';
import {validatePlace,samePlace,preparePlace} from './places.mjs';
export {incidentView,incidentOptions} from './incidents.mjs';
const uuid = /^[0-9a-fA-F-]{36}$/;
export const ACTIONS = {CHECKING:'確認中', NEEDS_HELP:'要救助', RESCUED:'本人の救助完了', REVOKED:'記録を撤回'};
const require = (ok, message) => { if (!ok) throw Error(message); };
const text = (s,n,empty=true) => typeof s==='string' && s.length<=n && (empty || s.trim().length>0);
export function validateResident(r) {
 require(r && /^[A-Za-z0-9_-]{1,40}$/.test(r.groupId) && uuid.test(r.originId),'登録情報のIDが不正です');
 require(Number.isSafeInteger(r.revision)&&r.revision>0&&Number.isSafeInteger(r.updatedAt)&&r.updatedAt>=0,'更新情報が不正です');
 require(text(r.name,60,false)&&text(r.floor,40)&&text(r.room,240),'名前・階数・部屋の入力を確認してください');
 require(Number.isFinite(r.latitude)&&Math.abs(r.latitude)<=85&&Number.isFinite(r.longitude)&&Math.abs(r.longitude)<=180,'緯度・経度を確認してください');
 require(['UNREPORTED','SAFE','SOS'].includes(r.safety),'安否が不正です'); return r;
}
export function validateEvent(e) {
 require(e && /^[A-Za-z0-9_-]{1,40}$/.test(e.groupId)&&[e.eventId,e.subjectId,e.authorId].every(x=>uuid.test(x)),'記録のIDが不正です');
 require(Number.isSafeInteger(e.createdAt)&&e.createdAt>=0&&text(e.authorName,60,false)&&text(e.note,500),'記録の内容が不正です');
 require(Object.hasOwn(ACTIONS,e.action),'対応状況が不正です');
 require(e.supersedes==null || (uuid.test(e.supersedes)&&e.supersedes!==e.eventId),'訂正元が不正です');
 require(e.action!=='REVOKED'||e.supersedes!=null,'撤回には訂正元が必要です'); return e;
}
const sameEvent=(a,b)=>['groupId','eventId','subjectId','authorId','authorName','createdAt','action','note','supersedes'].every(k=>(a[k]??null)===(b[k]??null));
export function currentEvents(events) {
 const byId=new Map(events.map(e=>[e.eventId,e]));
 // Only same-author, same-subject corrections affect the displayed heads. Preserve dangling/foreign references in history.
 const replaced=new Set(events.filter(e=>{const old=byId.get(e.supersedes);return old&&old.authorId===e.authorId&&old.subjectId===e.subjectId&&old.groupId===e.groupId;}).map(e=>e.supersedes));
 return events.filter(e=>!replaced.has(e.eventId));
}
export class Store {
 constructor(db){this.db=db;this.name=db.name;}
 static open(name='pland-lab',factory=globalThis.indexedDB){return new Promise((resolve,reject)=>{
  const request=factory.open(name,3);
  request.onupgradeneeded=()=>{const db=request.result;if(!db.objectStoreNames.contains('residents')){db.createObjectStore('residents',{keyPath:['groupId','originId']});db.createObjectStore('events',{keyPath:['groupId','eventId']});db.createObjectStore('meta');}if(!db.objectStoreNames.contains('incidents'))db.createObjectStore('incidents',{keyPath:['groupId','eventId']});if(!db.objectStoreNames.contains('places'))db.createObjectStore('places',{keyPath:['groupId','eventId']});};
  request.onsuccess=()=>{request.result.onversionchange=()=>request.result.close();resolve(new Store(request.result));};request.onerror=()=>reject(request.error);
 });}
 close(){this.db.close();}
 read(table,key){return new Promise((resolve,reject)=>{const tx=this.db.transaction(table);const q=key===undefined?tx.objectStore(table).getAll():tx.objectStore(table).get(key);q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});}
 // Resolve only on transaction completion, never on the individual put's success.
 update(table,key,change){return new Promise((resolve,reject)=>{
  const tx=this.db.transaction(table,'readwrite');const s=tx.objectStore(table);const q=s.get(key);let result,problem;
  q.onsuccess=()=>{try{const next=change(q.result);result=next!==undefined;if(result){if(table==='meta')s.put(next,key);else s.put(next);}}catch(e){problem=e;tx.abort();}};
  tx.oncomplete=()=>resolve(result);tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 async identity(){const key='identity';await this.update('meta',key,old=>old?undefined:crypto.randomUUID());return this.read('meta',key);}
 async all(group){return (await this.read('residents')).filter(r=>r.groupId===group);}
 get(group,id){return this.read('residents',[group,id]);}
 async events(group){return (await this.read('events')).filter(e=>e.groupId===group);}
 getEvent(group,id){return this.read('events',[group,id]);}
 async incidents(group){return (await this.read('incidents')).filter(e=>e.groupId===group);}
 getIncident(group,id){return this.read('incidents',[group,id]);}
 async places(group){return (await this.read('places')).filter(e=>e.groupId===group);}
 getPlace(group,id){return this.read('places',[group,id]);}
 placeTransaction(input,local){return new Promise((resolve,reject)=>{
  const tx=this.db.transaction(local?['places','incidents','residents']:['places'],'readwrite'),s=tx.objectStore('places'),q=s.getAll();let result,problem;
  const save=(rows,incidents=[])=>{try{
   const e=local?preparePlace(input,rows.filter(x=>x.groupId===input.groupId),incidents.filter(x=>x.groupId===input.groupId)):validatePlace(input);
   const old=rows.find(x=>x.groupId===e.groupId&&x.eventId===e.eventId);if(old){require(samePlace(old,e),'同じIDの建物記録に異なる内容があります');result=false;return;}
   require(!rows.some(x=>x.groupId===e.groupId&&x.authorId===e.authorId&&x.sequence===e.sequence),'同じ更新番号の建物記録に異なる内容があります');s.put(e);result=e;
  }catch(e){problem=e;tx.abort();}};
  q.onsuccess=()=>{if(!local){save(q.result);return;}const own=tx.objectStore('residents').get([input.groupId,input.authorId]);own.onsuccess=()=>{if(!own.result){problem=Error('先に自分の情報を登録してください');tx.abort();return;}const incidents=tx.objectStore('incidents').getAll();incidents.onsuccess=()=>save(q.result,incidents.result);};};
  tx.oncomplete=()=>resolve(result);tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 mergePlace(e){return this.placeTransaction(e,false);}
 appendPlace(e){return this.placeTransaction(e,true);}
 resolveIncidentSelection(group){return new Promise((resolve,reject)=>{
  const tx=this.db.transaction(['incidents','meta'],'readwrite'),meta=tx.objectStore('meta'),key='incident:'+group;
  const q=meta.get(key);let result;
  q.onsuccess=()=>{result=q.result;if(result!==undefined)return;
   const rows=tx.objectStore('incidents').getAll();rows.onsuccess=()=>{const active=incidentOptions(rows.result.filter(e=>e.groupId===group)).filter(v=>!v.closed);if(active.length===1){result=active[0].start.eventId;meta.put(result,key);}};
  };
  tx.oncomplete=()=>resolve(result||'');tx.onabort=()=>reject(tx.error||Error('表示対象を保存できません'));tx.onerror=()=>{};
 });}
 incidentTransaction(make){return new Promise((resolve,reject)=>{
  const tx=this.db.transaction('incidents','readwrite'),s=tx.objectStore('incidents'),q=s.getAll();let result,problem;
  q.onsuccess=()=>{try{const e=validateIncident(make(q.result)),old=q.result.find(x=>x.groupId===e.groupId&&x.eventId===e.eventId);if(old){require(sameIncident(old,e),'同じIDの記録に異なる内容があります');result=false;return;}
   require(!q.result.some(x=>x.groupId===e.groupId&&x.incidentId===e.incidentId&&x.authorId===e.authorId&&x.sequence===e.sequence),'同じ更新番号の記録に異なる内容があります');s.put(e);result=e;
  }catch(e){problem=e;tx.abort();}};tx.oncomplete=()=>resolve(result);tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 mergeIncident(e){return this.incidentTransaction(()=>e);}
 appendIncident(e){return this.incidentTransaction(rows=>{
  const relevant=rows.filter(x=>x.groupId===e.groupId),v=incidentView(relevant,e.incidentId);require(v&&!v.closed,'開始中の災害を選んでください');
  if(e.kind==='END')require(v.start.authorId===e.authorId,'終了できるのは開始した人だけです');
  if(e.kind==='CARE'&&e.supersedes){const old=v.care.find(x=>x.eventId===e.supersedes);require(old&&old.authorId===e.authorId&&old.subjectId===e.subjectId,'同じ災害の自分の記録だけ訂正できます');}
  const sequence=Math.max(0,...relevant.filter(x=>x.incidentId===e.incidentId&&x.authorId===e.authorId).map(x=>x.sequence))+1;return {...e,sequence};
 });}
 merge(r){validateResident(r);return this.update('residents',[r.groupId,r.originId],old=>old&&old.revision>=r.revision?undefined:r);}
 async saveOwn(r){return this.update('residents',[r.groupId,r.originId],old=>validateResident({...r,revision:(old?.revision||0)+1,updatedAt:Date.now()}));}
 saveProfile(r){return new Promise((resolve,reject)=>{
  const tx=this.db.transaction(['residents','meta'],'readwrite'),s=tx.objectStore('residents'),q=s.get([r.groupId,r.originId]);let problem;
  q.onsuccess=()=>{try{s.put(validateResident({...r,safety:q.result?.safety||r.safety,revision:(q.result?.revision||0)+1,updatedAt:Date.now()}));tx.objectStore('meta').put(r.groupId,'group');}catch(e){problem=e;tx.abort();}};
  tx.oncomplete=()=>resolve();tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 mergeEvent(e){validateEvent(e);return this.update('events',[e.groupId,e.eventId],old=>{if(old){require(sameEvent(old,e),'同じIDの記録に異なる内容があります');return undefined;}return e;});}
}
export class SyncEngine {
 constructor(group,id,store,send,onError=()=>{},onReceipt=()=>{}){Object.assign(this,{group,id,store,send,onError,onReceipt});this.peers=new Set();this.pending=new Map();}
 async connected(peer){this.peers.add(peer);await this.guard(()=>this.summary(peer));}
 disconnected(peer){this.peers.delete(peer);for(const key of this.pending.keys())if(key.startsWith(peer+'|'))this.pending.delete(key);}
 async changed(){for(const peer of this.peers)await this.guard(()=>this.summary(peer));}
 async guard(fn){try{await fn();}catch(e){this.onError(e.message||'同期に失敗しました');}}
 async emit(peer,m){const msg={version:1,groupId:this.group,entries:[],...m};require(new TextEncoder().encode(JSON.stringify(msg)).length<=24000,'通信データが大きすぎます');if(this.peers.has(peer))await this.send(peer,msg);}
 async summary(peer){
  const places=await this.store.places?.(this.group)||[];for(let i=0;i<places.length;i+=100)await this.emit(peer,{version:4,type:'PLACE_SUMMARY',entries:places.slice(i,i+100).map(e=>({originId:e.eventId,revision:1}))});
  const journal=await this.store.incidents?.(this.group)||[];for(let i=0;i<journal.length;i+=100)await this.emit(peer,{version:3,type:'INCIDENT_SUMMARY',entries:journal.slice(i,i+100).map(e=>({originId:e.eventId,revision:1}))});
  for(const care of [false,true]){const rows=care?await this.store.events(this.group):await this.store.all(this.group);const entries=rows.map(r=>({originId:care?r.eventId:r.originId,revision:care?1:r.revision}));
   for(let i=0;i<(care?entries.length:Math.max(1,entries.length));i+=100)await this.emit(peer,{version:care?2:1,type:care?'CARE_SUMMARY':'SUMMARY',entries:entries.slice(i,i+100)});
  }
 }
 async receive(peer,message){
  if(!this.peers.has(peer))return;
  await this.guard(async()=>{
   const raw=typeof message==='string'?message:JSON.stringify(message);if(new TextEncoder().encode(raw).length>24000)return;
   const m=JSON.parse(raw);if(!m||m.groupId!==this.group||!Array.isArray(m.entries??[])||(m.entries??[]).length>100)return;
   if(m.version===4&&typeof m.type==='string'&&m.type.startsWith('PLACE_')){await this.receivePlace(peer,m);return;}
   if(m.version===3&&typeof m.type==='string'&&m.type.startsWith('INCIDENT_')){await this.receiveIncident(peer,m);return;}
   const care=m.version===2&&typeof m.type==='string'&&m.type.startsWith('CARE_');if(!care&&m.version!==1)return;
   const type=care?m.type.slice(5):m.type, entries=m.entries??[];
   if(entries.some(e=>!e||!uuid.test(e.originId)||!Number.isSafeInteger(e.revision)||e.revision<1||(care&&e.revision!==1)))return;
   const base={version:care?2:1}, kind=t=>care?'CARE_'+t:t;
   const get=id=>care?this.store.getEvent(this.group,id):this.store.get(this.group,id);
   if(type==='SUMMARY'){
    const wanted=[];for(const e of entries){if(!care&&e.originId===this.id)continue;const old=await get(e.originId);if(!old||(!care&&old.revision<e.revision))wanted.push(e);}
    if(wanted.length)await this.emit(peer,{...base,type:kind('REQUEST'),entries:wanted});
   }else if(type==='REQUEST'){
    for(const e of entries){const r=await get(e.originId);if(r){const revision=care?1:r.revision;this.pending.set(`${peer}|${care}|${e.originId}`,revision);await this.emit(peer,{...base,type:kind('RECORD'),[care?'event':'record']:r});}}
   }else if(type==='RECORD'){
    const r=care?m.event:m.record;(care?validateEvent:validateResident)(r);if(r.groupId!==this.group||(!care&&r.originId===this.id))return;
    const changed=await (care?this.store.mergeEvent(r):this.store.merge(r));
    const entry={originId:care?r.eventId:r.originId,revision:care?1:r.revision};
    await this.guard(()=>this.emit(peer,{...base,type:kind('ACK'),entries:[entry]}));
    if(changed)for(const other of this.peers)if(other!==peer)await this.guard(()=>this.emit(other,{...base,type:kind('SUMMARY'),entries:[entry]}));
   }else if(type==='ACK'){
    for(const e of entries){const key=`${peer}|${care}|${e.originId}`;if(this.pending.get(key)===e.revision){this.pending.delete(key);this.onReceipt({peer,care,...e});}}
   }
  });
 }
 async receiveIncident(peer,m){
  const entries=m.entries??[];if(entries.some(e=>!e||!uuid.test(e.originId)||e.revision!==1))return;
  const emit=(to,type,data)=>this.emit(to,{version:3,type:'INCIDENT_'+type,...data});
  if(m.type==='INCIDENT_SUMMARY'){const missing=[];for(const e of entries)if(!await this.store.getIncident(this.group,e.originId))missing.push(e);if(missing.length)await emit(peer,'REQUEST',{entries:missing});}
  else if(m.type==='INCIDENT_REQUEST'){for(const e of entries){const incident=await this.store.getIncident(this.group,e.originId);if(incident){this.pending.set(`${peer}|incident|${e.originId}`,1);await emit(peer,'RECORD',{incident});}}}
  else if(m.type==='INCIDENT_RECORD'){const e=validateIncident(m.incident);if(e.groupId!==this.group)return;const changed=await this.store.mergeIncident(e),entries=[{originId:e.eventId,revision:1}];await this.guard(()=>emit(peer,'ACK',{entries}));if(changed)for(const other of this.peers)if(other!==peer)await this.guard(()=>emit(other,'SUMMARY',{entries}));}
  else if(m.type==='INCIDENT_ACK')for(const e of entries)if(this.pending.delete(`${peer}|incident|${e.originId}`))this.onReceipt({peer,incident:true,...e});
 }
 async receivePlace(peer,m){
  const entries=m.entries??[];if(entries.some(e=>!e||!uuid.test(e.originId)||e.revision!==1))return;
  const emit=(to,type,data)=>this.emit(to,{version:4,type:'PLACE_'+type,...data});
  if(m.type==='PLACE_SUMMARY'){const missing=[];for(const e of entries)if(!await this.store.getPlace(this.group,e.originId))missing.push(e);if(missing.length)await emit(peer,'REQUEST',{entries:missing});}
  else if(m.type==='PLACE_REQUEST'){for(const e of entries){const place=await this.store.getPlace(this.group,e.originId);if(place){this.pending.set(`${peer}|place|${e.originId}`,1);await emit(peer,'RECORD',{place});}}}
  else if(m.type==='PLACE_RECORD'){const e=validatePlace(m.place);if(e.groupId!==this.group)return;const changed=await this.store.mergePlace(e),entries=[{originId:e.eventId,revision:1}];await this.guard(()=>emit(peer,'ACK',{entries}));if(changed)for(const other of this.peers)if(other!==peer)await this.guard(()=>emit(other,'SUMMARY',{entries}));}
  else if(m.type==='PLACE_ACK')for(const e of entries)if(this.pending.delete(`${peer}|place|${e.originId}`))this.onReceipt({peer,place:true,...e});
 }
}
