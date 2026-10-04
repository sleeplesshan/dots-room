import { statSync,openSync,readSync,closeSync } from 'node:fs';
import { Readable } from 'node:stream';
import { SourceAdapter } from './adapter.js';
import { Input,inputSchema,LIMITS } from '../protocol.js';
/** Split before appending: a newline-free or oversize line never grows the retained buffer beyond 8 MiB. */
export class BoundedLines {
 private partial=Buffer.alloc(0);private discarded=false;
 constructor(private line:(line:Buffer)=>void,private reject:()=>void){}
 write(chunk:Buffer){let at=0;while(at<chunk.length){const end=chunk.indexOf(10,at);const part=chunk.subarray(at,end<0?chunk.length:end);
  if(!this.discarded){if(this.partial.length+part.length>LIMITS.logical){this.partial=Buffer.alloc(0);this.discarded=true;this.reject()}else this.partial=Buffer.concat([this.partial,part]);}
  if(end<0)break;if(!this.discarded&&this.partial.length)this.line(this.partial);this.partial=Buffer.alloc(0);this.discarded=false;at=end+1;
 }}
 get retainedBytes(){return this.partial.length}
}
const rejected=()=>process.stderr.write('수동 이벤트 거부: 계약 또는 크기 오류\n');
function lines(emit:(e:Input)=>void){return new BoundedLines(line=>{try{emit(inputSchema.parse(JSON.parse(line.toString('utf8'))))}catch{rejected()}},rejected)}
export class ManualAdapter implements SourceAdapter {
 source={adapterId:'manual-stdin-v1',sourceMode:'manual' as const,sourceSessionId:'manual'};private onData?:(data:Buffer)=>void;
 constructor(private input:Readable=process.stdin){}
 start(emit:(e:Input)=>void){emit({type:'source.status',payload:{state:'manual',lastObservedAt:new Date().toISOString(),detail:'수동 입력 · 자동 dots 연결이 아니에요'}});const reader=lines(emit);this.onData=data=>reader.write(Buffer.from(data));this.input.on('data',this.onData)}
 stop(){if(this.onData)this.input.off('data',this.onData)}
}
export class FileAdapter implements SourceAdapter {
 source={adapterId:'user-jsonl-v1',sourceMode:'manual' as const,sourceSessionId:'user-file'};private timer?:NodeJS.Timeout;private offset=0;private identity?:string;private stopped=false;
 constructor(readonly file:string){}
 start(emit:(e:Input)=>void){emit({type:'source.status',payload:{state:'manual',lastObservedAt:new Date().toISOString(),detail:'사용자 이벤트 파일 · 원천 자동 구독 여부 미검증'}});const reader=lines(emit);
  const scan=()=>{if(this.stopped)return;try{const stat=statSync(this.file),identity=`${stat.dev}:${stat.ino}`;
   if(!stat.isFile()||(this.identity!==undefined&&this.identity!==identity)||stat.size<this.offset){this.stop();emit({type:'source.status',payload:{state:'stale',lastObservedAt:new Date().toISOString(),detail:'파일 교체·잘림: 재시작 후 새 파일을 읽어 주세요'}});return}
   this.identity=identity;if(stat.size===this.offset)return;const fd=openSync(this.file,'r');try{const b=Buffer.alloc(Math.min(stat.size-this.offset,1024*1024)),n=readSync(fd,b,0,b.length,this.offset);this.offset+=n;reader.write(b.subarray(0,n))}finally{closeSync(fd)}
  }catch{this.stop();emit({type:'source.status',payload:{state:'stale',lastObservedAt:new Date().toISOString(),detail:'파일을 읽을 수 없어요. 재시작 후 확인해 주세요'}})}};
  scan();if(!this.stopped)this.timer=setInterval(scan,500)
 }
 stop(){this.stopped=true;if(this.timer)clearInterval(this.timer)}
}
