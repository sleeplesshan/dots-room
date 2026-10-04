import { z } from 'zod';
export const PRESETS={briefing:'지금 뭐하고 있어? 브리핑해줘',thanks:'고마워',stop:'멈춰',later:'알겠어. 이따 확인해 볼게',continue:'계속해줘'} as const;
export type PresetId=keyof typeof PRESETS;
export type SendResult={status:'confirmed'|'failed'|'uncertain';code?:string;messageId?:string};
export interface PresetMessageSender {sendPreset(id:PresetId):Promise<SendResult>}
export const MAX_TEXT_LENGTH=2000;
export interface TextMessageSender {sendText(text:string):Promise<SendResult>}
export const textCommandSchema=z.object({commandId:z.uuid(),text:z.string().refine(v=>v.trim().length>0&&Array.from(v).length<=MAX_TEXT_LENGTH,'TEXT_LENGTH')}).strict();
export interface SourceRefresher {refresh():Promise<{observedAt:string}>}
export const commandSchema=z.object({commandId:z.uuid(),presetId:z.enum(['briefing','thanks','stop','later','continue'])}).strict();
export const syncSchema=z.object({commandId:z.uuid()}).strict();
/** Single browser operation at a time. IDs bind to their operation; uncertain sends are never retried. */
export class CommandGate {
 private active=false;private results=new Map<string,{key:string,at:number,result:Promise<unknown>}>();
 run<T>(id:string,key:string,work:()=>Promise<T>):Promise<T>{
  const old=this.results.get(id);if(old){if(old.key!==key)throw Error('COMMAND_ID_CONFLICT');return old.result as Promise<T>;}
  if(this.active)throw Error('BUSY');const now=Date.now();for(const [id,r] of this.results)if(now-r.at>15*60*1000)this.results.delete(id);
  if(this.results.size>=256)throw Error('COMMAND_CACHE_FULL');this.active=true;
  const result=Promise.resolve().then(work).finally(()=>{this.active=false;});this.results.set(id,{key,at:now,result});return result;
 }
}
