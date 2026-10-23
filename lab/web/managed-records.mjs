import {incidentOptions} from './incidents.mjs';
// Shared experiment rules. Stored event IDs remain immutable across corrections and merged incidents.
const uuid=/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const check=(ok,message='記録の形式が不正です')=>{if(!ok)throw Error(message);};
const integer=n=>Number.isSafeInteger(n)&&n>=0;
const nonempty=s=>typeof s==='string'&&s.trim().length>0;
export const memberFor=(roster,device)=>{const member=roster.members.find(m=>m.deviceIds.includes(device));check(member,'参加者に登録されていない端末です');return member;};
export const requiredReporters=n=>Math.min(10,Math.max(2,Math.ceil(n/3)));
export function validateRoster(input){
 const r={version:1,...input};check(r.version===1&&/^[A-Za-z0-9_-]{1,40}$/.test(r.groupId)&&Array.isArray(r.members)&&r.members.length);
 const devices=r.members.flatMap(m=>m.deviceIds);check(new Set(devices).size===devices.length&&new Set(r.members.map(m=>m.memberId)).size===r.members.length);
 check(r.members.some(m=>m.role==='ADMIN'),'管理者が必要です');
 for(const m of r.members)check(uuid.test(m.memberId)&&nonempty(m.activityName)&&m.activityName.length<=60&&['MEMBER','LEADER','ADMIN'].includes(m.role)&&Array.isArray(m.deviceIds)&&m.deviceIds.length&&m.deviceIds.every(d=>uuid.test(d)));
 return r;
}
export function validateQuake(r,input){
 const e={targetId:null,reportIds:[],title:'',area:'',endedIncidentIds:[],...input};memberFor(r,e.actorDeviceId);
 check(e.groupId===r.groupId&&uuid.test(e.eventId)&&integer(e.operatedAt)&&typeof e.training==='boolean');
 check(e.targetId===null||uuid.test(e.targetId));check(Array.isArray(e.reportIds)&&e.reportIds.every(x=>uuid.test(x))&&new Set(e.reportIds).size===e.reportIds.length);
 check(Array.isArray(e.endedIncidentIds)&&e.endedIncidentIds.every(x=>uuid.test(x))&&new Set(e.endedIncidentIds).size===e.endedIncidentIds.length);
 check(e.action==='END'?(!e.endedIncidentIds.length||e.endedIncidentIds.includes(e.targetId)):!e.endedIncidentIds.length);
 if(e.action==='REPORT')check(e.targetId===null&&!e.reportIds.length&&nonempty(e.title)&&e.title.length<=80&&nonempty(e.area)&&e.area.length<=120);
 else {check(['CANCEL','FORM','END'].includes(e.action)&&e.targetId!==null&&e.targetId!==e.eventId&&!e.title&&!e.area);check(e.action==='FORM'?e.reportIds.length>0:e.reportIds.length===0);}
 if(e.action==='END')check(memberFor(r,e.actorDeviceId).role==='ADMIN','終了できるのは管理者だけです');
 return e;
}
const compare=(a,b)=>a.operatedAt-b.operatedAt||(a.eventId<b.eventId?-1:a.eventId>b.eventId?1:0);
function proof(r,events,e){
 if(e.action!=='FORM')return null;
 const reports=e.reportIds.map(id=>events.find(x=>x.eventId===id&&x.action==='REPORT'&&x.groupId===r.groupId));
 if(reports.some(x=>!x))return null;reports.sort(compare);
 if(!reports.length||reports[0].eventId!==e.targetId||reports.some(x=>x.training!==e.training)||reports.at(-1).operatedAt-reports[0].operatedAt>1800000||new Set(reports.map(x=>memberFor(r,x.actorDeviceId).memberId)).size<requiredReporters(r.members.length))return null;
 return {groupId:r.groupId,incidentId:reports[0].eventId,firstReportedAt:reports[0].operatedAt,training:e.training,reportIds:reports.map(x=>x.eventId)};
}
export function quakeStates(r,events){
 const relevant=events.filter(e=>e.groupId===r.groupId).map(e=>validateQuake(r,e));
 const forms=relevant.map(e=>proof(r,relevant,e)).filter(Boolean).sort((a,b)=>a.firstReportedAt-b.firstReportedAt||(a.incidentId<b.incidentId?-1:a.incidentId>b.incidentId?1:0)||a.reportIds.join().localeCompare(b.reportIds.join()));
 const states=[];
 for(const training of [false,true]){
  const batches=[];for(const f of forms.filter(x=>x.training===training)){const last=batches.at(-1);if(!last||f.firstReportedAt-last[0].firstReportedAt>1800000)batches.push([f]);else last.push(f);}
  for(const batch of batches){const incidentIds=[...new Set(batch.map(f=>f.incidentId))];states.push({formation:batch[0],incidentIds,closed:relevant.some(e=>e.action==='END'&&e.training===training&&(incidentIds.includes(e.targetId)||e.endedIncidentIds.some(id=>incidentIds.includes(id))))});}
 }
 return states.sort((a,b)=>a.formation.firstReportedAt-b.formation.firstReportedAt||(a.formation.incidentId<b.formation.incidentId?-1:1));
}
export function formationCandidates(r,events){
 const reports=events.filter(e=>e.groupId===r.groupId&&e.action==='REPORT').sort(compare);
 const formed=events.map(e=>proof(r,events,e)).filter(Boolean);
 const cancelled=new Set(events.filter(e=>e.action==='CANCEL'&&e.groupId===r.groupId&&reports.some(x=>x.eventId===e.targetId&&x.training===e.training&&memberFor(r,x.actorDeviceId).memberId===memberFor(r,e.actorDeviceId).memberId)).map(e=>e.targetId));
 const available=reports.filter(e=>!cancelled.has(e.eventId)&&!formed.some(f=>f.reportIds.includes(e.eventId)||(f.training===e.training&&e.operatedAt>=f.firstReportedAt&&e.operatedAt-f.firstReportedAt<=1800000)));
 const result=[];for(const training of [false,true]){
  const batches=[];for(const e of available.filter(x=>x.training===training)){const last=batches.at(-1);if(!last||e.operatedAt-last[0].operatedAt>1800000)batches.push([e]);else last.push(e);}
  for(const batch of batches){const members=new Set(),evidence=[];for(const e of batch){const id=memberFor(r,e.actorDeviceId).memberId;if(!members.has(id)){members.add(id);evidence.push(e);}}
   if(members.size>=requiredReporters(r.members.length))result.push({groupId:r.groupId,incidentId:evidence[0].eventId,firstReportedAt:evidence[0].operatedAt,training,reportIds:evidence.slice(0,requiredReporters(r.members.length)).map(e=>e.eventId)});
  }
 }return result;
}
export function validateQuakeCommand(r,events,input){
 const e=validateQuake(r,input),states=quakeStates(r,events);
 if(e.action==='CANCEL'){
  const report=events.find(x=>x.eventId===e.targetId&&x.action==='REPORT'&&x.groupId===e.groupId);
  check(report&&report.training===e.training&&memberFor(r,report.actorDeviceId).memberId===memberFor(r,e.actorDeviceId).memberId,'自分の報告だけ取り消せます');
  check(!events.some(x=>{const f=proof(r,events,x);return f&&(f.reportIds.includes(report.eventId)||(report.training===f.training&&report.operatedAt>=f.firstReportedAt&&report.operatedAt-f.firstReportedAt<=1800000));}),'成立した報告は取り消せません');
 }else if(e.action==='FORM')check(formationCandidates(r,events).some(f=>sameRecord(f,proof(r,events,e))),'成立に必要な報告がありません');
 else if(e.action==='END')check(states.some(s=>s.incidentIds.includes(e.targetId)&&e.endedIncidentIds.every(id=>s.incidentIds.includes(id))&&s.formation.training===e.training&&!s.closed),'開始中の災害を指定してください');
 return e;
}
const ordered=value=>Array.isArray(value)?value.map(ordered):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(k=>[k,ordered(value[k])])):value;
export const sameRecord=(a,b)=>JSON.stringify(ordered(a))===JSON.stringify(ordered(b));
export function validateResponse(r,input){
 const e={replaces:[],...input},c=e.change;memberFor(r,e.actorDeviceId);
 check(new TextEncoder().encode(JSON.stringify(e)).length<=22000,'記録が通信可能なサイズを超えています。本文または訂正元の数を減らしてください');
 check(e.groupId===r.groupId&&uuid.test(e.eventId)&&integer(e.sequence)&&e.sequence>0&&integer(e.operatedAt)&&c);
 check(Array.isArray(e.replaces)&&new Set(e.replaces).size===e.replaces.length&&e.replaces.every(x=>uuid.test(x)&&x!==e.eventId));
 check(c.type==='AVAILABILITY'?e.incidentId===null:uuid.test(e.incidentId));
 if(c.target)check(['PERSON','REPORT'].includes(c.target.kind)&&uuid.test(c.target.id));
 switch(c.type){
  case 'REPORT':check(uuid.test(c.reportId)&&nonempty(c.category)&&nonempty(c.description)&&Number.isFinite(c.latitude)&&Math.abs(c.latitude)<=85&&Number.isFinite(c.longitude)&&Math.abs(c.longitude)<=180);break;
  case 'ASSIGNMENT':check(c.target&&r.members.some(m=>m.memberId===c.memberId)&&typeof c.active==='boolean'&&(c.memberId===memberFor(r,e.actorDeviceId).memberId||['ADMIN','LEADER'].includes(memberFor(r,e.actorDeviceId).role)));break;
  case 'PROGRESS':check(c.target&&['UNASSIGNED','IN_PROGRESS','DONE'].includes(c.status)&&typeof c.result==='string'&&(c.status!=='DONE'||nonempty(c.result)));break;
  case 'PRIORITY':check(c.target&&['ADMIN','LEADER'].includes(memberFor(r,e.actorDeviceId).role)&&['UNSET','LOW','MEDIUM','HIGH'].includes(c.urgency)&&typeof c.reason==='string'&&(c.urgency==='UNSET'||nonempty(c.reason)));break;
  case 'AVAILABILITY':check(['UNSET','AVAILABLE','WORKING','UNAVAILABLE'].includes(c.state));break;
  case 'MATERIAL':check(c.target&&uuid.test(c.requestId)&&nonempty(c.name)&&nonempty(c.unit)&&Number.isFinite(c.needed)&&c.needed>=0&&Number.isFinite(c.arranged)&&c.arranged>=0);break;
  default:throw Error('対応記録の種別が不正です');
 }return e;
}
export function responseKey(r,e){
 const c=e.change,t=c.target;const value=c.type==='REPORT'?`report:${c.reportId}`:c.type==='MATERIAL'?`material:${c.requestId}`:c.type==='AVAILABILITY'?`availability:${memberFor(r,e.actorDeviceId).memberId}`:`${c.type.toLowerCase()}:${t.kind}:${t.id}${c.type==='ASSIGNMENT'?':'+c.memberId:''}`;
 return `${e.groupId}:${e.incidentId??''}:${value}`;
}
function authorize(r,history,e){
 validateResponse(r,e);const member=memberFor(r,e.actorDeviceId),coordinator=['ADMIN','LEADER'].includes(member.role),c=e.change;
 const parents=e.replaces.map(id=>history.find(x=>x.eventId===id&&x.groupId===e.groupId));check(parents.every(p=>p&&responseKey(r,p)===responseKey(r,e)),'訂正元がありません');
 function creator(id){if(e.eventId===id){check(!e.replaces.length);return member;}const root=history.find(x=>x.eventId===id&&x.groupId===e.groupId&&x.incidentId===e.incidentId);check(root&&responseKey(r,root)===responseKey(r,e)&&e.replaces.length);return memberFor(r,root.actorDeviceId);}
 if(c.type==='REPORT')check(creator(c.reportId).memberId===member.memberId||coordinator,'通報を訂正する権限がありません');
 else if(c.type==='ASSIGNMENT')check(c.memberId===member.memberId||coordinator,'他の人の担当は班長・管理者が調整します');
 else if(c.type==='PRIORITY')check(coordinator,'緊急度は班長・管理者が変更します');
 else if(c.type==='MATERIAL'){check(creator(c.requestId).memberId===member.memberId||coordinator);const original=history.find(x=>x.eventId===c.requestId)?.change;if(original)check(original.unit===c.unit&&sameRecord(original.target,c.target));}
 else if(c.type==='AVAILABILITY')check(parents.every(p=>memberFor(r,p.actorDeviceId).memberId===member.memberId));
}
export function responseHeads(r,events){
 const waiting=events.filter(e=>e.groupId===r.groupId).sort((a,b)=>a.eventId<b.eventId?-1:1),accepted=[];let changed=true;
 while(changed){changed=false;for(let i=0;i<waiting.length;){const e=waiting[i];try{authorize(r,accepted,e);}catch{i++;continue;}accepted.push(e);waiting.splice(i,1);changed=true;}}
 const replaced=new Set(accepted.flatMap(e=>e.replaces));return accepted.filter(e=>!replaced.has(e.eventId)).sort((a,b)=>a.eventId<b.eventId?-1:1);
}
export function validateResponseCommand(r,events,e){authorize(r,events,e);const heads=new Set(responseHeads(r,events).map(x=>x.eventId));check(e.replaces.every(id=>heads.has(id)),'記録が更新されています');return e;}
export function managedIncidentOptions(journal,roster,quakes){
 if(!roster)return incidentOptions(journal);
 const states=quakeStates(roster,quakes),originals=incidentOptions(journal),ids=new Set(states.flatMap(s=>s.incidentIds));
 const views=states.flatMap(state=>{
  const related=originals.filter(v=>state.incidentIds.includes(v.start.eventId)),canonical=related.find(v=>v.start.eventId===state.formation.incidentId);if(!canonical)return [];
  const answers={};for(const answer of related.flatMap(v=>Object.values(v.answers)).sort((a,b)=>a.createdAt-b.createdAt||a.sequence-b.sequence||(a.eventId<b.eventId?-1:1)))answers[answer.authorId]=answer;
  return [{...canonical,closed:state.closed,answers,care:related.flatMap(v=>v.care)}];
 });return [...views,...originals.filter(v=>!ids.has(v.start.eventId)).map(v=>({...v,closed:true}))].sort((a,b)=>b.start.createdAt-a.start.createdAt||(a.start.eventId<b.start.eventId?-1:1));
}
export const canonicalIncidentId=(roster,quakes,id)=>roster?quakeStates(roster,quakes).find(s=>s.incidentIds.includes(id))?.formation.incidentId??id:id;
