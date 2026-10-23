const uuid=/^[0-9a-fA-F-]{36}$/;
const require=(condition,message='位置・メモの内容が不正です')=>{if(!condition)throw Error(message);};
const positionFields=['groupId','originId','revision','acquiredAt','latitude','longitude','accuracyMeters'];
const memoFields=['groupId','incidentId','authorId','answerId','revision','updatedAt','text'];
export function validateLocal(input,position){
 require(input&&/^[A-Za-z0-9_-]{1,40}$/.test(input.groupId));
 require((position?[input.originId]:[input.incidentId,input.authorId,input.answerId]).every(id=>typeof id==='string'&&uuid.test(id)));
 require(Number.isSafeInteger(input.revision)&&input.revision>0);
 const time=position?input.acquiredAt:input.updatedAt;require(Number.isSafeInteger(time)&&time>=0);
 if(position)require(Number.isFinite(input.latitude)&&Math.abs(input.latitude)<=90&&Number.isFinite(input.longitude)&&Math.abs(input.longitude)<=180&&Number.isFinite(input.accuracyMeters)&&input.accuracyMeters>=0);
 else require(typeof input.text==='string'&&input.text.length<=500);
 return Object.fromEntries((position?positionFields:memoFields).map(key=>[key,input[key]]));
}
export function newerLocal(previous,input,position){
 const next=validateLocal(input,position);if(!previous)return next;
 require(previous.groupId===next.groupId);
 if(position)require(previous.originId===next.originId);
 else require(['incidentId','authorId','answerId'].every(key=>previous[key]===next[key]),'SOSメモの作成者・回答は変更できません');
 if(previous.revision===next.revision)require((position?positionFields:memoFields).every(key=>previous[key]===next[key]),'同じ更新番号に異なる内容があります');
 return previous.revision<next.revision?next:undefined;
}
