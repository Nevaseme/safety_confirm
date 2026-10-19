export class Router {
 constructor(token){this.token=token;this.phase='AB';this.clients=new Map();}
 send(socket,value){if((socket.bufferedAmount||0)>128000){socket.close(1008,'slow client');return;}socket.send(JSON.stringify(value));}
 linked(a,b){return a.group===b.group && [a.role,b.role].sort().join('')===this.phase;}
 message(socket,raw){
  try{
   if(Buffer.byteLength(raw)>32000)return socket.close(1009,'too large');const m=JSON.parse(raw);
   let client=this.clients.get(socket);
   if(!client){
    if(m?.kind!=='join'||m.token!==this.token||!['A','B','C'].includes(m.role)||!(/^[A-Za-z0-9_-]{1,40}$/).test(m.group)||[...this.clients.values()].some(c=>c.role===m.role))return socket.close(1008,'join rejected');
    client={role:m.role,group:m.group};this.clients.set(socket,client);
    this.send(socket,{kind:'joined',role:client.role,phase:this.phase});
    for(const [other,c]of this.clients)if(other!==socket&&this.linked(client,c)){this.send(other,{kind:'peer-up',peer:client.role});this.send(socket,{kind:'peer-up',peer:c.role});}return;
   }
   if(m.kind==='phase'&&client.role==='B'&&['AB','BC'].includes(m.phase)&&m.phase!==this.phase){
    if(m.phase==='BC'&&[...this.clients.values()].some(c=>c.role==='A'))return this.send(socket,{kind:'error',message:'先にAndroid Aの実験通信を停止してください'});
    for(const [s,c]of this.clients)for(const [t,d]of this.clients)if(s!==t&&this.linked(c,d))this.send(s,{kind:'peer-down',peer:d.role});
    this.phase=m.phase;
    for(const [s,c]of this.clients){this.send(s,{kind:'phase',phase:this.phase});for(const [t,d]of this.clients)if(s!==t&&this.linked(c,d))this.send(s,{kind:'peer-up',peer:d.role});}return;
   }
   if(m.kind==='data'&&m.body&&Buffer.byteLength(JSON.stringify(m.body))<=24000){for(const [to,d]of this.clients)if(d.role===m.to&&this.linked(client,d))this.send(to,{kind:'data',from:client.role,body:m.body});}
  }catch{socket.close(1008,'invalid message');}
 }
 leave(socket){const client=this.clients.get(socket);if(!client)return;this.clients.delete(socket);for(const [other,c]of this.clients)if(this.linked(client,c))this.send(other,{kind:'peer-down',peer:client.role});}
}
