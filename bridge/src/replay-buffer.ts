import { Event,LIMITS } from './protocol.js';
export class ReplayBuffer {
 items:{event:Event,bytes:number,at:number}[]=[];bytes=0;
 constructor(readonly limits={count:LIMITS.replayCount,bytes:LIMITS.replayBytes,age:LIMITS.replayAge},readonly now=()=>performance.now()){}
 push(event:Event){const bytes=Buffer.byteLength(JSON.stringify(event));this.items.push({event,bytes,at:this.now()});this.bytes+=bytes;this.trim();}
 trim(){while(this.items.length&&(this.items.length>this.limits.count||this.bytes>this.limits.bytes||this.now()-this.items[0]!.at>this.limits.age)){this.bytes-=this.items.shift()!.bytes;}}
 after(streamId:string,seq:number,current:number):Event[]|null{this.trim();if(seq>current)return null;if(seq===current)return [];const result=this.items.filter(x=>x.event.streamId===streamId&&x.event.seq>seq).map(x=>x.event);return result.length===current-seq&&result.every((e,i)=>e.seq===seq+i+1)?result:null;}
}
