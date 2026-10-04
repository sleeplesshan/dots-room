import { z } from 'zod';
export const LIMITS={frame:256*1024,logical:8*1024*1024,queue:10*1024*1024,replayCount:10000,replayBytes:10*1024*1024,replayAge:15*60*1000,messages:500,messageBytes:5*1024*1024,tasks:256} as const;
export const mediaSchema=z.object({assetId:z.string().regex(/^[a-f0-9]{64}$/).nullable(),status:z.enum(['pending','available','error','expired']),width:z.number().int().positive().max(1024).nullable(),height:z.number().int().positive().max(1024).nullable(),alt:z.string().max(1000),error:z.string().max(100).nullable()}).strict().superRefine((m,c)=>{if(m.status==='available'&&(!m.assetId||!m.width||!m.height))c.addIssue({code:'custom',message:'AVAILABLE_MEDIA_METADATA'});if(m.status==='error'&&!m.error)c.addIssue({code:'custom',message:'IMAGE_ERROR_REQUIRED'});});
export const observationSchema=z.object({origin:z.literal('browser-ui'),sourceMessageId:z.string().min(1).max(160),sourceConversationId:z.string().max(160).nullable(),sourceTimestamp:z.iso.datetime().max(30).nullable(),observedAt:z.iso.datetime().max(30),sourceRevision:z.number().int().positive().nullable(),revisionOrigin:z.literal('local-observation'),finality:z.literal('unknown'),statusProvenance:z.literal('unknown')}).strict();
const id=z.string().min(1).max(160), timestamp=z.iso.datetime().max(30), revision=z.number().int().positive().max(2147483647);
export const sourceSchema=z.object({adapterId:id,sourceMode:z.enum(['live','manual','mock']),sourceSessionId:id.nullable()}).strict();
const metric=z.object({knowledge:z.enum(['known','unknown']),value:z.number().nonnegative().nullable(),origin:id,scope:id,observedAt:timestamp.nullable(),freshness:z.enum(['fresh','stale','manual','unknown']),resetAt:timestamp.nullable(),unit:id}).strict();
export const usageSchema=z.object({inputTokens:metric,outputTokens:metric}).strict();
export const quotaSchema=metric.extend({limitId:id,name:id,windowDurationMins:z.number().positive().nullable(),usedPercent:z.number().min(0).max(100).nullable()}).strict();
export const taskSchema=z.object({taskId:id,conversationId:id,status:z.enum(['queued','running','waiting_user','completed','failed','canceled','unknown']),phase:z.enum(['reading','thinking','working','responding','none']),publicSummary:z.string().max(4000).refine(v=>Buffer.byteLength(JSON.stringify(v))<=4000,"SUMMARY_BYTES"),priority:z.number().int().min(0).max(100),updatedAt:timestamp}).strict();
export const messageSchema=z.object({messageId:id,conversationId:id,taskId:id.optional(),role:z.enum(['user','assistant']),revision,content:z.string().max(2*1024*1024),state:z.enum(['streaming','complete','observed']),nextDelta:z.number().int().nonnegative().max(2147483647),completedAt:timestamp.nullable(),observation:observationSchema.optional(),images:z.array(mediaSchema).max(8).optional()}).strict();
export const statusSchema=z.object({state:z.enum(['live','unavailable','stale','manual','demo']),lastObservedAt:timestamp.nullable(),detail:z.string().max(2000),taskEvents:z.enum(['supported','unsupported']).optional()}).strict();
export const stateSchema=z.object({sourceStatus:statusSchema,tasks:z.array(taskSchema).max(LIMITS.tasks),messages:z.array(messageSchema).max(LIMITS.messages),usage:usageSchema,quotas:z.array(quotaSchema).max(32),history:z.object({truncated:z.boolean(),retainedMessageCount:z.number().int().nonnegative()}).strict()}).strict();
const base=z.object({schemaVersion:z.literal(2),streamId:z.uuid(),eventId:z.uuid(),seq:z.number().int().positive().max(Number.MAX_SAFE_INTEGER),timestamp,source:sourceSchema,conversationId:id.optional(),taskId:id.optional()});
const mbase={messageId:id,revision};
export const payloadSchemas={
 'server.hello':z.object({heartbeatMs:z.number().positive(),staleMs:z.number().positive(),capabilities:z.array(id)}).strict(),
 'state.snapshot':stateSchema.extend({snapshotId:z.uuid(),asOfSeq:z.number().int().positive()}).strict(),
 'source.status':statusSchema,
 'task.upsert':taskSchema.omit({taskId:true,conversationId:true}),
 'task.remove':z.object({}).strict(),
 'message.observed':z.object({...mbase,role:z.enum(['user','assistant']),content:z.string().max(2*1024*1024),observation:observationSchema,images:z.array(mediaSchema).max(8)}).strict(),
 'message.start':z.object({...mbase,role:z.enum(['user','assistant'])}).strict(),
 'message.delta':z.object({...mbase,role:z.enum(['user','assistant']),deltaIndex:z.number().int().nonnegative().max(2147483647),text:z.string().max(128*1024),isFinal:z.boolean()}).strict(),
 'message.completed':z.object({...mbase,role:z.enum(['user','assistant']),content:z.string().max(2*1024*1024),completedAt:timestamp}).strict(),
 'message.upsert':z.object({...mbase,role:z.enum(['user','assistant']),content:z.string().max(2*1024*1024),completedAt:timestamp}).strict(),
 'usage.update':z.object({usage:usageSchema,quotas:z.array(quotaSchema).max(32)}).strict(),
 'heartbeat':z.object({}).strict(),
 'server.error':z.object({code:id,message:z.string().max(2000),recoverable:z.boolean()}).strict()
} as const;
export type EventType=keyof typeof payloadSchemas;
export const eventSchema=z.discriminatedUnion('type',Object.entries(payloadSchemas).map(([type,payload])=>base.extend({type:z.literal(type),payload}).strict()) as any).superRefine((e:any,c)=>{
 if((e.type.startsWith('message.')||e.type.startsWith('task.'))&&!e.conversationId)c.addIssue({code:'custom',message:'conversationId required'});
 if(e.type.startsWith('task.')&&!e.taskId)c.addIssue({code:'custom',message:'taskId required'});
 if(e.type==='message.observed'&&e.payload.messageId!==e.payload.observation.sourceMessageId)c.addIssue({code:'custom',message:'source message ID mismatch'});
 if(e.type==='state.snapshot'&&e.seq!==e.payload.asOfSeq)c.addIssue({code:'custom',message:'snapshot seq mismatch'});
});
export interface Event {schemaVersion:2;streamId:string;eventId:string;seq:number;timestamp:string;source:z.infer<typeof sourceSchema>;type:EventType;conversationId?:string;taskId?:string;payload:any}
export type State=z.infer<typeof stateSchema>;
export type Message=z.infer<typeof messageSchema>;
export const clientSchema=z.discriminatedUnion('type',[
 z.object({schemaVersion:z.literal(2),type:z.literal('client.hello'),clientInstanceId:id,resume:z.object({streamId:z.uuid(),lastAppliedSeq:z.number().int().nonnegative()}).strict().nullable(),capabilities:z.array(id).max(16)}).strict(),
 z.object({schemaVersion:z.literal(2),type:z.literal('client.ack'),streamId:z.uuid(),lastAppliedSeq:z.number().int().nonnegative()}).strict(),
 z.object({schemaVersion:z.literal(2),type:z.literal('client.resync')}).strict()
]);
export const inputSchema=z.discriminatedUnion('type',Object.entries(payloadSchemas).filter(([type])=>!['server.hello','state.snapshot','heartbeat','server.error'].includes(type)).map(([type,payload])=>z.object({type:z.literal(type),...(type.startsWith('message.')||type.startsWith('task.')?{conversationId:id}:{conversationId:id.optional()}),...(type.startsWith('task.')?{taskId:id}:{taskId:id.optional()}),payload}).strict()) as any);
export type Input=z.infer<typeof inputSchema>;
export function unknownMetric(){return {knowledge:'unknown' as const,value:null,origin:'unavailable',scope:'unavailable',observedAt:null,freshness:'unknown' as const,resetAt:null,unit:'tokens'};}
export function initialState():State{return {sourceStatus:{state:'unavailable',lastObservedAt:null,detail:'형아, 바미 연결을 확인할 수 없어요'},tasks:[],messages:[],usage:{inputTokens:unknownMetric(),outputTokens:unknownMetric()},quotas:[],history:{truncated:false,retainedMessageCount:0}};}
export function wireFrames(e:Event):string[]{const raw=JSON.stringify(e),b=Buffer.from(raw);if(b.length>LIMITS.logical)throw Error('LOGICAL_LIMIT');if(b.length<=LIMITS.frame)return [raw];const size=160*1024,count=Math.ceil(b.length/size);return Array.from({length:count},(_,index)=>JSON.stringify({schemaVersion:2,type:'server.chunk',eventId:e.eventId,streamId:e.streamId,seq:e.seq,index,count,totalBytes:b.length,data:b.subarray(index*size,(index+1)*size).toString('base64')}));}
