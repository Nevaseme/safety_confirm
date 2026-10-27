import {CENTER,NOW} from './clustering.mjs?v=7';

// Fictional geography for 1.6.7 only. Previous comparison data stays intact.
export function createTownFixtures(sourcePeople,sourceHomes,{observations=false}={}){
  const centers={west:[CENTER[0]-.0036,CENTER[1]],central:[...CENTER],east:[CENTER[0]+.0036,CENTER[1]]};
  const bounds=Object.fromEntries(Object.entries(centers).map(([id,[lng,lat]])=>[id,[[lng-.00145,lat-.0013],[lng+.00145,lat+.0013]]]));
  const states=['sos','unanswered','safe','safe','safe','rescued','safe','safe','unanswered','safe','safe','safe'];
  const ids=[...sourcePeople.map(p=>p.id),...Array.from({length:36-sourcePeople.length},(_,i)=>`town-person-${i}`)];
  const homes=[],people=[];
  for(const [townIndex,regionId] of ['east','central','west'].entries()){
    const center=centers[regionId];
    for(let h=0;h<4;h++){
      const id=h===0?{east:'sato',central:'central-home',west:'tanaka'}[regionId]:regionId==='east'&&h===1?'yamada':`${regionId}-home-${h}`;
      homes.push({...structuredClone(sourceHomes[0]),id,regionId,name:`架空町${101+townIndex*100+h}-1`,location:[center[0]+(h%2?1:-1)*.00065,center[1]+(h<2?1:-1)*.0005]});
    }
    const houses=homes.filter(h=>h.regionId===regionId);
    for(let i=0;i<12;i++){
      const id=ids[townIndex*12+i],home=houses[Math.floor(i/3)],unknown=i===1;
      const source=sourcePeople.find(p=>p.id===id)||sourcePeople[0];
      people.push({...structuredClone(source),id,regionId,homeId:home.id,address:home.name,name:`架空の住民 ${townIndex*12+i+1}`,s:states[i],location:unknown?null:[home.location[0]+(i%3-1)*.00015,home.location[1]+(i%2?1:-1)*.00012],acquiredAt:unknown?null:NOW-120000,bedroom:'1階・南側の部屋',memo:i===0?'出口がふさがれているため、救助を求めています。':'',impact:observations&&(i===0||i===8),enclosed:observations&&(i===0||i===1),history:[],priorSafety:undefined});
    }
  }
  const point=(regionId,x,y)=>({regionId,location:[centers[regionId][0]+x,centers[regionId][1]+y]});
  const reports={'road-1':point('east',0,.00085),'building-1':point('central',.0009,0),'road-2':point('west',0,.00085),'other-1':point('west',-.0009,0)};
  return {centers,bounds,people,homes,reports};
}
