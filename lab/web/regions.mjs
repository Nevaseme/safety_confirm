const check=(ok,message)=>{if(!ok)throw Error(message);};
export function validateRegionCatalog(input){
 const c={version:1,...input};check(c.version===1&&Array.isArray(c.regions),'地域設定の形式が不正です');
 const byId=new Map(c.regions.map(r=>[r.id,r]));check(byId.size===c.regions.length,'地域IDが重複しています');
 for(const r of c.regions){
  check(typeof r.id==='string'&&/^[A-Za-z0-9_-]{1,80}$/.test(r.id)&&typeof r.name==='string'&&r.name.trim()&&r.name.length<=120,'地域のIDと名称を確認してください');
  const visited=new Set();let next=r.id;
  while(next!=null){check(!visited.has(next),'地域の所属が循環しています');visited.add(next);const parent=byId.get(next);check(parent,'所属先の地域が見つかりません');next=parent.parentId;}
 }return c;
}
export function regionalSummary(input,cases){
 const catalog=validateRegionCatalog(input),byId=new Map(catalog.regions.map(r=>[r.id,r]));
 const counts=new Map(catalog.regions.map(r=>[r.id,new Map()])),grouped=new Map(),unassigned=[];
 for(const c of cases){
  check(c.target&&['PERSON','REPORT'].includes(c.target.kind)&&/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(c.target.id)&&Array.isArray(c.regionIds),'案件の形式が不正です');
  const key=`${c.target.kind}:${c.target.id}`,entry=grouped.get(key)||{target:c.target,ids:new Set()};for(const id of c.regionIds)entry.ids.add(id);grouped.set(key,entry);
 }
 for(const [key,entry] of grouped){
  if(!entry.ids.size)unassigned.push(entry.target);
  for(let next of entry.ids){while(next!=null){const region=byId.get(next);check(region,'案件の地域が見つかりません');counts.get(next).set(key,entry.target);next=region.parentId;}}
 }
 return {regions:catalog.regions.map(r=>({regionId:r.id,name:r.name,count:counts.get(r.id).size,targets:[...counts.get(r.id).values()]})),unassigned};
}
