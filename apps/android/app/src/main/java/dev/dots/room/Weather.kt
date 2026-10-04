package dev.dots.room

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

@Serializable data class WeatherRegion(val name:String="서울",val latitude:Double=37.5665,val longitude:Double=126.9780,val timezone:String="Asia/Seoul")
@Serializable data class WeatherReading(val validAt:String,val code:Int?=null,val temperatureC:Double?=null,val apparentC:Double?=null,val humidityPercent:Double?=null,val precipitationMm:Double?=null,val windMs:Double?=null,val isDay:Boolean?=null,val minC:Double?=null,val maxC:Double?=null)
@Serializable data class WeatherState(val schemaVersion:Int=1,val provider:String="open-meteo",val kind:String="model",val region:WeatherRegion=WeatherRegion(),val status:String="unavailable",val fetchedAt:String?=null,val attemptedAt:String?=null,val error:String="NOT_FETCHED",val reading:WeatherReading?=null){
 fun validate():WeatherState {
  require(schemaVersion==1&&provider=="open-meteo"&&kind=="model"&&region.name.isNotBlank()&&region.name.length<=80&&region.latitude.isFinite()&&region.latitude in -90.0..90.0&&region.longitude.isFinite()&&region.longitude in -180.0..180.0)
  require(status in listOf("ready","stale","unavailable")&&error in listOf("","NOT_FETCHED","NETWORK","TIMEOUT","INVALID_RESPONSE"))
  java.time.ZoneId.of(region.timezone)
  listOfNotNull(fetchedAt,attemptedAt,reading?.validAt).forEach{Instant.parse(it)}
  reading?.let{r->require(fetchedAt!=null);require(r.code==null||r.code in listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99));fun valid(n:Double?,lo:Double,hi:Double)=n==null||n.isFinite()&&n in lo..hi
   listOf(r.temperatureC,r.apparentC,r.minC,r.maxC).forEach{require(valid(it,-90.0,65.0))};require(valid(r.humidityPercent,0.0,100.0)&&valid(r.precipitationMm,0.0,1000.0)&&valid(r.windMs,0.0,150.0))}
  require(status!="ready"||reading!=null)
  return this
 }
}
object WeatherVisual {
 const val STALE_SECONDS=45*60L;const val EXPIRED_SECONDS=3*60*60L
 fun age(s:WeatherState,now:Instant):Long? {val dates=listOfNotNull(s.fetchedAt,s.reading?.validAt);return if(dates.size<2)null else dates.maxOf{Duration.between(Instant.parse(it),now).seconds.coerceAtLeast(0)}}
 fun stale(s:WeatherState,now:Instant)=s.status!="ready"||(age(s,now)?:Long.MAX_VALUE)>=STALE_SECONDS
 fun usable(s:WeatherState,now:Instant)=s.reading!=null&&(age(s,now)?:Long.MAX_VALUE)<EXPIRED_SECONDS
 fun condition(code:Int?)=when(code){0,1->"clear";2->"cloud";3->"overcast";45,48->"fog";in 51..67,in 80..82,95,96,99->"rain";in 71..77,85,86->"snow";else->"neutral"}
 fun label(code:Int?)=when(condition(code)){"clear"->"맑음";"cloud"->"구름 조금";"overcast"->"흐림";"fog"->"안개";"rain"->if(code in listOf(95,96,99))"뇌우"else "비";"snow"->"눈";else->"날씨 미확인"}
 fun key(s:WeatherState,now:ZonedDateTime):String {val usable=usable(s,now.toInstant());val day=if(usable)s.reading?.isDay?: (now.hour in 6..18) else now.hour in 6..18;return (if(day)"day"else "night")+":"+(if(usable)condition(s.reading?.code)else "neutral")}
}
