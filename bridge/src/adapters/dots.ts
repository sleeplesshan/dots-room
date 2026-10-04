import { SourceAdapter } from './adapter.js';
import { Input } from '../protocol.js';
export class DotsAdapter implements SourceAdapter {
 source={adapterId:'codex-dots-not-implemented',sourceMode:'live' as const,sourceSessionId:null};
 start(emit:(e:Input)=>void){emit({type:'source.status',payload:{state:'unavailable',lastObservedAt:null,detail:'NOT_IMPLEMENTED · 공식 dots 대화 구독·내보내기 인터페이스가 필요해요'}});}
 stop(){}
}
