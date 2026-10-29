import {CENTER,NOW} from './clustering.mjs?v=7';

// Fictional geography for 1.6.7 only. Previous comparison data stays intact.
export function createTownFixtures(sourcePeople,sourceHomes,{observations=false,namedResidents=false,allSafetyPatterns=false}={}){
  const names=[
    '山田 太郎','山田 花子','山田 陽菜','佐藤 健一','佐藤 美咲','佐藤 翔太',
    '鈴木 和也','鈴木 恵子','鈴木 悠真','高橋 誠','高橋 陽子','高橋 結衣',
    '伊藤 和夫','伊藤 美智子','伊藤 蓮','渡辺 直樹','渡辺 由美','渡辺 芽衣',
    '中村 秀夫','中村 千恵','中村 大輝','小林 哲也','小林 真由','小林 凛',
    '加藤 修','加藤 恵里奈','加藤 湊','吉田 隆','吉田 久美子','吉田 葵',
    '山本 聡','山本 裕子','山本 陸','松本 徹','松本 佳子','松本 紬'
  ];
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
      people.push({...structuredClone(source),id,regionId,homeId:home.id,address:home.name,name:namedResidents?names[townIndex*12+i]:`架空の住民 ${townIndex*12+i+1}`,s:states[i],location:unknown?null:[home.location[0]+(i%3-1)*.00015,home.location[1]+(i%2?1:-1)*.00012],acquiredAt:unknown?null:NOW-120000,bedroom:'1階・南側の部屋',memo:i===0?'出口がふさがれているため、救助を求めています。':'',impact:observations&&(i===0||i===8),enclosed:observations&&(i===0||i===1),history:[],priorSafety:undefined});
    }
  }
  if(allSafetyPatterns){
    const extraNames={east:['朝倉 梨花','岡田 修司','石井 千春'],central:['藤田 彩','清水 浩','橋本 直子'],west:['池田 真奈','阿部 隆','石川 美穂']};
    for(const regionId of ['east','central','west']){
      const members=people.filter(p=>p.regionId===regionId);
      for(const [i,state] of ['sos','safe','rescued'].entries()){
        const source=members.find(p=>p.s===state);
        const extra={...structuredClone(source),id:`pattern-${regionId}-${state}-unknown`,name:extraNames[regionId][i],location:null,acquiredAt:null};
        people.push(extra);members.push(extra);
      }
      for(const state of ['sos','unanswered','safe','rescued'])for(const known of [true,false]){
        const person=members.find(p=>p.s===state&&Boolean(p.location)===known);
        Object.assign(person,{impact:true,enclosed:true,impactAt:'14:32',enclosedAt:'14:33'});
        if(state==='safe')Object.assign(person,{answeredAt:'14:40',history:[{time:'14:40',author:'本人',action:'無事の回答',text:'衝撃・閉所の検知後、屋外へ移動して無事を回答。'}]});
        if(state==='rescued')Object.assign(person,{priorSafety:'sos',history:[{time:'14:46',author:'自分',action:'救助済み',text:'衝撃・閉所の検知後、本人を救助して屋外へ移動。'}]});
      }
    }
  }
  const point=(regionId,x,y)=>({regionId,location:[centers[regionId][0]+x,centers[regionId][1]+y]});
  const reports={'road-1':point('east',0,.00085),'building-1':point('central',.0009,0),'road-2':point('west',0,.00085),'other-1':point('west',-.0009,0)};
  return {centers,bounds,people,homes,reports};
}
