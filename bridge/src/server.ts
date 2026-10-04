import http from 'node:http';
import {WeatherStore} from './weather.js';
import {defaultRegion} from './config.js';
import {DeskStateStore} from './desk-state.js';
import { CommandGate,commandSchema,syncSchema,PresetMessageSender,SourceRefresher,TextMessageSender,textCommandSchema,MAX_TEXT_LENGTH } from './browser/commands.js';
import { MediaStore } from './media.js';
import { createHash,randomUUID } from 'node:crypto';
import { WebSocket,WebSocketServer } from 'ws';
import { Event,Input,State,LIMITS,eventSchema,inputSchema,clientSchema,initialState,wireFrames,sourceSchema } from './protocol.js';
import { reduce } from './reducer.js';
import { ReplayBuffer } from './replay-buffer.js';
import { authorized } from './auth.js';
import { SourceAdapter } from './adapters/adapter.js';
export type NetworkAccess={serverId?:string,weatherRegion?:typeof defaultRegion,gateway?:{host:string,key:string}};
export class BamiBridge {
 readonly desk=new DeskStateStore();
 readonly weather:WeatherStore;
 readonly serverId:string;
 private commands=new CommandGate();
 readonly streamId=randomUUID();seq=0;state:State=initialState();readonly replay=new ReplayBuffer();readonly http=http.createServer((req,res)=>{
  if(!this.requestAllowed(req)){res.writeHead(401);res.end();return;}
  if(['/v1/weather','/v1/desk-state','/v1/capabilities','/v1/preset-messages','/v1/messages','/v1/sync'].includes(req.url??'')){void this.command(req,res);return;}
  if(req.method==='GET'&&req.url==='/healthz'){res.writeHead(200,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end('{"ok":true}');}else if(req.method==='GET'&&/^\/v1\/media\/[a-f0-9]{64}$/.test(req.url??'')){if(!this.requestAllowed(req)||!authorized(req.headers.authorization,this.token)){res.writeHead(401);res.end();return;}const b=this.media.get(req.url!.split('/').pop()!);if(!b){res.writeHead(404);res.end();return;}res.writeHead(200,{'Content-Type':'image/png','Content-Length':b.length,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(b);}else{res.writeHead(404);res.end();}
 });
 readonly wss=new WebSocketServer({noServer:true,maxPayload:LIMITS.frame,perMessageDeflate:false});private ready=new Set<WebSocket>();private heartbeat?:NodeJS.Timeout;private token:string;private attempts=new Map<string,{count:number,at:number}>();port=8788;
 constructor(readonly adapter:SourceAdapter,token:string,readonly media=new MediaStore(),readonly network:NetworkAccess={}){this.weather=new WeatherStore(undefined,undefined,network.weatherRegion??defaultRegion);this.serverId=network.serverId??randomUUID();this.token=token;this.http.on('upgrade',(req,socket,head)=>{
  const key=req.socket.remoteAddress??'',now=performance.now(),record=this.attempts.get(key);const attempt=!record||now-record.at>60000?{count:1,at:now}:{count:record.count+1,at:record.at};this.attempts.set(key,attempt);
  const code=attempt.count>60?429:(req.url!=='/v1/events'||!this.requestAllowed(req))?403:!authorized(req.headers.authorization,this.token)?401:0;
  if(code){socket.end(`HTTP/1.1 ${code} Rejected\r\nConnection: close\r\n\r\n`);return;}
  this.wss.handleUpgrade(req,socket,head,ws=>this.connect(ws));
 });}
 emit(input:Input,source=this.adapter.source){sourceSchema.parse(source);input=inputSchema.parse(input);if(input.type==='source.status'&&['live','manual','demo'].includes(input.payload.state)&&source.sourceMode!=='live')input={...input,payload:{...input.payload,state:source.sourceMode==='mock'?'demo':'manual'}};const e=this.make(input.type,input.payload,input.conversationId,input.taskId,source);const next=reduce(this.state,e);this.state=next;this.record(e);return e;}
 private requestAllowed(req:http.IncomingMessage){
  if(req.headers.origin!==undefined||!['127.0.0.1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress??''))return false;
  if(req.headers.host===`127.0.0.1:${this.port}`&&req.headers['x-bami-gateway']===undefined)return true;
  const gateway=this.network.gateway;return !!gateway&&req.headers.host===gateway.host&&authorized(typeof req.headers['x-bami-gateway']==='string'?req.headers['x-bami-gateway']:undefined,gateway.key);
 }
 private async command(req:http.IncomingMessage,res:http.ServerResponse){
  const reply=(code:number,value:unknown)=>{if(res.destroyed)return;res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(value));};
  if(!this.requestAllowed(req)||!authorized(req.headers.authorization,this.token)){reply(401,{code:'UNAUTHORIZED'});return;}
  if(req.url==='/v1/weather'){if(req.method!=='GET'){reply(405,{code:'METHOD_NOT_ALLOWED'});return;}reply(200,this.weather.snapshot());return;}
  if(req.url==='/v1/desk-state'){if(req.method!=='GET'){reply(405,{code:'METHOD_NOT_ALLOWED'});return;}reply(200,this.desk.snapshot());return;}
  const adapter=this.adapter as SourceAdapter & Partial<PresetMessageSender & SourceRefresher & TextMessageSender>;
  if(req.url==='/v1/capabilities'&&req.method==='GET'){reply(200,{presetMessages:!!adapter.sendPreset,textMessages:!!adapter.sendText,maxTextLength:MAX_TEXT_LENGTH,deskState:true,weather:true,sync:!!adapter.refresh,serverId:this.serverId});return;}
  if(req.method!=='POST'||req.url==='/v1/capabilities'){reply(405,{code:'METHOD_NOT_ALLOWED'});return;}
  if(req.headers['content-type']?.split(';')[0]?.trim()!=='application/json'){reply(415,{code:'JSON_REQUIRED'});return;}
  try {
   const raw=await new Promise<string>((resolve,reject)=>{let bytes=0;const chunks:Buffer[]=[];const timer=setTimeout(()=>{reject(Error('REQUEST_TIMEOUT'));req.destroy();},5000);req.on('data',(c:Buffer)=>{bytes+=c.length;if(bytes>32768){clearTimeout(timer);reject(Error('TOO_LARGE'));req.destroy();}else chunks.push(c);});req.on('end',()=>{clearTimeout(timer);resolve(Buffer.concat(chunks).toString('utf8'));});req.on('error',()=>{clearTimeout(timer);reject(Error('REQUEST_ERROR'));});});
   const parsed=JSON.parse(raw);
   if(req.url==='/v1/preset-messages'){
    const c=commandSchema.parse(parsed);if(!adapter.sendPreset){reply(501,{code:'NOT_SUPPORTED'});return;}
    const result=await this.commands.run(c.commandId,'send:'+c.presetId,()=>adapter.sendPreset!(c.presetId));reply(200,result);
   }else if(req.url==='/v1/messages'){
    const c=textCommandSchema.parse(parsed);if(!adapter.sendText){reply(501,{code:'NOT_SUPPORTED'});return;}
    const key='text:'+createHash('sha256').update(c.text).digest('hex');
    const result=await this.commands.run(c.commandId,key,()=>adapter.sendText!(c.text));reply(200,result);
   }else{
    const c=syncSchema.parse(parsed);if(!adapter.refresh){reply(501,{code:'NOT_SUPPORTED'});return;}
    const result=await this.commands.run(c.commandId,'sync',async()=>{const source=await adapter.refresh!();const snapshot=this.snapshot();return {status:'ready',observedAt:source.observedAt,streamId:this.streamId,snapshotId:snapshot.payload.snapshotId,asOfSeq:snapshot.seq};});reply(200,result);
   }
  }catch(error){const code=error instanceof Error?error.message:'';const safe=['BUSY','COMMAND_ID_CONFLICT','COMMAND_CACHE_FULL','USER_CONTROL','SPACE_UNAVAILABLE','ADDRESS_CHANGED','UNSUPPORTED_OR_LOGIN','STRUCTURE_CHANGED','SOURCE_UNAVAILABLE','OPERATION_TIMEOUT'];reply(safe.includes(code)?409:400,{code:safe.includes(code)?code:'INVALID_REQUEST'});}
 }
 private make(type:Event['type'],payload:any,conversationId?:string,taskId?:string,source=this.adapter.source):Event{const e:Event={schemaVersion:2,streamId:this.streamId,eventId:randomUUID(),seq:this.seq+1,timestamp:new Date().toISOString(),source,type,payload,...(conversationId?{conversationId}:{}),...(taskId?{taskId}:{})};eventSchema.parse(e);wireFrames(e);return e;}
 private record(e:Event){this.seq=e.seq;this.replay.push(e);for(const ws of this.ready)this.send(ws,e);}
 private send(ws:WebSocket,e:Event){if(ws.readyState!==WebSocket.OPEN)return;for(const frame of wireFrames(e)){if(ws.bufferedAmount+Buffer.byteLength(frame)>LIMITS.queue){this.ready.delete(ws);ws.close(1013,'RESYNC_REQUIRED');return;}ws.send(frame);}}
 snapshot(){const seq=this.seq+1;const e=this.make('state.snapshot',{...structuredClone(this.state),snapshotId:randomUUID(),asOfSeq:seq});this.record(e);return e;}
 private connect(ws:WebSocket){let hello=false,lastAck=0;const deadline=setTimeout(()=>ws.close(1008,'HELLO_TIMEOUT'),5000);ws.on('close',()=>{clearTimeout(deadline);this.ready.delete(ws);});ws.on('error',()=>this.ready.delete(ws));
 ws.on('message',raw=>{try{const rawMessage=JSON.parse(raw.toString());if(rawMessage.schemaVersion!==2){ws.close(1008,'UPDATE_REQUIRED');return;}const m=clientSchema.parse(rawMessage);if(!hello){if(m.type!=='client.hello'||!m.capabilities.includes('snapshot-v2')||!m.capabilities.includes('message-revisions-v1'))throw Error('HANDSHAKE');if(this.adapter.source.adapterId==='browser-dots-v2'&&(!m.capabilities.includes('message-observations-v2')||!m.capabilities.includes('media-v1'))){ws.close(1008,'UPDATE_REQUIRED');return;}hello=true;clearTimeout(deadline);
 const h=this.make('server.hello',{heartbeatMs:5000,staleMs:15000,capabilities:['snapshot-v2','message-revisions-v1','chunk-v1','message-observations-v2','media-v1']});this.record(h);this.send(ws,h);
 const replay=m.resume&&m.resume.streamId===this.streamId?this.replay.after(this.streamId,m.resume.lastAppliedSeq,this.seq):null;
 if(replay){for(const e of replay)this.send(ws,e);this.ready.add(ws);}else{this.ready.add(ws);this.snapshot();}return;}
 if(m.type==='client.hello')throw Error('DUPLICATE_HELLO');if(m.type==='client.resync')this.snapshot();else{if(m.streamId!==this.streamId||m.lastAppliedSeq>this.seq||m.lastAppliedSeq<lastAck)throw Error('INVALID_ACK');lastAck=m.lastAppliedSeq;}
 }catch{ws.close(1008,'PROTOCOL_ERROR');}});
 }
 async start(port=8788){this.port=port;await new Promise<void>((resolve,reject)=>{this.http.once('error',reject);this.http.listen(port,'127.0.0.1',()=>{this.http.off('error',reject);this.port=(this.http.address() as any).port;resolve();});});await this.adapter.start(e=>{try{this.emit(e);}catch{this.emit({type:'source.status',payload:{state:'stale',lastObservedAt:new Date().toISOString(),detail:'원천 이벤트가 누락되거나 계약을 위반했어요'}});}});this.heartbeat=setInterval(()=>this.record(this.make('heartbeat',{})),5000);return this.port;}
 rotateToken(token:string){this.token=token;for(const ws of this.wss.clients)ws.close(1008,'TOKEN_ROTATED');}
 async stop(){this.weather.stop();await this.adapter.stop();if(this.heartbeat)clearInterval(this.heartbeat);for(const ws of this.wss.clients)ws.terminate();await new Promise<void>(r=>this.wss.close(()=>r()));await new Promise<void>(r=>this.http.close(()=>r()));}
}
