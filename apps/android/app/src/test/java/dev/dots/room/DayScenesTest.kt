package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
import java.time.ZonedDateTime
import java.nio.file.Files
import java.nio.file.Paths

class DayScenesTest {
 private fun m(scene:String):Manifest=protocolJson.decodeFromString(String(Files.readAllBytes(Paths.get("../../../.cache/android-assets/bami/day/$scene.json"))))
 @Test fun scheduleUsesLocalWeekdayBoundariesAndAllFiveColleaguesRotate(){
  for((time,scene) in listOf("06:59" to "home","07:00" to "subway-am","07:59" to "subway-am","08:00" to "office","16:59" to "office","17:00" to "subway-pm","17:59" to "subway-pm","18:00" to "home"))assertEquals(scene,DaySchedule.scene(ZonedDateTime.parse("2026-10-02T$time:00+09:00[Asia/Seoul]")))
  for(day in listOf("2026-10-03","2026-10-04"))assertEquals("home",DaySchedule.scene(ZonedDateTime.parse("${day}T08:00:00+09:00[Asia/Seoul]")))
  assertEquals("office",DaySchedule.scene(ZonedDateTime.parse("2026-10-05T08:00:00+09:00[Asia/Seoul]")))
  val today=ZonedDateTime.parse("2026-10-02T08:00:00+09:00[Asia/Seoul]");val absent=(0L..4L).map{val roster=DaySchedule.roster(today.plusDays(it));assertEquals(4,roster.size);DaySchedule.colleagues.single{it !in roster}}
  assertEquals(5,absent.toSet().size);assertEquals(DaySchedule.roster(today),DaySchedule.roster(today.plusDays(5)))
  assertEquals("home",DaySchedule.scene(today.withZoneSameInstant(java.time.ZoneId.of("America/New_York"))))
 }
 @Test fun uniformTransformRoundTripsWithoutStretch(){for((w,h) in listOf(960f to 1200f,600f to 400f,320f to 512f)){val v=RoomViewport(w,h);val p=Point(213f,307f);val s=v.screen(p);val restored=v.point(s.x,s.y);assertEquals(p.x,restored.x,.001f);assertEquals(p.y,restored.y,.001f);assertEquals(v.fit,v.scale,0f);assertTrue(v.left>=0);assertTrue(v.top>=0)}}
 @Test fun everyAuthoredRouteAndFurnitureEntryIsReachableWithoutWalkingInsideProps(){
  for(scene in listOf("home","office","subway-am","subway-pm")){
   val m=m(scene);val from=Point(m.spawn[0],m.spawn[1]);assertFalse(scene,Navigation.collides(from,m.furniture,bounds=m.walkBounds))
   for(node in m.waypoints){val path=Navigation.route(from,node.id,m.waypoints,m.furniture,m.walkBounds);assertTrue("$scene ${node.id} unreachable",path.isNotEmpty());for(i in 0 until path.lastIndex)assertTrue(Navigation.safe(path[i],path[i+1],m.furniture,m.walkBounds))}
   val motion=SceneMotion(m)
   for(action in listOf("read","work","talk","sleep","talk")){repeat(1500){motion.tick(action,1f/30,false,true)};assertFalse("$scene $action fallback",motion.fallback);assertFalse("$scene $action moving",motion.moving);assertFalse(motion.transition);if(action=="sleep"){if(scene=="home")assertTrue(motion.inBed)else{assertTrue(motion.seatedAtDesk);assertEquals("rest",motion.displayAction(action))}}}
   val sleep=DropPlacement.resolve(Point(350f,440f),m);if(sleep.furniture==null)assertFalse(Navigation.collides(sleep.point,m.furniture,bounds=m.walkBounds,radius=m.walkRadius))else assertEquals("bed",sleep.furniture)
   val desk=m.furniture.first{it.id=="desk"};assertTrue(desk.screen!![2]>=112);assertTrue(desk.screen[3]>=if(scene.startsWith("subway"))28 else 40)
   if(scene.startsWith("subway")){val chair=m.furniture.first{it.id=="chair"};val placement=DropPlacement.resolve(Point(chair.rect[0]+chair.rect[2]/2,chair.rect[1]+chair.rect[3]/2),m);assertEquals("chair",placement.furniture);val i=RoomInteraction();i.restore(placement,0);assertEquals("rest",i.action());motion.place(placement);assertEquals("S",motion.direction)}
  }
 }
}
