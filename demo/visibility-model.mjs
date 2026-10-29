import {STATES,hasPosition,CENTER} from './clustering.mjs?v=6';
import {caseCategory,REGIONS,memberName} from './operations-model.mjs?v=4';

const towns=REGIONS.filter(r=>r.depth===2);
export const TOWN_CENTERS=Object.fromEntries(towns.map((r,i)=>[r.id,[CENTER[0]+(i-1)*.0025,CENTER[1]+.002]]));
export function viewingScope(viewer){
  const municipal=viewer.role==='municipal';
  return {label:municipal?'架空市全域':REGIONS.find(r=>r.id===viewer.regionId)?.name||viewer.regionId,townIds:municipal?towns.map(r=>r.id):[viewer.regionId]};
}
export function municipalMapBounds(reports,centers=TOWN_CENTERS){
  const points=[...Object.values(centers),...reports.filter(r=>!r.personId&&!r.cancelled&&Array.isArray(r.location)&&r.location.length===2&&r.location.every(Number.isFinite)).map(r=>r.location)];
  return [[Math.min(...points.map(p=>p[0])),Math.min(...points.map(p=>p[1]))],[Math.max(...points.map(p=>p[0])),Math.max(...points.map(p=>p[1]))]];
}

export function disclosureStart(timing){
  const times=[timing.eewReceivedAt,timing.firstReportedAt].filter(t=>Number.isFinite(t)&&t>=0);
  return times.length?Math.min(...times):null;
}
export function recordTrigger(timing,key,at){
  if(!['eewReceivedAt','firstReportedAt'].includes(key)||!Number.isFinite(at)||at<0)throw Error('時刻が不正です');
  timing[key]=Number.isFinite(timing[key])?Math.min(timing[key],at):at;
}
export function canSeePerson(person,viewer,timing){
  if(viewer.role==='municipal'||person.regionId!==viewer.regionId)return false;
  const start=disclosureStart(timing);
  return person.s!=='unanswered'||(start!==null&&timing.now>=start+180000);
}
export function projectPerson(person,viewer,timing,{usualPlaces=false,hideSafeObservations=false}={}){
  if(!canSeePerson(person,viewer,timing))return null;
  const fields=['id','s','regionId','location','acquiredAt','accuracy','answeredAt','memo','impact','enclosed','impactAt','enclosedAt','history','priorSafety'];
  const output=Object.fromEntries(fields.filter(k=>person[k]!==undefined).map(k=>[k,person[k]]));
  if(hideSafeObservations&&person.s==='safe')for(const key of ['impact','enclosed','impactAt','enclosedAt'])delete output[key];
  output.name=['safe','rescued'].includes(person.s)?'':person.name||(person.id==='p5'?'自分':'登録住民');
  if(!hasPosition(person)||(usualPlaces&&person.s==='unanswered')){
    for(const k of ['address','homeId','bedroom'])if(person[k]!==undefined)output[k]=person[k];
    if(usualPlaces&&person.daytimePlace!==undefined)output.daytimePlace=person.daytimePlace;
  }
  return output;
}
export function projectMapPerson(person,viewer,timing,options={}){
  const visible=projectPerson(person,viewer,timing,options);
  if(visible)return visible;
  const start=disclosureStart(timing);
  if(!options.anonymousWaitingMap||!['resident','area'].includes(viewer.role)||person.regionId!==viewer.regionId||person.s!=='unanswered'||!hasPosition(person)||start===null||timing.now>=start+180000)return null;
  return {id:person.id,s:'unanswered',name:'応答なしの人',location:[...person.location],acquiredAt:person.acquiredAt,impact:Boolean(person.impact),enclosed:Boolean(person.enclosed),mapDisclosurePending:true};
}
export function projectReport(row,viewer,people,timing){
  const person=row.personId?people.find(p=>p.id===row.personId):null;
  if(row.personId&&(!person||!canSeePerson(person,viewer,timing)))return null;
  const fields=['id','personId','title','category','description','regionIds','assignees','status','result','urgency','reason','updatedAt','materials','history','cancelled','cancelReason','location'];
  const output=Object.fromEntries(fields.filter(k=>row[k]!==undefined).map(k=>[k,row[k]]));
  if(person)output.title=['safe','rescued'].includes(person.s)?(person.s==='safe'?'無事の人':'救助済みの人'):person.name||(person.id==='p5'?'自分':'登録住民');
  if(!row.personId&&row.reporter){
    output.reporter={address:row.reporter.address};
    if(viewer.role==='municipal'||(viewer.role==='area'&&viewer.regionId===row.reporter.regionId))output.reporter.name=row.reporter.name;
  }
  return output;
}
export function safetyStatistics(people){
  return REGIONS.filter(r=>r.depth===2).map(region=>({regionId:region.id,name:region.name,counts:Object.fromEntries(STATES.map(s=>[s,people.filter(p=>p.regionId===region.id&&p.s===s).length]))}));
}
const quote=value=>{let s=String(value??'');if(/^\s*[=+@-]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';};
export function visibilityCsv(rows,viewer,people,timing){
  const header=['地域','対象ID','種別','氏名・対象','対応状況','緊急度','担当の活動名','対応結果','内容','通報者の住所','通報者氏名','必要資材','更新日時'];
  const values=rows.filter(r=>!r.cancelled).map(r=>projectReport(r,viewer,people,timing)).filter(Boolean).map(r=>[
    r.regionIds?.map(id=>REGIONS.find(g=>g.id===id)?.name||id).join('／'),r.id,caseCategory(r.category),r.title,r.status,r.urgency,r.assignees?.map(memberName).join('／'),r.result,r.description,r.reporter?.address,r.reporter?.name,
    r.materials?.map(m=>`${m.name} 必要${m.needed}${m.unit}・手配済み${m.arranged}${m.unit}・不足${Math.max(0,m.needed-m.arranged)}${m.unit}`).join('／'),r.updatedAt
  ]);
  return '\uFEFF'+[header,...values].map(line=>line.map(quote).join(',')).join('\r\n')+'\r\n';
}
export function statisticsCsv(people){
  return '\uFEFF'+[['町内会','SOS','反応なし','無事','救助済み'],...safetyStatistics(people).map(r=>[r.name,...STATES.map(s=>r.counts[s])])].map(row=>row.map(quote).join(',')).join('\r\n')+'\r\n';
}
export const reportIcon=urgency=>urgency==='高'?'assets/report-emergency.png':['中','低'].includes(urgency)?'assets/report-normal.png':null;
