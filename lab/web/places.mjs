const uuid=/^[0-9a-fA-F-]{36}$/;
const require=(ok,message)=>{if(!ok)throw Error(message);};
export const BUILDING_ACTIONS={CHECKING:'確認中',NEEDS_HELP:'要対応',COMPLETED:'対応完了の記録',RECHECK:'再確認が必要',REVOKED:'記録を撤回'};
const fields=['groupId','eventId','authorId','authorName','sequence','createdAt','kind','buildingId','unitId','incidentId','label','latitude','longitude','action','scope','unresolved','note','supersedes'];
export function normalizePlace(e){return {buildingId:null,unitId:null,incidentId:null,label:'',latitude:null,longitude:null,action:null,scope:'',unresolved:'',note:'',supersedes:null,...Object.fromEntries(fields.filter(k=>Object.hasOwn(e,k)).map(k=>[k,e[k]]))};}
export const samePlace=(a,b)=>JSON.stringify(normalizePlace(a))===JSON.stringify(normalizePlace(b));
export function validatePlace(input){
 const e=normalizePlace(input);
 require(/^[A-Za-z0-9_-]{1,40}$/.test(e.groupId)&&[e.eventId,e.authorId].every(x=>typeof x==='string'&&uuid.test(x)),'建物記録のIDが不正です');
 require([e.buildingId,e.unitId,e.incidentId,e.supersedes].every(x=>x===null||typeof x==='string'&&uuid.test(x)),'関連先のIDが不正です');
 require(Number.isSafeInteger(e.sequence)&&e.sequence>0&&Number.isSafeInteger(e.createdAt)&&e.createdAt>=0,'建物記録の更新情報が不正です');
 for(const [key,max]of [['authorName',60],['label',80],['scope',240],['unresolved',240],['note',500]])require(typeof e[key]==='string'&&e[key].length<=max,'建物記録の文字数を確認してください');
 require(e.authorName.trim(),'記録者を登録してください');
 require(['BUILDING','UNIT','HOME','CARE'].includes(e.kind),'建物記録の種類が不正です');
 if(e.kind==='BUILDING')require(e.buildingId&&e.unitId===null&&e.label.trim()&&Number.isFinite(e.latitude)&&Math.abs(e.latitude)<=85&&Number.isFinite(e.longitude)&&Math.abs(e.longitude)<=180,'建物名と緯度・経度を確認してください');
 else {require(e.latitude===null&&e.longitude===null,'不要な位置情報です');if(e.kind==='UNIT')require(e.buildingId&&e.unitId&&e.label.trim(),'建物と住戸名を指定してください');else require(e.label==='','不要な名称です');}
 if(e.kind==='HOME')require(e.buildingId!==null||e.unitId===null,'住戸には建物が必要です');
 if(e.kind==='CARE')require(e.buildingId&&e.unitId===null&&e.incidentId&&Object.hasOwn(BUILDING_ACTIONS,e.action)&&e.scope.trim()&&e.unresolved.trim()&&e.supersedes!==e.eventId&&(e.action!=='REVOKED'||e.supersedes),'確認範囲・未確認事項を入力し、訂正元を確認してください');
 else require(e.incidentId===null&&e.action===null&&e.scope===''&&e.unresolved===''&&e.note===''&&e.supersedes===null,'不要な対応情報です');
 return e;
}
function definitions(rows,kind,key){
 return rows.filter(e=>e.kind===kind&&e.eventId===e[key]).map(root=>rows.filter(e=>e.kind===kind&&e[key]===root[key]&&e.groupId===root.groupId&&e.authorId===root.authorId&&(kind!=='UNIT'||e.buildingId===root.buildingId)).reduce((a,b)=>a.sequence>b.sequence?a:b,root));
}
export const placeBuildings=rows=>definitions(rows,'BUILDING','buildingId').sort((a,b)=>a.label.localeCompare(b.label,'ja')||a.buildingId.localeCompare(b.buildingId));
export const placeUnits=(rows,buildingId)=>definitions(rows,'UNIT','unitId').filter(e=>e.buildingId===buildingId).sort((a,b)=>a.label.localeCompare(b.label,'ja')||a.unitId.localeCompare(b.unitId));
export const homeFor=(rows,authorId)=>rows.filter(e=>e.kind==='HOME'&&e.authorId===authorId).reduce((a,b)=>!a||a.sequence<b.sequence?b:a,null);
export function validHome(rows,authorId){const h=homeFor(rows,authorId);return h&&placeBuildings(rows).some(b=>b.buildingId===h.buildingId)&&(!h.unitId||placeUnits(rows,h.buildingId).some(u=>u.unitId===h.unitId))?h:null;}
export const buildingCare=(rows,buildingId,incidentId)=>rows.filter(e=>e.kind==='CARE'&&e.buildingId===buildingId&&e.incidentId===incidentId);
export function currentBuildingCare(rows){const byId=new Map(rows.map(e=>[e.eventId,e]));const replaced=new Set(rows.filter(e=>{const p=byId.get(e.supersedes);return p&&p.groupId===e.groupId&&p.authorId===e.authorId&&p.buildingId===e.buildingId&&p.incidentId===e.incidentId&&p.sequence<e.sequence;}).map(e=>e.supersedes));return rows.filter(e=>!replaced.has(e.eventId));}
export function preparePlace(input,rows,incidents){
 const e=normalizePlace(input),buildings=placeBuildings(rows),building=buildings.find(b=>b.buildingId===e.buildingId);
 if(e.kind==='BUILDING'){if(building)require(building.authorId===e.authorId,'建物を訂正できるのは登録者だけです');else require(e.eventId===e.buildingId,'建物IDを確認してください');}
 else if(e.kind!=='HOME'||e.buildingId)require(building,'建物が未受信です。再接続してください');
 if(e.kind==='UNIT'){const unit=placeUnits(rows,e.buildingId).find(u=>u.unitId===e.unitId);if(unit)require(unit.authorId===e.authorId,'住戸を訂正できるのは登録者だけです');else require(e.unitId===e.eventId,'住戸IDを確認してください');}
 if(e.kind==='HOME'&&e.unitId)require(placeUnits(rows,e.buildingId).some(u=>u.unitId===e.unitId),'選んだ建物の住戸を指定してください');
 if(e.kind==='CARE'){
  const start=incidents.find(x=>x.kind==='START'&&x.eventId===e.incidentId);require(start&&!incidents.some(x=>x.kind==='END'&&x.incidentId===e.incidentId&&x.authorId===start.authorId),'開始中の災害を選んでください');
  if(e.supersedes)require(buildingCare(rows,e.buildingId,e.incidentId).some(p=>p.eventId===e.supersedes&&p.authorId===e.authorId),'同じ建物・災害の自分の記録だけ訂正できます');
 }
 return validatePlace({...e,sequence:Math.max(0,...rows.filter(x=>x.authorId===e.authorId).map(x=>x.sequence))+1});
}
