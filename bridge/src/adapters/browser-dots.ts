import {validateDotsUrl} from '../config.js';
import { spawn,ChildProcessWithoutNullStreams } from 'node:child_process';
import { createReadStream,ReadStream } from 'node:fs';
import { mkdtemp,rm,open,writeFile } from 'node:fs/promises';
import { tmpdir,homedir } from 'node:os';
import { promisify } from 'node:util';
import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { PresetId,SendResult } from '../browser/commands.js';
import { SourceAdapter } from './adapter.js';
import { Input } from '../protocol.js';
import { ObservationTracker } from '../browser/tracker.js';
import { BrowserRecovery } from '../browser/recovery.js';
import { MediaStore } from '../media.js';
export class BrowserDotsAdapter implements SourceAdapter {
 readonly source={adapterId:'browser-dots-v2',sourceMode:'live' as const,sourceSessionId:null};diagnostic={ready:false,finished:false,spaceId:null as number|null,exitCode:null as number|null,stderr:false};private recovery=new BrowserRecovery();private retryTimer?:NodeJS.Timeout;private child?:ChildProcessWithoutNullStreams;private timer?:NodeJS.Timeout;private closed=false;private stopping=false;private dataStream?:ReadStream;private dataHandle?:import('node:fs/promises').FileHandle;private pipeDir?:string;private control?:import('node:fs/promises').FileHandle;
 private workerId?:string;private launch?:()=>void;private pending=new Map<string,{resolve:(r:any)=>void,reject:(e:Error)=>void,timer:NodeJS.Timeout}>();
 constructor(readonly media:MediaStore,readonly executable=process.env.BAMI_EGO_BROWSER??'ego-browser',readonly target=validateDotsUrl(process.env.DOTS_ROOM_URL??'')){
 validateDotsUrl(target);
 const id=process.env.BAMI_BROWSER_SPACE_ID;if(id!==undefined){if(!/^[1-9][0-9]*$/.test(id)||!Number.isSafeInteger(Number(id)))throw Error('INVALID_BROWSER_SPACE');this.recovery.spaceId=Number(id);}
 }
 async start(emit:(e:Input)=>void){const tracker=new ObservationTracker();let last=Date.now(),buffer='',invalid=false;const status=(state:string,detail:string)=>emit({type:'source.status',payload:{state,lastObservedAt:new Date(last).toISOString(),detail,taskEvents:'unsupported'}});status('unavailable','브라우저 표시 대화를 연결하고 있어요');
 this.pipeDir=await mkdtemp(tmpdir()+'/bami-browser-');const pipe=this.pipeDir+'/stop.pipe';await promisify(execFile)('/usr/bin/mkfifo',['-m','600',pipe]);this.control=await open(pipe,'r+');const dataPipe=this.pipeDir+'/data.pipe';await promisify(execFile)('/usr/bin/mkfifo',['-m','600',dataPipe]);this.dataHandle=await open(dataPipe,'r+');this.dataStream=createReadStream(dataPipe);this.dataStream.setEncoding('utf8');const launch=()=>{
 this.closed=false;this.diagnostic.ready=false;this.diagnostic.exitCode=null;buffer='';last=Date.now();
 const code=`const {run}=await import(${JSON.stringify(new URL('../browser/reader-worker.mjs',import.meta.url).href)});await run(taskSpace,${JSON.stringify(pipe)},${JSON.stringify(dataPipe)},${JSON.stringify(this.recovery.spaceId)},${JSON.stringify(this.target)});`;
 const child=this.child=spawn(this.executable,['nodejs','-e',code],{stdio:'pipe',env:{...process.env}});child.stdin.end();child.stderr.on('data',()=>{this.diagnostic.stderr=true;});
 child.stdout.on('data',chunk=>{if(!this.diagnostic.ready){const value=chunk.toString();const known=['EACCES','ENOENT','ERR','TypeError','SyntaxError','ReferenceError'];const code=known.find(k=>value.includes(k))??'CLI_OUTPUT';status('unavailable','수집 실행 오류: '+code);}});
 const retry=()=>{
  if(this.stopping||invalid)return;
  const delay=this.recovery.delay();
  if(delay===null){status('unavailable','브라우저 수집 중지: '+(this.recovery.reason??'RETRY_LIMIT')+' · 주소/로그인/제어 상태를 확인해 주세요');return;}
  status('unavailable','브라우저 수집 재연결 중');this.retryTimer=setTimeout(()=>{if(!this.stopping&&!this.recovery.blocked)launch();},delay);
 };
 child.on('error',()=>{this.recovery.error('SPACE_UNAVAILABLE');status('unavailable','Ego Browser 실행 실패');});
 child.on('close',code=>{if(this.child!==child)return;this.closed=true;this.diagnostic.ready=false;this.diagnostic.exitCode=code;this.failPending();retry();});
 };
 this.dataStream.on('data',chunk=>{if(invalid)return;buffer+=chunk.toString();if(Buffer.byteLength(buffer)>4*1024*1024){invalid=true;buffer='';status('stale','수집 파이프 크기 제한');void this.control?.write('stop\n');return;}let end;while((end=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,end);buffer=buffer.slice(end+1);try{const m=JSON.parse(line);if(m.kind==='command'){const p=this.pending.get(m.id);if(p){clearTimeout(p.timer);this.pending.delete(m.id);p.resolve(m.result);}}else if(m.kind==='ready'){this.diagnostic.ready=true;if(process.env.BAMI_BROWSER_SPACE_FILE&&Number.isSafeInteger(m.spaceId)&&m.spaceId>0)void writeFile(process.env.BAMI_BROWSER_SPACE_FILE,String(m.spaceId)+'\n',{mode:0o600}).catch(()=>{});this.workerId=m.workerId;this.diagnostic.spaceId=m.spaceId;this.recovery.ready(m.spaceId,Date.now());}else if(m.kind==='stopped'){this.diagnostic.finished=m.finished===true;}else if(m.kind==='media'){const entry=this.media.put(Buffer.from(m.data,'base64'));if(entry.assetId!==m.assetId)throw Error('HASH');}else if(m.kind==='row'){const event=tracker.observe(m.row);if(event)emit(event);}else if(m.kind==='heartbeat'){last=Date.now();this.recovery.heartbeat(last);status('live','브라우저에 표시된 대화 · 완료와 작업 상태 확인 불가');}else if(m.kind==='error'){this.recovery.error(m.code);this.failPending(m.code);if(this.recovery.blocked)clearTimeout(this.retryTimer);status('unavailable','브라우저 주소·로그인·화면 구조 또는 제어 상태를 확인해 주세요');}}catch{invalid=true;buffer='';status('stale','브라우저 수집 데이터 검증 실패');void this.control?.write('stop\n');break;}}});
 this.dataStream.on('error',()=>status('unavailable','수집 파이프 연결 실패'));this.launch=launch;launch();this.timer=setInterval(()=>{if(!this.recovery.blocked&&Date.now()-last>15000)status('stale','브라우저 수집 응답 없음');},5000);
 }
 private failPending(code='SOURCE_UNAVAILABLE'){for(const p of this.pending.values()){clearTimeout(p.timer);p.reject(Error(code));}this.pending.clear();}
 private async request(kind:'sync'|'send'|'text',presetId?:PresetId,text?:string):Promise<any>{
  if(this.stopping||this.recovery.blocked)throw Error(this.recovery.reason??'SOURCE_UNAVAILABLE');
  if(this.pending.size)throw Error('BUSY');
  if(this.closed&&this.recovery.spaceId!==null){clearTimeout(this.retryTimer);this.launch?.();}
  const deadline=Date.now()+5000;while(!this.diagnostic.ready&&Date.now()<deadline&&!this.recovery.blocked)await new Promise(r=>setTimeout(r,50));
  if(!this.diagnostic.ready||this.recovery.blocked)throw Error(this.recovery.reason??'SOURCE_UNAVAILABLE');
  const id=randomUUID();return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{this.pending.delete(id);this.diagnostic.ready=false;this.child?.kill('SIGTERM');reject(Error('OPERATION_TIMEOUT'));},20000);this.pending.set(id,{resolve,reject,timer});void this.control?.write(JSON.stringify({id,kind,presetId,text,workerId:this.workerId})+'\n').catch(()=>{this.failPending();});});
 }
 async refresh():Promise<{observedAt:string}>{return this.request('sync');}
 async sendText(text:string):Promise<SendResult>{try{return await this.request('text',undefined,text);}catch(error){const code=error instanceof Error?error.message:'SOURCE_UNAVAILABLE';return {status:code==='OPERATION_TIMEOUT'||code==='SOURCE_UNAVAILABLE'?'uncertain':'failed',code};}}
 async sendPreset(id:PresetId):Promise<SendResult>{try{return await this.request('send',id);}catch(error){const code=error instanceof Error?error.message:'SOURCE_UNAVAILABLE';return {status:code==='OPERATION_TIMEOUT'||code==='SOURCE_UNAVAILABLE'?'uncertain':'failed',code};}}
 async stop(){this.failPending();this.stopping=true;clearInterval(this.timer);clearTimeout(this.retryTimer);const child=this.child;if(child?.pid&&!this.closed){const exited=new Promise<void>(r=>child.once('close',()=>r()));await this.control?.write(process.env.BAMI_KEEP_BROWSER_PAGE_ON_STOP==='1'?'detach\n':'stop\n');const timer=setTimeout(()=>child.kill('SIGTERM'),10000);await exited;clearTimeout(timer);}this.dataStream?.destroy();await this.dataHandle?.close();await this.control?.close();if(this.pipeDir)await rm(this.pipeDir,{recursive:true,force:true});this.media.clear();}
}
