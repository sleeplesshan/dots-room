import { createHash } from 'node:crypto';
export const MEDIA_LIMIT=2*1024*1024, CACHE_LIMIT=32*1024*1024;
export class MediaStore {
 private entries=new Map<string,Buffer>();bytes=0;
 constructor(readonly limit=CACHE_LIMIT){}
 put(bytes:Buffer){if(bytes.length>MEDIA_LIMIT||bytes.length<24||!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))||bytes.toString('ascii',12,16)!=='IHDR')throw Error('IMAGE_INVALID');const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20);if(!width||!height||width>1024||height>1024)throw Error('IMAGE_DIMENSIONS');const assetId=createHash('sha256').update(bytes).digest('hex');if(!this.entries.has(assetId)){while(this.bytes+bytes.length>this.limit&&this.entries.size){const first=this.entries.keys().next().value!;this.bytes-=this.entries.get(first)!.length;this.entries.delete(first);}if(bytes.length>this.limit)throw Error('IMAGE_CACHE_LIMIT');this.entries.set(assetId,Buffer.from(bytes));this.bytes+=bytes.length;}return {assetId,status:'available' as const,width,height,alt:'',error:null};}
 get(id:string){const b=this.entries.get(id);if(b){this.entries.delete(id);this.entries.set(id,b);}return b;}
 clear(){this.entries.clear();this.bytes=0;}
}
