import {CENTER,NOW} from './clustering.mjs?v=6';
const central=[
  [-.00073,.00058,'sos','山田 太郎',true,true,'2階の寝室にいます。ドアが開かず、外へ出られません。'],
  [.00045,.00078,'sos','佐藤 美咲',false,true,'棚が倒れて通路をふさいでいます。声は出せます。'],
  [.00085,-.00032,'sos','田中 健一',true,false,'玄関の近くにいます。足を痛めて動けません。'],
  [-.00045,-.00070,'unanswered','山田 花子',true,false,''],
  [.0001,-.00135,'unanswered','鈴木 和子',false,false,''],
  [.0010,.0014,'safe','',false,false,''],
  [-.0009,-.00175,'safe','',false,false,''],
  [-.0008,.00200,'safe','',false,false,''],
  [.00108,-.0017,'rescued','',false,false,'']
];
export const homes=[
  {id:'yamada',name:'木山123-1',location:[CENTER[0]-.00063,CENTER[1]+.00041],built:1986,material:'木造',floors:2,rating:2},
  {id:'sato',name:'木山123-2',location:[CENTER[0]+.00041,CENTER[1]+.00057],built:2004,material:'木造',floors:2,rating:1},
  {id:'tanaka',name:'木山124-1',location:[CENTER[0]+.00070,CENTER[1]-.00028],built:1995,material:'木造',floors:2,rating:1}
];
function person(id,name,s,location,i,extra={}){
  return {id,name,s,location,acquiredAt:NOW-(i===3?18:2+i%8)*60000,accuracy:8+i%7,answeredAt:'14:34',impact:false,enclosed:false,impactAt:'14:32',enclosedAt:'14:33',homeId:i===0||i===3?'yamada':i===1||i===5?'sato':'tanaka',bedroom:i===0?'2階・北側の寝室。窓側のベッド。':i===3?'1階・南側の和室。奥の布団。':'2階・東側の部屋。窓の横のベッド。',memo:'',history:s==='rescued'?[{time:'14:46',author:'自分',text:'玄関前で本人を確認。屋外へ移動済み。',action:'救助済み'}]:[],...extra};
}
export const people=central.map((a,i)=>person('p'+i,a[3],a[2],[CENTER[0]+a[0]*.84,CENTER[1]+a[1]*.8],i,{impact:a[4],enclosed:a[5],memo:a[6]}));
const groups=[{x:.0041,y:.0052,counts:[1,1,6,0]},{x:-.0041,y:.0053,counts:[0,1,0,0]},{x:.0041,y:-.0053,counts:[1,0,4,0]},{x:-.004,y:-.0055,counts:[0,0,1,0]}];
const otherNames=['加藤 恵子','渡辺 明','','','','','','','林 正夫','斎藤 洋子','','','','',''];
groups.forEach(g=>g.counts.forEach((n,state)=>{for(let j=0;j<n;j++){let i=people.length;people.push(person('p'+i,otherNames[i-9],['sos','unanswered','safe','rescued'][state],[CENTER[0]+g.x+(j%3-1)*.00045,CENTER[1]+g.y+(Math.floor(j/3)+state-1)*.0004],i));}}));
people.push(person('unknown1','森 久美子','unanswered',null,24,{acquiredAt:null,bedroom:'1階・南側の寝室。奥の布団。'}));
people.push(person('unknown2','石井 達也','unanswered',[CENTER[0]-.01,CENTER[1]+.01],25,{acquiredAt:NOW-4*86400000}));
export function personLabel(p){return p.s==='safe'?'無事の人':p.s==='rescued'?'救助済みの人':p.name||(p.id==='p5'?'自分':'登録住民');}
