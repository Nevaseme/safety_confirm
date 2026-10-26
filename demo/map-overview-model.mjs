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
