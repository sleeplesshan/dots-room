import { Input,Event } from '../protocol.js';
export interface SourceAdapter {source:Event['source'];start(emit:(e:Input)=>void):void|Promise<void>;stop():void|Promise<void>}
