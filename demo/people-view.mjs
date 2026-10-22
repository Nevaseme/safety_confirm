import {hasPosition,NOW} from './clustering.mjs';

// The fixture's first earthquake report is at 14:32, before its 15:00 observation time.
export const FIRST_REPORT_AT=Date.parse('2026-10-21T14:32:00+09:00');
export function elapsedLabel(now=NOW,start=FIRST_REPORT_AT){
  const minutes=Math.max(0,Math.floor((now-start)/60000));
  if(minutes<60)return `${minutes}分前`;
  const hours=Math.floor(minutes/60);
  return `${hours}時間${minutes%60?`${minutes%60}分`:''}前`;
}
export function scopedPeople(people,ids=null){
  if(ids===null)return people;
  const selected=new Set(ids);
  return people.filter(p=>selected.has(p.id));
}
export function filterPeople(people,filter='all'){
  if(filter==='all')return people;
  if(filter==='unknown')return people.filter(p=>!hasPosition(p));
  // Old/unreceived locations have their own view, without stale safety assertions.
  return people.filter(p=>hasPosition(p)&&(filter==='safe'?['safe','rescued'].includes(p.s):p.s===filter));
}
export function listCounts(people){
  return Object.fromEntries(['all','sos','unanswered','safe','unknown'].map(key=>[key,filterPeople(people,key).length]));
}
