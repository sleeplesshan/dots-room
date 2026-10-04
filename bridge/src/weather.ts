import {regionSchema,defaultRegion} from './config.js';
import {z} from 'zod';

export const WEATHER_INTERVAL=15*60_000,WEATHER_STALE=45*60_000,WEATHER_EXPIRED=3*60*60_000;
export const REGION=defaultRegion;
const date=z.iso.datetime({offset:true});
const temperature=z.number().finite().min(-90).max(65).nullable();
export const weatherReadingSchema=z.object({validAt:date,code:z.union([z.literal(0),z.literal(1),z.literal(2),z.literal(3),z.literal(45),z.literal(48),z.literal(51),z.literal(53),z.literal(55),z.literal(56),z.literal(57),z.literal(61),z.literal(63),z.literal(65),z.literal(66),z.literal(67),z.literal(71),z.literal(73),z.literal(75),z.literal(77),z.literal(80),z.literal(81),z.literal(82),z.literal(85),z.literal(86),z.literal(95),z.literal(96),z.literal(99)]).nullable(),temperatureC:temperature,apparentC:temperature,humidityPercent:z.number().min(0).max(100).nullable(),precipitationMm:z.number().min(0).max(1000).nullable(),windMs:z.number().min(0).max(150).nullable(),isDay:z.boolean().nullable(),minC:temperature,maxC:temperature}).strict();
export const weatherSchema=z.object({schemaVersion:z.literal(1),provider:z.literal('open-meteo'),kind:z.literal('model'),region:regionSchema,status:z.enum(['ready','stale','unavailable']),fetchedAt:date.nullable(),attemptedAt:date.nullable(),error:z.enum(['','NOT_FETCHED','NETWORK','TIMEOUT','INVALID_RESPONSE']),reading:weatherReadingSchema.nullable()}).strict();
export type WeatherReading=z.infer<typeof weatherReadingSchema>;
export type Weather=z.infer<typeof weatherSchema>;
const units=z.object({temperature_2m:z.literal('°C'),apparent_temperature:z.literal('°C'),relative_humidity_2m:z.literal('%'),precipitation:z.literal('mm'),wind_speed_10m:z.literal('m/s')});
const apiSchema=z.object({timezone:z.string(),utc_offset_seconds:z.number().int().min(-50400).max(50400),current_units:units,current:z.object({time:z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/),weather_code:z.number().int().nullable(),temperature_2m:temperature,apparent_temperature:temperature,relative_humidity_2m:z.number().nullable(),precipitation:z.number().nullable(),wind_speed_10m:z.number().nullable(),is_day:z.union([z.literal(0),z.literal(1)]).nullable()}),daily:z.object({time:z.array(z.string()).min(1),temperature_2m_min:z.array(temperature).min(1),temperature_2m_max:z.array(temperature).min(1)}),daily_units:z.object({temperature_2m_min:z.literal('°C'),temperature_2m_max:z.literal('°C')})});
export function normalizeWeather(value:unknown,region=REGION):WeatherReading {
 const data=apiSchema.parse(value),c=data.current;if(data.timezone!==region.timezone)throw Error('INVALID_RESPONSE');
 if(data.daily.time[0]!==c.time.slice(0,10))throw Error('INVALID_RESPONSE');
 return weatherReadingSchema.parse({validAt:new Date(Date.parse(c.time+'Z')-data.utc_offset_seconds*1000).toISOString(),code:c.weather_code,temperatureC:c.temperature_2m,apparentC:c.apparent_temperature,humidityPercent:c.relative_humidity_2m,precipitationMm:c.precipitation,windMs:c.wind_speed_10m,isDay:c.is_day===null?null:c.is_day===1,minC:data.daily.temperature_2m_min[0],maxC:data.daily.temperature_2m_max[0]});
}
export const weatherUrl=(region=REGION)=>{const url=new URL('https://api.open-meteo.com/v1/forecast');url.search=new URLSearchParams({latitude:String(region.latitude),longitude:String(region.longitude),current:'temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m',daily:'temperature_2m_max,temperature_2m_min',timezone:region.timezone,wind_speed_unit:'ms',forecast_days:'1'}).toString();return url.toString()};
async function loadWeather(signal:AbortSignal,region=REGION):Promise<unknown> {
 const response=await fetch(weatherUrl(region),{signal,redirect:'error',headers:{Accept:'application/json'}});
 if(!response.ok)throw Error('NETWORK');
 if(Number(response.headers.get('content-length')??0)>131072)throw Error('INVALID_RESPONSE');
 const reader=response.body?.getReader();if(!reader)throw Error('INVALID_RESPONSE');
 const parts:Uint8Array[]=[];let bytes=0;
 try{while(true){const r=await reader.read();if(r.done)break;bytes+=r.value.length;if(bytes>131072)throw Error('INVALID_RESPONSE');parts.push(r.value)}}finally{await reader.cancel()}
 try{return JSON.parse(Buffer.concat(parts).toString('utf8'))}catch{throw Error('INVALID_RESPONSE')}
}
/** A regional public-data cache. HTTP reads never trigger upstream polling or expose authentication. */
export class WeatherStore {
 value:Weather={schemaVersion:1,provider:'open-meteo',kind:'model',region:REGION,status:'unavailable',fetchedAt:null,attemptedAt:null,error:'NOT_FETCHED',reading:null};
 private timer?:NodeJS.Timeout;private flight?:Promise<void>;private controller?:AbortController;private stopped=false;
 constructor(private load:((signal:AbortSignal)=>Promise<unknown>)|undefined=undefined,private clock:()=>number=Date.now,readonly region=REGION){regionSchema.parse(region);this.value.region=region;}
 start(){this.stopped=false;void this.refresh();this.timer=setInterval(()=>void this.refresh(),WEATHER_INTERVAL);this.timer.unref()}
 stop(){this.stopped=true;if(this.timer)clearInterval(this.timer);this.controller?.abort()}
 refresh():Promise<void>{if(this.flight)return this.flight;this.flight=this.read().finally(()=>{this.flight=undefined});return this.flight}
 private async read(){const controller=new AbortController();this.controller=controller;const timeout=setTimeout(()=>controller.abort(),10000);const attemptedAt=new Date(this.clock()).toISOString();
  try{const reading=normalizeWeather(await (this.load?this.load(controller.signal):loadWeather(controller.signal,this.region)),this.region);const fetched=this.clock();if(Math.abs(fetched-Date.parse(reading.validAt))>WEATHER_STALE)throw Error('INVALID_RESPONSE');if(this.stopped)return;this.value={...this.value,reading,status:'ready',fetchedAt:new Date(fetched).toISOString(),attemptedAt,error:''}}
  catch(error){if(this.stopped)return;const code=controller.signal.aborted?'TIMEOUT':error instanceof z.ZodError||error instanceof Error&&error.message==='INVALID_RESPONSE'?'INVALID_RESPONSE':'NETWORK';this.value={...this.value,status:this.value.reading?'stale':'unavailable',attemptedAt,error:code}}
  finally{clearTimeout(timeout);if(this.controller===controller)this.controller=undefined}
 }
 snapshot(now=this.clock()):Weather {const s=structuredClone(this.value);if(s.reading&&s.fetchedAt&&(now-Date.parse(s.fetchedAt)>=WEATHER_STALE||now-Date.parse(s.reading.validAt)>=WEATHER_STALE))s.status='stale';return weatherSchema.parse(s)}
}
