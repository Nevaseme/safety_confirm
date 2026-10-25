// Browser-memory fixtures for the public demo; no Android records or live services.
export const CATEGORIES=['SOS','道路閉塞','建物倒壊','その他'];
export const WORK_STATUSES=['未対応','対応予定','対応中','対応済み'];
export function updateWorkProgress(row,status,result=''){
  if(!WORK_STATUSES.includes(status))throw Error('対応状況を選択してください');
  const text=result.trim();
  if(status==='対応済み'&&!text)throw Error('対応済みにする場合は結果を入力してください');
  row.status=status;row.result=text;
}
export function startWork(row){
  if(!row.assignees.includes('self'))row.assignees.push('self');
  if(['未対応','対応予定'].includes(row.status))row.status='対応中';
}
export const REGIONS=[
  {id:'city',name:'架空市',parentId:null,depth:0},
  {id:'east-ward',name:'東区',parentId:'city',depth:1},
  {id:'east',name:'東町内会',parentId:'east-ward',depth:2},
  {id:'central',name:'中央町内会',parentId:'east-ward',depth:2},
  {id:'west-ward',name:'西区',parentId:'city',depth:1},
  {id:'west',name:'西町内会',parentId:'west-ward',depth:2}
];
export const MEMBERS=[{id:'self',name:'自分'},{id:'north',name:'北班'},{id:'contact',name:'連絡班'}];
export const caseCategory=value=>CATEGORIES.includes(value)?value:'その他';
export const regionName=id=>REGIONS.find(r=>r.id===id)?.name||'地域未設定';
export const memberName=id=>MEMBERS.find(r=>r.id===id)?.name||id;
export function workSummary(row){return {status:row.cancelled?'取り消し済み':row.status,assignment:row.assignees.length?'担当：'+row.assignees.map(memberName).join('・'):'担当者無し'};}
export function cancelReport(row,reason){
  if(row.personId)throw Error('SOSは本人の安否画面で更新します');
  if(!reason?.trim())throw Error('取り消しの理由を入力してください');
  row.cancelled=true;row.cancelReason=reason.trim();row.updatedAt='10月26日 15:00';
  (row.history??=[]).push({text:'通報を取り消し：'+row.cancelReason,author:'自分',at:row.updatedAt});
}
export function restoreReport(row){
  row.cancelled=false;row.updatedAt='10月26日 15:00';
  (row.history??=[]).push({text:'通報の取り消しを戻す',author:'自分',at:row.updatedAt});
}
export function seedCases(people,{fourStage=false}={}){
  const common={status:'未対応',result:'',urgency:'未設定',reason:'',createdBy:'north',updatedAt:'10月26日 14:45'};
  const sos=people.filter(p=>p.s==='sos').map((p,i)=>({...common,id:`sos-${p.id}`,personId:p.id,title:p.name||'SOSの人',description:p.memo||'助けを求めています。',category:'SOS',regionIds:[['east','central','west'][i%3]],assignees:i===0?[]:i===2?['self']:['north'],status:i===2?'対応中':'未対応',urgency:i===0?'高':'未設定',reason:i===0?'けがにより移動が困難':'',materials:[],history:[]}));
  const reports=[
    {id:'road-1',category:'道路閉塞',title:'道路閉塞',description:'公民館の南側。倒木で通れません。',regionIds:['east'],assignees:[]},
    {id:'building-1',category:'建物倒壊',title:'建物倒壊',description:'中央通りの空き家が倒壊しています。',regionIds:['central'],assignees:['north']},
    {id:'road-2',category:'道路閉塞',title:'道路閉塞',description:'西町の交差点。ブロック塀が道路を塞いでいます。',regionIds:['west'],assignees:['contact']},
    {id:'other-1',category:'断水',title:'断水',description:'集会所の水道が使えません。',regionIds:['west'],assignees:['contact']}
  ].map(r=>({...common,...r,materials:[],history:[]}));
  const rows=[...sos,...reports];
  if(fourStage){
    for(const row of rows)if(row.status==='未対応'&&row.assignees.length)row.status='対応予定';
    const road=rows.find(r=>r.id==='road-2');road.status='対応中';
    const other=rows.find(r=>r.id==='other-1');other.status='対応済み';other.result='集会所の給水を再開し、利用できることを確認しました。';
  }
  return rows;
}
export function filterCases(rows,filter='all',member='self'){
  return rows.filter(r=>!r.cancelled).filter(r=>filter==='none'?r.assignees.length===0:filter==='mine'?r.assignees.includes(member):true);
}
export function summarizeCases(rows,regionId='all',type=null){
  const unique=new Map();
  for(const row of rows.filter(r=>!r.cancelled)){
    const old=unique.get(row.id);
    unique.set(row.id,{...row,regionIds:[...new Set([...(old?.regionIds||[]),...(row.regionIds||[])])]});
  }
  const selected=[...unique.values()].filter(row=>{
    if(regionId==='all')return true;
    if(regionId==='unassigned')return row.regionIds.length===0;
    return row.regionIds.some(id=>{
      for(let region=REGIONS.find(r=>r.id===id);region;region=REGIONS.find(r=>r.id===region.parentId))if(region.id===regionId)return true;
      return false;
    });
  }).filter(row=>!type||caseCategory(row.category)===type);
  const counts=Object.fromEntries(CATEGORIES.map(t=>[t,selected.filter(r=>caseCategory(r.category)===t).length]));
  return {total:selected.length,counts,ids:selected.map(r=>r.id)};
}
export function saveMaterial(row,value){
  if(!value.name?.trim()||!value.unit?.trim()||![value.needed,value.arranged].every(n=>Number.isFinite(n)&&n>=0))throw Error('品名・単位と、0以上の数量を入力してください');
  const previous=row.materials.find(r=>r.id===value.id);
  if(previous&&previous.unit!==value.unit)throw Error('単位を変える場合は別の要請として登録してください');
  const next={...value,shortfall:Math.max(0,value.needed-value.arranged)};
  if(previous)row.materials.splice(row.materials.indexOf(previous),1,next);else row.materials.push(next);
  return next;
}
export function caseTitle(row,people=[]){
  const person=people.find(p=>p.id===row.personId);
  return person&&['safe','rescued'].includes(person.s)?(person.s==='safe'?'無事の人':'救助済みの人'):row.title;
}
export function casesCsv(rows,people=[]){
  const quote=value=>{let text=String(value??'');if(/^[\s]*[=+@-]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"';};
  const headers=['地域','対象ID','種別','氏名・対象','状況','緊急度','担当の活動名','対応結果','内容','必要資材','不足数','更新日時'];
  const values=rows.filter(r=>!r.cancelled).map(r=>[(r.regionIds||[]).map(regionName).join('／')||'地域未設定',r.id,caseCategory(r.category),caseTitle(r,people),r.status,r.urgency,(r.assignees||[]).map(memberName).join('／'),r.result,r.description,(r.materials||[]).map(m=>`${m.name} ${m.needed}${m.unit}（手配済み ${m.arranged}${m.unit}）`).join('／'),(r.materials||[]).map(m=>`${m.name} ${Math.max(0,m.needed-m.arranged)}${m.unit}`).join('／'),r.updatedAt]);
  return '\uFEFF'+[headers,...values].map(row=>row.map(quote).join(',')).join('\r\n')+'\r\n';
}
