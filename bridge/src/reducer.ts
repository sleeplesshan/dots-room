import { Event, State, Message, LIMITS, initialState } from './protocol.js';
export class ResyncRequired extends Error {}
export function reduce(state:State,e:Event):State {
 const s=structuredClone(state),p=e.payload;
 if(e.type.startsWith('message.')){const existing=s.messages.find(m=>m.messageId===p.messageId);if(existing&&(existing.conversationId!==e.conversationId||existing.role!==p.role||existing.taskId!==e.taskId))throw new ResyncRequired('MESSAGE_BINDING');}
 if(['message.completed','message.upsert','message.observed'].includes(e.type)&&Buffer.byteLength(p.content)>2*1024*1024)throw Error('MESSAGE_LIMIT');
 if(e.type==='state.snapshot'&&(new Set(p.messages.map((m:Message)=>m.messageId)).size!==p.messages.length||p.messages.some((m:Message)=>Buffer.byteLength(m.content)>2*1024*1024)||Buffer.byteLength(JSON.stringify(p.messages))>LIMITS.messageBytes))throw Error('SNAPSHOT_LIMIT');
 switch(e.type){
 case 'state.snapshot': return {sourceStatus:p.sourceStatus,tasks:p.tasks,messages:p.messages,usage:p.usage,quotas:p.quotas,history:p.history};
 case 'source.status':s.sourceStatus=p;break;
 case 'task.upsert':{const i=s.tasks.findIndex(t=>t.taskId===e.taskId);const t={...p,taskId:e.taskId!,conversationId:e.conversationId!};if(i<0){if(s.tasks.length>=LIMITS.tasks)throw Error('TASK_LIMIT');s.tasks.push(t);}else s.tasks[i]=t;break;}
 case 'task.remove':s.tasks=s.tasks.filter(t=>t.taskId!==e.taskId);break;
 case 'message.observed':{const old=s.messages.find(m=>m.messageId===p.messageId);if(old&&p.revision<=old.revision)break;const m:Message={messageId:p.messageId,conversationId:e.conversationId!,role:p.role,revision:p.revision,content:p.content,state:'observed',nextDelta:0,completedAt:null,observation:p.observation,images:p.images};const i=s.messages.findIndex(m=>m.messageId===p.messageId);if(i<0)s.messages.push(m);else s.messages[i]=m;break;}
 case 'message.start':{const old=s.messages.find(m=>m.messageId===p.messageId);if(!old||p.revision>old.revision){s.messages=s.messages.filter(m=>m.messageId!==p.messageId);s.messages.push({messageId:p.messageId,conversationId:e.conversationId!,...(e.taskId?{taskId:e.taskId}:{}),role:p.role,revision:p.revision,content:'',state:'streaming',nextDelta:0,completedAt:null});}break;}
 case 'message.delta':{const m=s.messages.find(m=>m.messageId===p.messageId);if(m&&p.revision<m.revision)break;if(!m||p.revision!==m.revision)throw new ResyncRequired('DELTA_REVISION');if(m.state==='complete'||p.deltaIndex<m.nextDelta)break;if(p.deltaIndex!==m.nextDelta)throw new ResyncRequired('DELTA_GAP');if(Buffer.byteLength(m.content+p.text)>2*1024*1024)throw Error('MESSAGE_LIMIT');if(m.nextDelta>=2147483647)throw Error('DELTA_LIMIT');m.content+=p.text;m.nextDelta++;break;}
 case 'message.completed':case 'message.upsert':{const old=s.messages.find(m=>m.messageId===p.messageId);if(old&&(p.revision<old.revision||(p.revision===old.revision&&old.state==='complete')))break;const m:Message={messageId:p.messageId,conversationId:e.conversationId!,...(e.taskId?{taskId:e.taskId}:{}),role:p.role,revision:p.revision,content:p.content,state:'complete',nextDelta:old?.nextDelta??0,completedAt:p.completedAt};const i=s.messages.findIndex(m=>m.messageId===p.messageId);if(i<0)s.messages.push(m);else s.messages[i]=m;break;}
 case 'usage.update':s.usage=p.usage;s.quotas=p.quotas;break;
 }
 let bytes=Buffer.byteLength(JSON.stringify(s.messages));while(s.messages.length>LIMITS.messages||bytes>LIMITS.messageBytes){const removed=s.messages.shift()!;bytes-=Buffer.byteLength(JSON.stringify(removed))+(s.messages.length?1:0);s.history.truncated=true;}
 s.history.retainedMessageCount=s.messages.length;return s;
}
export class Replica {
 state=initialState();streamId:string|null=null;seq=0;private ids=new Set<string>();
 apply(e:Event):boolean{if(e.type==='state.snapshot'){if(this.streamId===e.streamId&&e.seq<=this.seq)return false;this.state=reduce(this.state,e);this.streamId=e.streamId;this.seq=e.seq;this.ids.clear();this.ids.add(e.eventId);return true;}
 if(this.streamId!==e.streamId)throw new ResyncRequired('STREAM_CHANGED');if(e.seq<=this.seq)return false;if(e.seq!==this.seq+1)throw new ResyncRequired('SEQ_GAP');if(this.ids.has(e.eventId))throw new ResyncRequired('EVENT_ID_REUSED');const next=reduce(this.state,e);this.state=next;this.seq=e.seq;this.ids.add(e.eventId);if(this.ids.size>LIMITS.replayCount)this.ids.delete(this.ids.values().next().value!);return true;}
}
