import {STATES} from './clustering.mjs?v=6';
import {REGIONS} from './operations-model.mjs?v=4';

export function townSummary(regionId,people,rows){
  const cases=[...new Map(rows.filter(r=>!r.cancelled).map(r=>[r.id,r])).values()].filter(r=>r.regionIds.includes(regionId));
  const active=cases.filter(r=>r.status!=='対応済み');
  const shortages=new Map();
  for(const row of active)for(const m of row.materials||[]){
    const quantity=Math.max(0,m.needed-m.arranged);if(!quantity)continue;
    const name=m.name.trim(),unit=m.unit.trim(),key=JSON.stringify([name,unit]);
    const existing=shortages.get(key);if(existing)existing.quantity+=quantity;else shortages.set(key,{name,unit,quantity});
  }
  return {regionId,name:REGIONS.find(r=>r.id===regionId)?.name||regionId,safety:Object.fromEntries(STATES.map(s=>[s,people.filter(p=>p.regionId===regionId&&p.s===s).length])),highReports:active.filter(r=>!r.personId&&r.urgency==='高').length,needsMaterials:shortages.size>0,shortages:[...shortages.values()]};
}

function node(items){
  const grouped=items.length>1;
  return {items,x:items.reduce((s,i)=>s+i.x,0)/items.length,y:items.reduce((s,i)=>s+i.y,0)/items.length,width:grouped?112:items[0].width,height:grouped?48:items[0].height,sos:items.filter(i=>i.kind==='sos').length,reports:items.filter(i=>i.kind==='report').length,unanswered:items.filter(i=>i.kind==='unanswered').length};
}
export function clusterScreenItems(items){
  const nodes=items.map(i=>node([i]));
  let changed=true;
  while(changed){changed=false;outer:for(let a=0;a<nodes.length;a++)for(let b=a+1;b<nodes.length;b++){
    const p=nodes[a],q=nodes[b];
    if(Math.abs(p.x-q.x)<(p.width+q.width)/2+6&&Math.abs(p.y-q.y)<(p.height+q.height)/2+6){nodes[a]=node([...p.items,...q.items]);nodes.splice(b,1);changed=true;break outer;}
  }}
  return nodes;
}
