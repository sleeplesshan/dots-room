import {spawn,execFile,ChildProcessWithoutNullStreams} from 'node:child_process';
import {mkdtemp,open,rm} from 'node:fs/promises';
import {createReadStream,ReadStream} from 'node:fs';
import {tmpdir} from 'node:os';import {promisify} from 'node:util';
import {DeskStateStore} from './desk-state.js';
/** Dedicated Page only. No connector credentials or private HTTP APIs. Private FIFO IPC. */
export class NotesBrowser {
 private child?:ChildProcessWithoutNullStreams;private dir?:string;private stream?:ReadStream;private data?:import('node:fs/promises').FileHandle;private control?:import('node:fs/promises').FileHandle;private stopped=false;private blocked=false;private retry?:NodeJS.Timeout;private retryCount=0;
 constructor(private store:DeskStateStore,readonly pageId:string,readonly spaceId:number,private executable='ego-browser'){if(!/^page_[a-f0-9]{32}$/.test(pageId)||!Number.isSafeInteger(spaceId)||spaceId<1)throw Error('NOTES_CONFIG')}
 async start(){this.dir=await mkdtemp(tmpdir()+'/bami-notes-');const data=this.dir+'/data.pipe',stop=this.dir+'/stop.pipe';for(const p of [data,stop])await promisify(execFile)('/usr/bin/mkfifo',['-m','600',p]);this.data=await open(data,'r+');this.control=await open(stop,'r+');this.stream=createReadStream(data,{encoding:'utf8'});let buffer='';
 this.stream.on('data',c=>{buffer+=c;if(Buffer.byteLength(buffer)>256*1024){buffer='';this.error('NOTES_TOO_LARGE');void this.control?.write('stop\n');return;}let i;while((i=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,i);buffer=buffer.slice(i+1);try{const m=JSON.parse(line);if(m.kind==='notes'){this.store.notesRows(this.pageId,m.rows,m.observedAt);this.retryCount=0;}else if(m.kind==='error'){this.blocked=true;this.error(['USER_CONTROL','ADDRESS_CHANGED','UNSUPPORTED_OR_LOGIN','NOTES_STRUCTURE_CHANGED','NOTES_TOO_LARGE','SPACE_UNAVAILABLE'].includes(m.code)?m.code:'READ_FAILED')} }catch(e){this.error(e instanceof Error&&['NOTES_TOO_LARGE','NOTES_STRUCTURE_CHANGED'].includes(e.message)?e.message:'READ_FAILED')}}});
 const code=`const {runNotes}=await import(${JSON.stringify(new URL('./browser/notes-worker.mjs',import.meta.url).href)});await runNotes(taskSpace,${JSON.stringify(this.spaceId)},${JSON.stringify(this.pageId)},${JSON.stringify(stop)},${JSON.stringify(data)});`;
 const launch=()=>{if(this.stopped||this.blocked)return;this.child=spawn(this.executable,['nodejs','-e',code],{stdio:'pipe'});this.child.stdin.end();this.child.stdout.on('data',()=>{});this.child.stderr.on('data',()=>{});this.child.on('error',()=>this.error('SPACE_UNAVAILABLE'));this.child.on('exit',()=>{if(!this.stopped&&!this.blocked){this.error('READER_STOPPED');this.retry=setTimeout(launch,Math.min(30000,1000*2**Math.min(this.retryCount++,5)));}});};launch();
 }
 private error(code:string){this.store.notesError(this.pageId,code,new Date().toISOString())}
 async stop(){this.stopped=true;clearTimeout(this.retry);await this.control?.write('stop\n');if(this.child?.exitCode===null){const child=this.child;await Promise.race([new Promise<void>(r=>child.once('close',()=>r())),new Promise<void>(r=>setTimeout(()=>{child.kill();r()},4000))]);}this.stream?.destroy();await this.control?.close();await this.data?.close();if(this.dir)await rm(this.dir,{recursive:true,force:true})}
}
