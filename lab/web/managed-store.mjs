import {validateRoster,memberFor,validateQuake,validateQuakeCommand,formationCandidates,quakeStates,validateResponse,validateResponseCommand,responseHeads,sameRecord,canonicalIncidentId} from './managed-records.mjs';
import {regionalSummary} from './regions.mjs';

export class ManagedStore {
 constructor(db){this.db=db;}
 roster(group){return this.read('rosters',group);}
 async quakeEvents(group){return (await this.read('quakes')).filter(e=>e.groupId===group);}
 async responseEvents(group){return (await this.read('responses')).filter(e=>e.groupId===group);}
 reportRegionSummary(group,incidentId,catalog,assignments={}){return this.managedTransaction(tables=>{
  const roster=tables.rosters.find(r=>r.groupId===group);if(!roster)throw Error('参加者設定を読み込んでください');
  const quakes=tables.quakes.filter(e=>e.groupId===group),selected=canonicalIncidentId(roster,quakes,incidentId);
  if(!quakeStates(roster,quakes).some(s=>s.formation.incidentId===selected))throw Error('集計する災害を選んでください');
  const heads=responseHeads(roster,tables.responses.filter(e=>e.groupId===group).map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)})));
  const reports=[...new Set(heads.filter(e=>e.incidentId===selected&&e.change.type==='REPORT').map(e=>e.change.reportId))];
  return regionalSummary(catalog,reports.map(id=>({target:{kind:'REPORT',id},regionIds:assignments[id]||[]})));
 });}
 // All reads, validation and writes are inside one transaction; ACK callers await oncomplete.
 managedTransaction(work){return new Promise((resolve,reject)=>{
  const names=['rosters','quakes','responses','incidents','residents'];
  const tx=this.db.transaction(names,'readwrite'),tables={};let remaining=names.length,result,problem;
  for(const name of names){const q=tx.objectStore(name).getAll();q.onsuccess=()=>{
   tables[name]=q.result;if(--remaining)return;
   try{result=work(tables,(name,value)=>tx.objectStore(name).put(value));}catch(e){problem=e;tx.abort();}
  };}
  tx.oncomplete=()=>resolve(result);tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 importRoster(text,ownDeviceId){return this.managedTransaction((tables,put)=>{
  const r=validateRoster(typeof text==='string'?JSON.parse(text):text);memberFor(r,ownDeviceId);
  const old=tables.rosters.find(x=>x.groupId===r.groupId);if(old&&!sameRecord(old,r))throw Error('異なる参加者設定が保存されています');
  if(!old)put('rosters',r);return r;
 });}
 quakeTransaction(input,localDeviceId,local){return this.managedTransaction((tables,put)=>{
  const r=tables.rosters.find(x=>x.groupId===input.groupId);if(!r)throw Error('参加者設定を読み込んでください');
  let e=validateQuake(r,input);const events=tables.quakes.filter(x=>x.groupId===r.groupId),old=events.find(x=>x.eventId===e.eventId);
  if(old){if(!sameRecord(old,e)&&!(local&&e.action==='END'&&!e.endedIncidentIds.length&&sameRecord({...old,endedIncidentIds:[]},e)))throw Error('同じIDに異なる災害記録があります');return false;}
  if(local&&e.action==='END')e={...e,endedIncidentIds:quakeStates(r,events).find(s=>s.incidentIds.includes(e.targetId))?.incidentIds.toSorted()||[]};
  if(local)validateQuakeCommand(r,events,e);
  events.push(e);put('quakes',e);
  if(localDeviceId){memberFor(r,localDeviceId);for(const f of formationCandidates(r,events)){
   const formed=validateQuake(r,{groupId:r.groupId,eventId:crypto.randomUUID(),actorDeviceId:localDeviceId,operatedAt:local?e.operatedAt:Date.now(),action:'FORM',training:f.training,targetId:f.incidentId,reportIds:f.reportIds});events.push(formed);put('quakes',formed);
  }}
  for(const state of quakeStates(r,events)){
   const report=events.find(x=>x.eventId===state.formation.incidentId);
   if(!tables.incidents.some(x=>x.groupId===r.groupId&&x.eventId===report.eventId))put('incidents',{groupId:r.groupId,eventId:report.eventId,incidentId:report.eventId,authorId:report.actorDeviceId,authorName:memberFor(r,report.actorDeviceId).activityName,sequence:1,createdAt:report.operatedAt,kind:'START',title:report.title,area:report.area,training:report.training,safety:null,subjectId:null,action:null,note:'',supersedes:null});
  }return true;
 });}
 mergeQuake(e,localDeviceId){return this.quakeTransaction(e,localDeviceId,false);}
 commandQuake(e){return this.quakeTransaction(e,e.actorDeviceId,true);}
 responseTransaction(input,local){return this.managedTransaction((tables,put)=>{
  const r=tables.rosters.find(x=>x.groupId===input.groupId);if(!r)throw Error('参加者設定を読み込んでください');
  const history=tables.responses.filter(x=>x.groupId===r.groupId);
  const e=validateResponse(r,local?{...input,sequence:history.filter(x=>x.actorDeviceId===input.actorDeviceId).reduce((n,x)=>Math.max(n,x.sequence),0)+1}:input);
  const old=history.find(x=>x.eventId===e.eventId);if(old){if(local||!sameRecord(old,e))throw Error('同じIDに異なる対応記録があります');return false;}
  if(history.some(x=>x.actorDeviceId===e.actorDeviceId&&x.sequence===e.sequence))throw Error('同じ更新番号に異なる対応記録があります');
  if(local){
   if(e.incidentId&&!quakeStates(r,tables.quakes).some(s=>s.incidentIds.includes(e.incidentId)&&!s.closed))throw Error('成立済みで開始中の災害を指定してください');
   const t=e.change.target;
   if(t?.kind==='PERSON'&&!tables.residents.some(x=>x.groupId===r.groupId&&x.originId===t.id))throw Error('対象住民が見つかりません');
   if(t?.kind==='REPORT'&&!history.some(x=>x.eventId===t.id&&canonicalIncidentId(r,tables.quakes,x.incidentId)===canonicalIncidentId(r,tables.quakes,e.incidentId)&&x.change.type==='REPORT'))throw Error('対象の通報が見つかりません');
   validateResponseCommand(r,history.map(x=>({...x,incidentId:canonicalIncidentId(r,tables.quakes,x.incidentId)})),{...e,incidentId:canonicalIncidentId(r,tables.quakes,e.incidentId)});
  }put('responses',e);return local?e:true;
 });}
 mergeResponse(e){return this.responseTransaction(e,false);}
 appendResponse(e){return this.responseTransaction(e,true);}
 async responseHeads(group){const roster=await this.roster(group);const quakes=await this.quakeEvents(group);return roster?responseHeads(roster,(await this.responseEvents(group)).map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)}))):[];}
}
