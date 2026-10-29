import {englishUI} from './english-ui.mjs?v=1';

// Translate presentation only. Model values, form values, IDs and free text stay intact.
const extra={
  '言語':'Language','言語 / Language':'Language','メイン':'Main navigation','詳細':'Details',
  '一覧':'Resident list','メンバー一覧':'Resident list','住民一覧':'Resident list',
  'SOS':'SOS','無事':'Safe','対応中':'In progress','未設定':'Not set',
  '拡大':'Zoom in','縮小':'Zoom out','衝撃あり':'Impact detected','閉所あり':'Enclosed space detected','閉所を検知':'Enclosed space detected',
  '担当':'Assigned to','担当：':'Assigned to:','通報者：':'Reported by:',
  '道路':'Blocked','閉塞':'road','建物':'Building','倒壊':'collapse',
  '人':'','件':'','必要':'Needed','不足':'Still needed','資材：':'Supplies: ',
  '寝室：':'Sleeping area: ','普段の住居：':'Home: ','緊急度の理由：':'Reason for urgency: ',
  '個別に表示できる人はいません。':'No individual details are available.',
  '個別に表示できる通報はありません。':'No individual reports are available.',
  '安否統計':'Safety statistics','安否の統計':'Safety statistics',
  '個別に確認できる登録住民':'Residents you can view',
  '対応・通報':'Response and reports','表示する立場・町内会・時間':'Role, neighborhood and time',
  'このプレビューでは地図と詳細を確認できます':'This preview supports the map and details.',
  '設定から日本語・英語を切り替え':'Switch between Japanese and English in Settings',
  '未設定（判定前）':'Not assessed','緊急度 高':'High urgency','緊急度 中':'Medium urgency','緊急度 低':'Low urgency','緊急度 未設定':'Urgency not assessed',
  '「対応予定」は引き受け済み・未着手です。':'Acknowledged means someone has agreed to respond but has not started the response work yet.',
  'このデモで自分の安否を記録する利用者は、架空の東町内会に所属しています。':'In this demo, your safety status belongs to the fictional East neighborhood.',
  '一般住民':'Resident','エリア管理者':'Area administrator','自治体職員':'Municipal staff',
  '東町内会':'East neighborhood','中央町内会':'Central neighborhood','西町内会':'West neighborhood','架空市':'Example City','架空市全域':'Entire Example City','東区':'East district','西区':'West district','木山地区':'Kiyama area',
  '北班':'North team','連絡班':'Communications team',
  '安否を記録':'Save my status','SOSメモを更新':'Update help details',
  '安否で絞り込み':'Filter by safety status','の地震':' earthquake','の訓練':' drill',
  '状態を更新':'Status updated','状況を更新':'Status updated','自分が対応を開始':'You started responding','自分の担当を解除':'You removed yourself from the assignment',
  '地域別の通報総数と種類別件数':'Report totals and types by area',
  '件数から、閲覧できる通報の詳細を開きます。安否の非公開対象は統計だけを表示します。':'Select a count to view the reports you can access. Restricted safety information is shown only as statistics.',
  '通報を取り消し':'Report withdrawn','通報の取り消しを戻す':'Report restored',
  '表示を切り替える':'Apply settings',
  '不足あり':'Supplies needed','不足の記録なし':'No shortages recorded',
};
const dictionary={...englishUI,...extra};
const patternEntries=Object.entries(dictionary).filter(([key])=>key.includes('{')).map(([key,value])=>{
  const names=[];const source=key.split(/(\{[^}]+\})/).map(part=>{
    if(part.startsWith('{')){names.push(part.slice(1,-1));return names.at(-1)==='time'?'([0-9]{1,2}:[0-9]{2})':['count','minutes','meters','years','year','hours','number'].includes(names.at(-1))?'([0-9.]+)':'(.+?)';}
    return part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
  }).join('');return {regex:new RegExp('^'+source+'$'),names,value};
});
const months=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
export function translateUI(input,locale='en',context='',depth=0){
  if(locale!=='en'||input==null)return input;
  const source=String(input),s=source.trim();if(!s)return source;
  const wrap=value=>source.slice(0,source.indexOf(s))+value+source.slice(source.indexOf(s)+s.length);
  if(context==='urgency'&&s==='未設定')return wrap('Not assessed');
  if(context==='activity'&&s==='対応中')return wrap('Busy');
  if(context==='self'&&s==='無事')return wrap('I’m safe');
  if(context==='self'&&s==='SOS')return wrap('I need help');
  if(Object.hasOwn(dictionary,s))return wrap(dictionary[s]);
  if(depth>5)return source;
  const t=v=>translateUI(v,locale,'',depth+1);
  const location=s.match(/^(最後のスマホ位置)\s+(.+)$/);
  if(location)return wrap(`${t(location[1])}: ${t(location[2])}`);
  for(const {regex,names,value} of patternEntries){const match=s.match(regex);if(match){let result=value.replace(/\{([^}]+)\}/g,(_,name)=>t(match[names.indexOf(name)+1]??''));if(match[1]==='1')result=result.replace('1 years old','1 year old').replace('1 floors','1 floor').replace('1 more people have','1 more person has');return wrap(result);}}
  let m;
  if((m=s.match(/^(\d{1,2})月(\d{1,2})日の(地震|訓練)$/)))return wrap(`${m[3]==='地震'?'Earthquake':'Drill'} on ${months[+m[1]-1]} ${m[2]}`);
  if((m=s.match(/^(\d{1,2})月(\d{1,2})日(.*)$/)))return wrap(`${months[+m[1]-1]} ${m[2]}${t(m[3])}`);
  if((m=s.match(/^経過 (\d+:\d+)$/)))return wrap(`Elapsed ${m[1]}`);
  if((m=s.match(/^(.+?) (合計|SOS|道路閉塞|建物倒壊|その他) (\d+)件$/)))return wrap(`${t(m[1])}, ${t(m[2])}: ${m[3]}`);
  if((m=s.match(/^(.+?) ([0-9]+)件$/)))return wrap(`${t(m[1])} (${m[2]})`);
  if((m=s.match(/^(SOS|無事|救助済み|応答なし|反応なし)\s*(\d+)人$/)))return wrap(`${t(m[1])}: ${m[2]}`);
  if((m=s.match(/^(\d+)(人|件)$/)))return wrap(m[1]);
  if((m=s.match(/^(\d+)分前$/)))return wrap(`${m[1]} min ago`);
  if((m=s.match(/^(.+)の通報$/)))return wrap(`${t(m[1])} report`);
  if((m=s.match(/^担当：(.+)$/)))return wrap(`Assigned to: ${t(m[1])}`);
  if((m=s.match(/^通報者：(.+)$/)))return wrap(`Reported by: ${m[1]}`);
  if((m=s.match(/^(.+)：$/)))return wrap(`${t(m[1])}: `);
  if((m=s.match(/^(必要|手配済み|不足)\s*([\d.]+)$/)))return wrap(`${t(m[1])} ${m[2]}`);
  if((m=s.match(/^(\d+:\d+)取得\s*・\s*精度 約([\d.]+)m$/)))return wrap(`Recorded at ${m[1]} · Accuracy: about ${m[2]} m`);
  if((m=s.match(/^緯度 ([\d.-]+)\s*・\s*経度 ([\d.-]+)$/)))return wrap(`Latitude ${m[1]} · Longitude ${m[2]}`);
  if((m=s.match(/^選択した地点：緯度 ([\d.-]+)・経度 ([\d.-]+)$/)))return wrap(`Selected location: ${m[1]}, ${m[2]}`);
  if((m=s.match(/^(\d+) \/ 500文字$/)))return wrap(`${m[1]} / 500 characters`);
  // Composition separators are emitted by templates; free text is marked translate="no".
  if(/[・、　]/.test(s))return wrap(s.split(/([・、　]+)/).map(part=>/^[・、　]+$/.test(part)?' · ':t(part)).join(''));
  if((m=s.match(/^(最後のスマホ位置|位置|最後の位置)\s+(.+)$/)))return wrap(`${t(m[1])}: ${t(m[2])}`);
  return source;
}

