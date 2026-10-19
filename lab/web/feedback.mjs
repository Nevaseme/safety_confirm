// UI state only. A successful durable write is never reclassified as a send failure.
export class SaveFeedback {
 constructor(changed=()=>{}){this.changed=changed;this.busy=false;this.active=null;this.states=new Map();}
 clearOtherResults(){for(const key of this.states.keys())if(key!==this.active)this.states.delete(key);this.changed();}
 get(key){return this.states.get(key)||{status:'idle',text:''};}
 async run(key,persist,publish,label){
  if(this.busy)return false;
  this.busy=true;this.active=key;this.states.set(key,{status:'pending',text:'保存しています…'});this.changed();
  let saved=false;
  try{
   await persist();saved=true;
   this.states.set(key,{status:'success',text:`${label}をこの端末に保存しました（${new Date().toLocaleTimeString('ja-JP')}）`});
   try{await publish();}catch{this.states.set(key,{status:'success',text:`${label}はこの端末に保存済みです。表示を開き直し、通信は再接続してください。`});}
  }catch(e){this.states.set(key,{status:'error',text:`保存できませんでした：${e.message||'保存先を確認してください'}。入力を確認して再試行してください。`});}
  finally{this.busy=false;this.active=null;this.changed();}
  return saved;
 }
}
export class ConnectionFeedback {
 constructor(changed=()=>{},expired=()=>{},clock={schedule:(fn,ms)=>setTimeout(fn,ms),cancel:id=>clearTimeout(id)}){
  this.changed=changed;this.expired=expired;this.clock=clock;this.epoch=0;this.timer=null;
  this.state={status:'stopped',phase:null,pendingPhase:null,error:null,text:'共有停止中'};
 }
 get active(){return this.state.status!=='stopped';}
 valid(epoch){return epoch===this.epoch&&this.active;}
 cancel(){if(this.timer!==null)this.clock.cancel(this.timer);this.timer=null;}
 deadline(ms,text){this.cancel();const epoch=this.epoch;this.timer=this.clock.schedule(()=>{if(this.valid(epoch)){this.stop(text,true);this.expired();}},ms);}
 start(){
  if(this.active)return null;
  const epoch=++this.epoch;this.state={status:'connecting',phase:null,pendingPhase:null,error:null,text:'接続しています…'};
  this.deadline(15000,'接続を確認できませんでした。サーバー・接続コードを確認して再試行してください。');this.changed();return epoch;
 }
 joined(epoch,phase){if(!this.valid(epoch)||this.state.status!=='connecting')return;this.cancel();this.state={status:'waiting',phase,pendingPhase:null,error:null,text:'サーバー接続済み・相手待ち'};this.changed();}
 peers(epoch,count){if(!this.valid(epoch)||this.state.status==='connecting')return;this.state={...this.state,status:count?'connected':'waiting',text:count?`相手 ${count} 台と接続中`:'サーバー接続済み・相手待ち'};this.changed();}
 requestPhase(role,phase){
  if(role!=='B'||!['waiting','connected'].includes(this.state.status)||this.state.pendingPhase||this.state.phase===phase)return false;
  this.state={...this.state,pendingPhase:phase,error:null};this.deadline(5000,'切替結果を確認できません。再接続して現在の経路を確認してください。');this.changed();return true;
 }
 confirmPhase(epoch,phase){if(!this.valid(epoch)||this.state.status==='connecting')return;this.cancel();this.state={...this.state,phase,pendingPhase:null,error:null};this.changed();}
 rejectPhase(epoch,error){if(!this.valid(epoch))return;if(this.state.pendingPhase)this.cancel();this.state={...this.state,pendingPhase:null,error};this.changed();}
 fail(epoch,error){if(this.valid(epoch))this.stop(error,true);}
 stop(text='共有停止中',error=false){this.cancel();++this.epoch;this.state={...this.state,status:'stopped',pendingPhase:null,error:error?text:null,text:error?'共有停止中':text};this.changed();}
}
