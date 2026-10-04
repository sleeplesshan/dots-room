import { z } from 'zod';
import { mediaSchema,Input } from '../protocol.js';
export const rowSchema=z.object({id:z.string().min(1).max(160),role:z.enum(['user','assistant']),content:z.string().max(2*1024*1024),sourceTimestamp:z.iso.datetime().max(30).nullable(),images:z.array(mediaSchema).max(8)}).strict();
export class ObservationTracker {
 private seen=new Map<string,{signature:string,revision:number}>();
 observe(raw:unknown):Input|null{const r=rowSchema.parse(raw),signature=JSON.stringify(r),old=this.seen.get(r.id);if(old?.signature===signature)return null;const revision=(old?.revision??0)+1;this.seen.delete(r.id);this.seen.set(r.id,{signature,revision});if(this.seen.size>1000)this.seen.delete(this.seen.keys().next().value!);return {type:'message.observed',conversationId:'browser-dots-local',payload:{messageId:r.id,role:r.role,content:r.content,revision,images:r.images,observation:{origin:'browser-ui',sourceMessageId:r.id,sourceConversationId:null,sourceTimestamp:r.sourceTimestamp,observedAt:new Date().toISOString(),sourceRevision:null,revisionOrigin:'local-observation',finality:'unknown',statusProvenance:'unknown'}}};}
}