export function installLanguageSettings(frame){
  const root=frame.phone,cache=new WeakMap();let locale='ja';
  try{locale=localStorage.getItem('pland-demo-language')==='en'?'en':'ja';}catch{}
  const context=element=>element.closest('[name=urgency],[data-ui-context=urgency]')?'urgency':element.closest('[name=activity],.ops-availability')?'activity':element.closest('.choice-buttons button,.safety-choices button')&&!element.closest('.symbol')?'self':'';
  const syncValue=(node,key,current,write,ctx='')=>{
    const saved=cache.get(node)||{};let record=saved[key];
    if(!record||current!==record.rendered)record={source:current};
    const next=translateUI(record.source,locale,ctx);record.rendered=next;saved[key]=record;cache.set(node,saved);if(current!==next)write(next);
  };
  const scan=node=>{
    if(node.nodeType===Node.TEXT_NODE){const parent=node.parentElement;if(!parent||parent.closest('[translate=no],textarea,script,style'))return;syncValue(node,'text',node.nodeValue,v=>node.nodeValue=v,context(parent));return;}
    if(node.nodeType!==Node.ELEMENT_NODE||node.matches('[translate=no],script,style'))return;
    // A custom incident type is user text, including when it happens to equal a UI label.
    const report=node.matches('.ops-map-report')&&frame.visibility.ops.find(node.dataset.caseId);
    if(report){const category=['SOS','道路閉塞','建物倒壊','その他'].includes(report.category)?translateUI(report.category,locale):report.category;node.setAttribute('aria-label',locale==='en'?`${category} · ${translateUI('緊急度 '+report.urgency,locale)} report`:`${category}・緊急度 ${report.urgency}の通報`);}
    for(const name of ['aria-label','title','placeholder','alt'])if(node.hasAttribute(name)&&!(report&&name==='aria-label'))syncValue(node,name,node.getAttribute(name),v=>node.setAttribute(name,v),context(node));
    if(node.tagName!=='TEXTAREA')for(const child of node.childNodes)scan(child);
  };
  const observer=new MutationObserver(records=>{
    observer.disconnect();
    for(const record of records){if(record.type==='childList')record.addedNodes.forEach(scan);else if(record.type==='attributes')scan(record.target);else scan(record.target);}
    labelSheet();observe();
  });
  const labelSheet=()=>{const heading=frame.head.querySelector('h2');if(heading&&!frame.sheet.hidden)frame.sheet.setAttribute('aria-label',heading.textContent.trim());};
  const observe=()=>observer.observe(root,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['aria-label','title','placeholder','alt']});
  const apply=()=>{observer.disconnect();root.lang=locale;scan(root);labelSheet();observe();};
  const categories=new Map([['Blocked road','道路閉塞'],['Building collapse','建物倒壊'],['Other','その他']]);
  frame.language={get locale(){return locale;},t:(text,ctx)=>translateUI(text,locale,ctx),categoryKey:text=>categories.get(text)||text};
  const menu=frame.showMenu.bind(frame);
  frame.showMenu=()=>{menu();const button=frame.body.querySelector('[data-menu=settings]');button.onclick=()=>{
    frame.openSheet('<h2>設定</h2>','<fieldset class="language-setting"><legend>言語 / Language</legend><label lang="ja" translate="no"><span>日本語</span><input type="radio" name="ui-language" value="ja"></label><label lang="en" translate="no"><span>English</span><input type="radio" name="ui-language" value="en"></label></fieldset>');
    frame.body.querySelectorAll('[name=ui-language]').forEach(input=>{input.checked=input.value===locale;input.onchange=()=>{locale=input.value;try{localStorage.setItem('pland-demo-language',locale);}catch{}apply();};});
  };};
  apply();
}
