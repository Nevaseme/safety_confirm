const uuid=/^[0-9a-fA-F-]{36}$/;
const ensure=(ok,msg)=>{if(!ok)throw Error(msg);};
const fields=['groupId','eventId','incidentId','authorId','authorName','sequence','createdAt','kind','title','area','training','safety','subjectId','action','note','supersedes'];
export function normalizeIncident(e){return {title:'',area:'',training:true,safety:null,subjectId:null,action:null,note:'',supersedes:null,...Object.fromEntries(fields.filter(k=>Object.hasOwn(e,k)).map(k=>[k,e[k]]))};}
export function validateIncident(input){
 const e=normalizeIncident(input);ensure(/^[A-Za-z0-9_-]{1,40}$/.test(e.groupId)&&[e.eventId,e.incidentId,e.authorId].every(x=>typeof x==='string'&&uuid.test(x)),'災害記録のIDが不正です');
 ensure(Number.isSafeInteger(e.sequence)&&e.sequence>0&&Number.isSafeInteger(e.createdAt)&&e.createdAt>=0,'災害記録の更新情報が不正です');
 for(const [key,max,empty] of [['authorName',60,false],['title',80,true],['area',120,true],['note',500,true]])ensure(typeof e[key]==='string'&&e[key].length<=max&&(empty||e[key].trim()),'災害記録の文字数を確認してください');
 ensure(typeof e.training==='boolean','訓練設定が不正です');
 if(e.kind==='START')ensure(e.eventId===e.incidentId&&e.sequence===1&&e.title.trim()&&e.area.trim(),'地震の名称と対象地域を入力してください');
 else ensure(e.eventId!==e.incidentId&&e.title===''&&e.area==='','災害記録の内容が不正です');
 ensure(['START','END','ANSWER','CARE'].includes(e.kind),'災害記録の種類が不正です');
 ensure(e.kind==='ANSWER'?['SAFE','SOS'].includes(e.safety):e.safety===null,'安否が不正です');
 if(e.kind==='CARE')ensure(uuid.test(e.subjectId)&&['CHECKING','NEEDS_HELP','RESCUED','REVOKED'].includes(e.action)&&(e.supersedes===null||(uuid.test(e.supersedes)&&e.supersedes!==e.eventId))&&(e.action!=='REVOKED'||e.supersedes!==null),'対応記録が不正です');
 else ensure(e.subjectId===null&&e.action===null&&e.supersedes===null&&e.note==='','災害記録の内容が不正です');
 return e;
}
export const sameIncident=(a,b)=>JSON.stringify(normalizeIncident(a))===JSON.stringify(normalizeIncident(b));
export function incidentView(rows,incidentId){
 const start=rows.find(e=>e.kind==='START'&&e.eventId===incidentId);if(!start)return null;
 const events=rows.filter(e=>e.incidentId===incidentId&&e.groupId===start.groupId);
 const answers={};for(const e of events.filter(e=>e.kind==='ANSWER'))if(!answers[e.authorId]||answers[e.authorId].sequence<e.sequence)answers[e.authorId]=e;
 return {start,closed:events.some(e=>e.kind==='END'&&e.authorId===start.authorId),answers,care:events.filter(e=>e.kind==='CARE').map(e=>({...e,subjectId:e.subjectId,action:e.action}))};
}
export function incidentOptions(rows){return rows.filter(e=>e.kind==='START').map(e=>incidentView(rows,e.eventId)).sort((a,b)=>b.start.createdAt-a.start.createdAt||a.start.eventId.localeCompare(b.start.eventId));}
export async function appendScopedIncident(store,scope,kind,extra={}){
 const {groupId,incidentId,authorId}=scope;
 const own=await store.get(groupId,authorId);if(!own)throw Error('先に登録してください');
 return store.appendIncident({groupId,eventId:crypto.randomUUID(),incidentId,authorId,authorName:own.name,createdAt:Date.now(),kind,...extra});
}
