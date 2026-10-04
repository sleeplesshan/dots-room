import {z} from 'zod';
import {createHash} from 'node:crypto';
const date=z.iso.datetime({offset:true});
export const executionTaskSchema=z.object({taskId:z.string().min(1).max(200),state:z.enum(['working','waiting_user','blocked','responding','idle','completed','unknown']),phase:z.enum(['reading','working','responding','none']),origin:z.enum(['codex-app-server','fixture']),observedAt:date,completionEventId:z.string().max(200).nullable()}).strict();
export const executionSchema=z.object({knowledge:z.enum(['known','unknown']),source:z.enum(['codex-app-server','browser-ui','fixture','unavailable']),observedAt:date.nullable(),freshness:z.enum(['fresh','stale','unknown']),reason:z.string().max(200),tasks:z.array(executionTaskSchema).max(64)}).strict();
export const noteSectionSchema=z.object({id:z.enum(['doing','waiting','done','free']),title:z.string().max(80),items:z.array(z.object({id:z.string().max(100),text:z.string().max(8000)}).strict()).max(100)}).strict();
export const notesSchema=z.object({pageId:z.string().regex(/^page_[a-f0-9]{32}$/).nullable(),url:z.string().regex(/^https:\/\/chatgpt\.com\/space\/page_[a-f0-9]{32}$/).nullable(),status:z.enum(['ready','empty','error','unconfigured']),revision:z.number().int().nonnegative(),sections:z.array(noteSectionSchema).max(4),sourceUpdatedAt:date.nullable(),observedAt:date.nullable(),lastSuccessAt:date.nullable(),freshness:z.enum(['fresh','stale','unknown']),error:z.string().max(120),writer:z.literal('user'),declaredUpdateText:z.string().max(200)}).strict();
export const deskStateSchema=z.object({schemaVersion:z.literal(1),execution:executionSchema,notes:notesSchema}).strict();
export type Execution=z.infer<typeof executionSchema>;export type Notes=z.infer<typeof notesSchema>;export type DeskState=z.infer<typeof deskStateSchema>;
export const unknownExecution=(reason='REAL_STATE_UNAVAILABLE'):Execution=>({knowledge:'unknown',source:'unavailable',observedAt:null,freshness:'unknown',reason,tasks:[]});
export const emptyNotes=():Notes=>({pageId:null,url:null,status:'unconfigured',revision:0,sections:[],sourceUpdatedAt:null,observedAt:null,lastSuccessAt:null,freshness:'unknown',error:'NOT_CONFIGURED',writer:'user',declaredUpdateText:''});
export const sectionTitles=['지금 하는 일','형아 확인이 필요한 일','오늘 끝낸 일','형아 자유 메모'];const ids=['doing','waiting','done','free'] as const;
export class DeskStateStore {
 value:DeskState={schemaVersion:1,execution:unknownExecution(),notes:emptyNotes()};
 execution(e:Execution){this.value={...this.value,execution:executionSchema.parse(e)}}
 notesRows(pageId:string,rows:{heading:string|null,text:string}[],observedAt:string){
  const sections=ids.map((id,i)=>({id,title:sectionTitles[i]!,items:[] as {id:string,text:string}[]}));let section=-1,declared='';const occurrences=new Map<string,number>();let count=0,total=0;
  for(const r of rows){if(r.heading!==null){section=sectionTitles.indexOf(r.heading);if(r.heading==='마지막 갱신 시각')section=4;continue;}const text=r.text.trim();if(!text||section<0)continue;if(section===4){declared=(declared+' '+text).trim().slice(0,200);continue;}if(++count>400||text.length>8000||(total+=Buffer.byteLength(text))>65536)throw Error('NOTES_TOO_LARGE');const hash=createHash('sha256').update(sections[section]!.id+'\0'+text).digest('hex').slice(0,24);const n=occurrences.get(hash)??0;occurrences.set(hash,n+1);sections[section]!.items.push({id:hash+':'+n,text});}
  if(!sectionTitles.every(h=>rows.some(r=>r.heading===h))&&rows.some(r=>r.text.trim()))throw Error('NOTES_STRUCTURE_CHANGED');
  const old=this.value.notes;const same=JSON.stringify(old.sections)===JSON.stringify(sections)&&old.pageId===pageId&&old.declaredUpdateText===declared;
  this.value={...this.value,notes:notesSchema.parse({pageId,url:'https://chatgpt.com/space/'+pageId,status:count===0?'empty':'ready',revision:same?old.revision:old.revision+1,sections,sourceUpdatedAt:null,observedAt,lastSuccessAt:observedAt,freshness:'fresh',error:'',writer:'user',declaredUpdateText:declared})};
 }
 notesError(pageId:string,code:string,observedAt:string){this.value={...this.value,notes:{...this.value.notes,pageId,url:'https://chatgpt.com/space/'+pageId,status:'error',freshness:'stale',error:code.slice(0,120),observedAt}};}
 snapshot(now=Date.now()){const s=structuredClone(this.value);if(s.execution.observedAt&&now-Date.parse(s.execution.observedAt)>15000)s.execution.freshness='stale';if(s.notes.lastSuccessAt&&now-Date.parse(s.notes.lastSuccessAt)>15000)s.notes.freshness='stale';return deskStateSchema.parse(s)}
}
