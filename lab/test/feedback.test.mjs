import test from 'node:test';
import assert from 'node:assert/strict';
import {SaveFeedback,ConnectionFeedback} from '../web/feedback.mjs';
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};};
test('successful group change removes prior safety and care feedback but retains current profile result',async()=>{
 const f=new SaveFeedback();await f.run('safety',()=>{},()=>{},'SOS');await f.run('care:resident',()=>{},()=>{},'記録');
 await f.run('profile',()=>{},()=>f.clearOtherResults(),'登録情報');
 assert.equal(f.get('safety').status,'idle');assert.equal(f.get('care:resident').status,'idle');assert.equal(f.get('profile').status,'success');
});
function clock(){let next=0;const jobs=new Map();return {jobs,schedule:(fn,ms)=>{jobs.set(++next,{fn,ms});return next;},cancel:id=>jobs.delete(id),fire:()=>{const job=[...jobs.values()][0];jobs.clear();job.fn();}};}
test('save immediately locks conflicting actions; durable completion survives publish failure',async()=>{
 const f=new SaveFeedback(), d=deferred();let writes=0;
 const first=f.run('care',()=>{writes++;return d.promise;},()=>{throw Error('切断');},'対応記録');
 assert.equal(f.busy,true);assert.equal(f.get('care').status,'pending');
 await f.run('safety',()=>{writes++;},()=>{},'SOS');assert.equal(writes,1);
 d.resolve();await first;assert.equal(f.busy,false);assert.equal(f.get('care').status,'success');
 assert.match(f.get('care').text,/端末に保存/);assert.match(f.get('care').text,/再接続/);
});
test('failed persistence never publishes; retry clears only this operation error',async()=>{
 const f=new SaveFeedback();let sent=0;
 await f.run('profile',()=>{throw Error('容量不足');},()=>{sent++;},'登録情報');
 assert.equal(f.get('profile').status,'error');assert.equal(sent,0);
 await f.run('care',()=>{throw Error('別のエラー');},()=>{},'記録');
 await f.run('profile',()=>{},()=>{},'登録情報');
 assert.equal(f.get('profile').status,'success');assert.equal(f.get('care').status,'error');
});
test('connection only completes after joined; duplicate start and stale callbacks ignored',()=>{
 const c=clock(),f=new ConnectionFeedback(()=>{},()=>{},c),e=f.start();
 assert.equal(f.state.status,'connecting');assert.equal(f.start(),null);
 assert.equal([...c.jobs.values()][0].ms,15000);
 f.joined(e,'AB');assert.equal(f.state.status,'waiting');assert.equal(c.jobs.size,0);
 f.peers(e,1);assert.equal(f.state.status,'connected');f.stop('画面を閉じたため停止');
 f.joined(e,'BC');assert.equal(f.state.status,'stopped');
 const next=f.start();f.fail(e,'old error');assert.equal(f.state.status,'connecting');f.joined(next,'BC');
 assert.equal(f.state.phase,'BC');assert.equal(f.state.error,null);
});
test('route awaits server confirmation, rejects duplicate/current/C requests and retains confirmed route on rejection',()=>{
 const c=clock(),f=new ConnectionFeedback(()=>{},()=>{},c),e=f.start();f.joined(e,'AB');
 assert.equal(f.requestPhase('C','BC'),false);assert.equal(f.requestPhase('B','AB'),false);
 assert.equal(f.requestPhase('B','BC'),true);assert.equal(f.state.phase,'AB');
 assert.equal([...c.jobs.values()][0].ms,5000);assert.equal(f.requestPhase('B','BC'),false);
 f.rejectPhase(e,'先にAndroidを停止');assert.equal(f.state.phase,'AB');assert.equal(f.state.pendingPhase,null);
 f.requestPhase('B','BC');f.confirmPhase(e,'BC');assert.equal(f.state.phase,'BC');assert.equal(f.state.error,null);
});
test('timeouts close transport; route timeout is unknown, not a false rejection; late response cannot revive',()=>{
 const c=clock();let closed=0;const f=new ConnectionFeedback(()=>{},()=>closed++,c),e=f.start();
 c.fire();assert.equal(closed,1);assert.equal(f.state.status,'stopped');assert.match(f.state.error,/接続/);
 const e2=f.start();f.joined(e2,'AB');f.requestPhase('B','BC');c.fire();
 assert.equal(closed,2);assert.match(f.state.error,/切替結果を確認できません/);
 f.confirmPhase(e2,'BC');assert.equal(f.state.phase,'AB');assert.equal(f.state.status,'stopped');
 f.joined(e,'AB');assert.equal(f.state.status,'stopped');
});
