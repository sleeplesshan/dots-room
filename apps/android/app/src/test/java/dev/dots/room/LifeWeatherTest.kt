package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime

class LifeWeatherTest {
 private val wall=Instant.parse("2026-10-03T06:45:00Z")
 @Test fun unknownDecorationNeverInventsWorkOrCompletionAndDoesNotRepeat(){
  val data=AppState(sourceStatus=SourceStatus("live"));val desk=DeskState();var b=ActualBehavior.next(data,desk,Behavior("listening"),1000,true,wall);assertEquals("listening",b.action)
  b=ActualBehavior.next(data,desk,b,2200,true,wall);assertEquals("idle",b.action)
  val choices=mutableListOf<String>();repeat(15){val t=b.lifeNextAt;b=ActualBehavior.next(data,desk,b,t,true,wall);choices+=b.lifeChoice;assertEquals("unknown",runtimeState(desk.execution,true,wall));assertEquals(0,b.running);assertTrue(b.completedIds.isEmpty());assertFalse(b.action in listOf("work","think","sleep","celebrate"));assertTrue(b.lifeNextAt-t in 25000..45000)}
  assertTrue(choices.zipWithNext().all{it.first!=it.second});assertTrue(choices.contains("read"));assertTrue(choices.contains("left-lane"));assertEquals("listening",ActualBehavior.next(data,desk,b,500000,false,wall).action)
 }
 @Test fun duplicateRevisionDoesNotRestartSpeechOrLife(){
  val message=Message("reply","visible",role="assistant",revision=1,content="답변",observation=Observation("browser-ui","reply",null,null,wall.toString(),null,"local-observation","unknown","none"))
  val data=AppState(messages=listOf(message),sourceStatus=SourceStatus("live"));var b=ActualBehavior.next(data,DeskState(),Behavior(),1000,true,wall);b=ConversationReaction.speechVisible(b,2000)
  assertEquals("talk",ActualBehavior.next(data,DeskState(),b,6999,true,wall).action)
  b=ActualBehavior.next(data,DeskState(),b,7000,true,wall);assertEquals("listening",b.action);b=ActualBehavior.next(data,DeskState(),b,8200,true,wall);assertEquals("idle",b.action)
  val next=ActualBehavior.next(data.copy(messages=listOf(message.copy(revision=2,content="수정 답변"))),DeskState(),b,9000,true,wall);assertEquals(b.since,next.since);assertEquals(b.lifeNextAt,next.lifeNextAt)
 }
 @Test fun blinkIntervalsAreSparseAndBilateralSheetDoesNotLoop(){val starts=(0L..180000L).filter{LifeDirector.blinkElapsed(it,0)==0L};assertTrue(starts.size in 10..16);assertTrue(starts.zipWithNext().all{it.second-it.first in 8000..16000});val m=manifest("home");val blink=m.animations.first{it.assetId=="blink-S"};assertFalse(blink.loop);assertEquals(6,blink.frameRects.size);assertEquals(4800,m.animations.first{it.assetId=="breath-S"}.frameDurationsMs.sum());for(id in listOf("breath-S","look-S","talk-S"))assertEquals(12,m.animations.first{it.assetId==id}.frameRects.size)}
 @Test fun sharedWeatherGoldenZeroNullAgeAndMappings(){val s=protocolJson.decodeFromString<WeatherState>(File("../../../protocol/fixtures/weather-ready-zero.json").readText()).validate();assertEquals(0.0,s.reading!!.temperatureC!!,0.0);assertEquals(0.0,s.reading.precipitationMm!!,0.0);assertNull(s.reading.apparentC);assertFalse(WeatherVisual.stale(s,wall));assertTrue(WeatherVisual.stale(s,wall.plusSeconds(2700)));assertTrue(WeatherVisual.usable(s,wall.plusSeconds(10799)));assertFalse(WeatherVisual.usable(s,wall.plusSeconds(10800)));assertEquals("rain",WeatherVisual.condition(95));assertEquals("snow",WeatherVisual.condition(85));assertEquals("fog",WeatherVisual.condition(48));assertEquals("neutral",WeatherVisual.condition(null));assertEquals("day:neutral",WeatherVisual.key(s,wall.plusSeconds(10800).atZone(java.time.ZoneId.of("Asia/Seoul"))));protocolJson.decodeFromString<WeatherState>(File("../../../protocol/fixtures/weather-unavailable.json").readText()).validate()}
 @Test fun windowTouchAndRegistrationStayTogetherAndOfficeDoesNotOverlap(){for(id in listOf("home","office","subway-am","subway-pm"))for((w,h)in listOf(960f to 1200f,1200f to 960f,1200f to 1920f)){val m=SceneLayout.fit(manifest(id),w,h).manifest;assertTrue(m.weatherWindows.isNotEmpty());for(window in m.weatherWindows){val r=window.rect;assertEquals(RoomWidget.WEATHER,roomWidgetAt(m,r[0]+r[2]/2,r[1]+r[3]/2));assertTrue(r[0]>=0&&r[1]>=0&&r[0]+r[2]<=m.logicalSize[0]&&r[1]+r[3]<=m.logicalSize[1]);if(id=="office"){for(p in m.furniture.filter{it.id in listOf("clock","calendar","memo","bookcase")}){assertFalse(p.id,r[0]<p.rect[0]+p.rect[2]&&r[0]+r[2]>p.rect[0]&&r[1]<p.rect[1]+p.rect[3]&&r[1]+r[3]>p.rect[1])}}}}}
 @Test fun completeRugRemainsInsideAllViewportsAndReducedDecorationDoesNotTeleport(){for((w,h)in listOf(384f to 480f,560f to 448f,384f to 614f)){val r=HomeFloorGeometry.rugBounds(w,h);assertTrue(r[0]>=0&&r[1]>=160&&r[0]+r[2]<=w&&r[1]+r[3]<=h)};val m=manifest("home");val motion=SceneMotion(m);val point=motion.position;repeat(40){motion.tick("read",1f/30,true,ambientTarget="read")};assertEquals(point,motion.position);assertFalse(motion.moving)}
 private fun manifest(id:String)=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/$id.json").readText())
}
