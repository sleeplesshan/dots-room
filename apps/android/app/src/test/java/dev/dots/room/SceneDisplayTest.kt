package dev.dots.room

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlinx.serialization.encodeToString

class SceneDisplayTest {
 private val scenes=listOf("home","office","subway-am","subway-pm")
 private val sizes=listOf("landscape-left" to (960f to 1200f),"portrait-full" to (1200f to 1920f),"portrait-split" to (1200f to 960f),"narrow" to (320f to 800f))
 private fun load(id:String)=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/$id.json").readText())
 @Test fun expandedPlansFillEveryPanelWithoutStretchingCharactersOrProps(){
  val exports=File("../../../.cache/display-layout").apply{mkdirs()}
  for(id in scenes)for((label,size)in sizes){val base=load(id);val layout=SceneLayout.fit(base,size.first,size.second);val m=layout.manifest
   val v=RoomViewport(size.first,size.second,m.logicalSize[0].toFloat(),m.logicalSize[1].toFloat(),fill=true)
   assertTrue("$id $label horizontal gap ${v.left}",v.left in -1f..0f);assertTrue("$id $label vertical gap ${v.top}",v.top in -1f..0f)
   val p=Point(m.spawn[0],m.spawn[1]);val q=v.screen(p).let{v.point(it.x,it.y)};assertEquals(p.x,q.x,.001f);assertEquals(p.y,q.y,.001f)
   assertEquals(base.animations,m.animations)
   for((old,p)in base.furniture.zip(m.furniture)){assertEquals(old.rect.drop(2),p.rect.drop(2));assertTrue("$id $label ${p.id} left",p.rect[0]>=0);assertTrue("$id $label ${p.id} right",p.rect[0]+p.rect[2]<=m.logicalSize[0]);assertTrue("$id $label ${p.id} bottom",p.rect[1]+p.rect[3]<=m.logicalSize[1])}
   val stored=Placement(Point(base.spawn[0],base.spawn[1]));val restored=layout.canonical(layout.project(stored));assertEquals(stored.point.x,restored.point.x,.001f);assertEquals(stored.point.y,restored.point.y,.001f)
   for(p in m.furniture.filter{it.anchor!=null}){val projected=layout.project(Placement(Point(0f,0f),p.id));assertEquals(Point(p.anchor!![0],p.anchor[1]),projected.point)}
   exports.resolve("$id-$label.json").writeText(protocolJson.encodeToString(m))
  }
 }
 @Test fun adaptedRoutesAndFurnitureTransitionsRemainReachable(){
  for(id in scenes)for((label,size)in sizes){val m=SceneLayout.fit(load(id),size.first,size.second).manifest
   for(from in m.waypoints)for(to in m.waypoints){val start=Point(from.point[0],from.point[1]);val path=Navigation.route(start,to.id,m.waypoints,m.furniture,m.walkBounds,m.walkRadius)
    assertTrue("$id $label ${from.id}->${to.id} unreachable",path.isNotEmpty());for((a,b)in(listOf(start)+path).zipWithNext())assertTrue("$id $label collision",Navigation.safe(a,b,m.furniture,m.walkBounds,m.walkRadius))
   }
   val motion=SceneMotion(m)
   for(action in listOf("read","work","talk","sleep","talk")){repeat(1600){motion.tick(action,1f/30,false,true);if(motion.moving)assertFalse("$id $label $action walking collision",Navigation.collides(motion.position,m.furniture,bounds=m.walkBounds,radius=m.walkRadius));if(motion.transition){val own=if(motion.occupiesSeat)setOf("desk","chair")else setOf(motion.occupiedFurnitureId!!);assertFalse("$id $label unrelated object",Navigation.collides(motion.position,m.furniture,bounds=m.walkBounds,radius=m.walkRadius,exceptIds=own))}}
    assertFalse("$id $label $action fallback",motion.fallback);assertFalse("$id $label $action stranded",motion.moving);assertFalse(motion.transition)
   }
  }
 }
 @Test fun ambientUsesClosedSmoothLoopsAndStaticPlant(){
  for(id in scenes){val m=load(id);for(a in m.ambient){assertEquals(AmbientPlayback.at(a,0),AmbientPlayback.at(a,a.periodMs));assertEquals(AmbientPlayback(0,0,0f),AmbientPlayback.at(a,13453,true));for(t in 0L..a.periodMs step 37){val p=AmbientPlayback.at(a,t);assertTrue(p.first in a.frameRects.indices);assertTrue(p.next in a.frameRects.indices);assertTrue(p.blend in 0f..1f)}
    if(a.kind in listOf("plant","clouds")){assertEquals(1,a.frameRects.size);assertEquals(AmbientPlayback(0,0,0f),AmbientPlayback.at(a,927))}else assertEquals(16,a.frameRects.size)
    if(a.kind=="cat"){assertNotNull(a.motionRect);for(t in 0L..a.periodMs step 17){val p=AmbientPlayback.at(a,t);assertEquals(0,p.first);assertEquals(0,p.next);assertEquals(0f,p.blend,0f);assertTrue(CatBreathing.rise(a,t)in 0f..1f);assertEquals(0f,CatBreathing.rise(a,t,true),0f)};assertEquals(0f,CatBreathing.rise(a,0),0f);assertEquals(1f,CatBreathing.rise(a,a.periodMs/2),.001f);assertEquals(0f,CatBreathing.rise(a,a.periodMs),0f);assertTrue(kotlin.math.abs(CatBreathing.rise(a,1000)-CatBreathing.rise(a,1033))<.018f)}
    if(a.drift&&a.kind!="clouds"){assertEquals(SkyDrift.positions(a,0,false),SkyDrift.positions(a,a.periodMs,false));assertEquals(SkyDrift.positions(a,0,false),SkyDrift.positions(a,9273,true));val speed=(a.rect[2]+24)/a.periodMs;assertEquals(speed*1000,SkyDrift.shift(a,1000,false),.001f);assertEquals(speed*2000,SkyDrift.shift(a,2000,false),.001f)}
   }}
 }
 @Test fun subwayCloudContoursRemainWholeAndOfficeStorageBelongsToTheBackWall(){
  for(id in listOf("subway-am","subway-pm"))for((_,size)in sizes){val m=SceneLayout.fit(load(id),size.first,size.second).manifest
   for(a in m.ambient.filter{it.kind=="clouds"})for(t in 0L..a.periodMs step 137){val q=TrainSky.rect(a,t,false)
    assertTrue(q[0]>=a.rect[0]+5);assertTrue(q[0]+q[2]<=a.rect[0]+a.rect[2]-5)
    assertTrue(q[1]>a.rect[1]);assertTrue(q[1]+q[3]<a.rect[1]+a.rect[3]);assertEquals(a.frameRects[0][2].toFloat()/a.frameRects[0][3],q[2]/q[3],.001f)
    assertEquals(TrainSky.rect(a,0,false),TrainSky.rect(a,12345,true));assertEquals(TrainSky.rect(a,0,false),TrainSky.rect(a,a.periodMs,false))
   }
  }
  val m=load("office");val cabinet=m.furniture.first{it.id=="bookcase"}
  assertEquals(92f,cabinet.rect[2],0f);assertEquals(158f,cabinet.rect[1]+cabinet.rect[3],0f);assertTrue(cabinet.rect[0]>=260)
  assertTrue(m.coworkers.filter{it.slot<2}.all{it.position[1]-65>cabinet.rect[1]+cabinet.rect[3]})
 }
 @Test fun coworkerSeatsKeepSharedHipAndFurnitureAnchorsAcrossSizes(){
  for((_,size)in sizes){val m=SceneLayout.fit(load("office"),size.first,size.second).manifest
   for(n in m.coworkers){val chair=m.furniture.first{it.id=="colleague-chair-${n.slot}"};val desk=m.furniture.first{it.id=="colleague-desk-${n.slot}"};val monitor=m.furniture.first{it.id=="colleague-monitor-${n.slot}"}
    assertEquals(n.position[0],chair.rect[0]+chair.rect[2]/2,.001f);assertEquals(n.position[0],desk.rect[0]+desk.rect[2]/2,.001f);assertEquals(n.position[0],monitor.rect[0]+monitor.rect[2]/2,.001f)
    assertEquals("hips sit on the seat",n.position[1]-4,chair.rect[1]+24,.001f)
    assertTrue("head above low backrest",n.position[1]-64+17<chair.rect[1]);assertEquals(48f,chair.rect[3],.001f)
   }
  }
  for(slot in 0..3)for(arm in 0..1)for(t in 0L..8800 step 33){assertTrue(CoworkerPose.rise(slot,arm,t,false)in 0f..1f);assertEquals(0f,CoworkerPose.rise(slot,arm,t,true),0f);assertTrue(kotlin.math.abs(CoworkerPose.rise(slot,arm,t,false)-CoworkerPose.rise(slot,arm,t+33,false))<.024f)}
 }
}
