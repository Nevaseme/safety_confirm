import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {WebSocketServer} from 'ws';
import {Router} from './router.mjs';
const root=path.dirname(fileURLToPath(import.meta.url));
const config=JSON.parse(fs.readFileSync(path.join(root,'.local/config.json'),'utf8'));
const router=new Router(config.token);
const allowed=new Map(['index.html','app.mjs','sync.mjs','local-situation.mjs','managed-records.mjs','managed-store.mjs','feedback.mjs','incidents.mjs','places.mjs','place-ui.mjs','style.css','manifest.webmanifest','sw.js','icon.svg'].map(f=>['/'+f,f]));
const mime={'.html':'text/html; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.webmanifest':'application/manifest+json','.svg':'image/svg+xml'};
const server=https.createServer({key:fs.readFileSync(path.join(root,'.local/server.key')),cert:fs.readFileSync(path.join(root,'.local/server.pem'))},(req,res)=>{
 const url=new URL(req.url,'https://localhost');const file=allowed.get(url.pathname==='/'?'/index.html':url.pathname);
 if(req.method!=='GET'||!file){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'Content-Type':mime[path.extname(file)],'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; connect-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"});
 fs.createReadStream(path.join(root,'web',file)).pipe(res);
});
const wss=new WebSocketServer({server,path:'/socket',maxPayload:32000});
wss.on('connection',(ws,req)=>{
 const origin=req.headers.origin;
 // Native Android has no Origin; web clients must have the same origin as this server.
 if(origin&&origin!==`https://${req.headers.host}`){ws.close(1008,'origin rejected');return;}
 const timer=setTimeout(()=>{if(!router.clients.has(ws))ws.close(1008,'join timeout');},5000);
 ws.isAlive=true;ws.on('pong',()=>{ws.isAlive=true;});
 ws.on('message',(data,binary)=>{if(binary)ws.close(1003,'text only');else router.message(ws,data.toString());});
 ws.on('close',()=>{clearTimeout(timer);router.leave(ws);});ws.on('error',()=>{});
});
const heartbeat=setInterval(()=>{for(const ws of wss.clients){if(!ws.isAlive){ws.terminate();continue;}ws.isAlive=false;ws.ping();}},15000);
server.on('close',()=>clearInterval(heartbeat));
server.listen(config.port,'0.0.0.0',()=>console.log(`PlanD lab HTTPS :${config.port}; no record storage; phase AB. See .local/connection.txt for device setup.`));
