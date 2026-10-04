package dev.dots.room

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*

class SceneLayerAuditTest {
 private val scenes=listOf("home","office","subway-am","subway-pm")
 private fun manifest(id:String)=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/$id.json").readText())
 @Test fun allPairsAndInterruptedFurnitureTransitionsKeepFeetOutOfUnrelatedObjects(){
  for(id in scenes){val m=manifest(id)
   for(from in m.waypoints)for(to in m.waypoints){val p=Point(from.point[0],from.point[1]);val route=Navigation.route(p,to.id,m.waypoints,m.furniture,m.walkBounds,m.walkRadius);assertTrue("$id ${from.id} -> ${to.id}",route.isNotEmpty());for((a,b)in (listOf(p)+route).zipWithNext())assertTrue(Navigation.safe(a,b,m.furniture,m.walkBounds,m.walkRadius))}
   val motion=SceneMotion(m);var phases=0
   for((action,target)in listOf("read" to null,"idle" to "window","work" to null,"talk" to null,"sleep" to null,"work" to null,"talk" to null)){
    repeat(1500){motion.tick(action,1f/30,false,true,target);if(motion.moving)assertFalse("$id walking collision",Navigation.collides(motion.position,m.furniture,bounds=m.walkBounds,radius=m.walkRadius));if(motion.transition){phases++;val own=if(motion.occupiesSeat)setOf("desk","chair")else setOf(motion.occupiedFurnitureId!!);assertFalse("$id transit into unrelated furniture",Navigation.collides(motion.position,m.furniture,bounds=m.walkBounds,radius=m.walkRadius,exceptIds=own))}}
    assertFalse("$id $action stranded",motion.fallback);assertFalse(motion.moving);assertFalse(motion.transition)
   };assertTrue(phases>0)
   val bed=m.furniture.find{it.id=="bed"};if(bed!=null){motion.place(Placement(Point(bed.anchor!![0],bed.anchor[1]),"bed"));motion.tick("talk",1f/30,false);assertEquals("exit",motion.transitionPhase);motion.tick("sleep",1f/30,false);repeat(300){motion.tick("sleep",1f/30,false)};assertTrue(motion.inBed);motion.tick("talk",1f/30,true);assertNull(motion.transitionPhase);assertFalse(motion.occupiesBed)}
  }
 }
 @Test fun sameLayerListProtectsFrontFacesBlanketsCoworkersAndHeldCharacter(){
  for(id in scenes){val m=manifest(id);val foot=Point(213f,350f)
   fun order(facing:String="S",held:Boolean=false,seat:Boolean=true,bed:Boolean=false,phase:String?=null,progress:Float=1f)=SceneLayers.ordered(m,foot,held,seat,bed,facing,phase,progress)
   fun chairAfter(order:List<SceneLayer>)=SceneLayers.occluders(order).any{it.kind==LayerKind.FURNITURE&&m.furniture[it.index].id=="chair"}
   assertFalse("front face must stay visible",chairAfter(order()));assertTrue(chairAfter(order("N")));assertFalse(chairAfter(order("N",phase="enter",progress=.1f)));assertTrue(chairAfter(order("N",phase="enter",progress=.5f)));assertFalse(chairAfter(order("N",phase="exit",progress=.9f)))
   assertTrue(SceneLayers.occluders(order(held=true,seat=false)).isEmpty())
   if(id=="home"){val sleep=order(seat=false,bed=true);assertEquals(listOf("front"),SceneLayers.occluders(sleep).filter{it.kind==LayerKind.FURNITURE&&m.furniture[it.index].id=="bed"}.map{it.part});assertFalse(SceneLayers.occluders(order(seat=false,bed=true,phase="exit",progress=.6f)).any{it.kind==LayerKind.FURNITURE&&m.furniture[it.index].id=="bed"})}
   if(id=="office"){
    assertTrue(SceneLayers.occluders(SceneLayers.ordered(m,Point(52f,330f),false,false,false,"S",null,0f)).any{it.kind==LayerKind.COWORKER})
    val layers=order(seat=false)
    for((i,n)in m.coworkers.withIndex()){
     val desk=m.furniture.indexOfFirst{it.id=="colleague-desk-${n.slot}"}
     assertTrue("coworker must not be buried by own desktop",layers.indexOfFirst{it.kind==LayerKind.FURNITURE&&it.index==desk}<layers.indexOfFirst{it.kind==LayerKind.COWORKER&&it.index==i})
    }
   }
  }
 }
 private data class Mask(val w:Int,val h:Int,val alpha:ByteArray)
 private val masks=mutableMapOf<String,Mask>()
 private fun mask(file:String)=masks.getOrPut(file){val b=ByteBuffer.wrap(File("../../../.cache/layer-alpha/$file.alpha").readBytes());val w=b.int;val h=b.int;val alpha=ByteArray(w*h);b.get(alpha);Mask(w,h,alpha)}
 private fun opaque(file:String,rect:List<Float>,src:List<Int>?,x:Float,y:Float):Boolean{if(x<rect[0]||x>=rect[0]+rect[2]||y<rect[1]||y>=rect[1]+rect[3])return false;val b=mask(file);val r=src?:listOf(0,0,b.w,b.h);val px=r[0]+((x-rect[0])/rect[2]*r[2]).toInt();val py=r[1]+((y-rect[1])/rect[3]*r[3]).toInt();return (b.alpha[py*b.w+px].toInt() and 255)>=128}
 @Test fun floorBoundsKeepEveryWalkingAndStandingFrameInsideCanvas(){
  for(id in scenes){val m=manifest(id);val bounds=m.walkBounds
   for(a in m.animations.filter{it.action in listOf("walk","neutral","idle","talk","curious","held","dizzy")})for(r in a.frameRects){val b=mask(a.atlas)
    for(y in 0 until r[3])for(x in 0 until r[2])if((b.alpha[(r[1]+y)*b.w+r[0]+x].toInt() and 255)>=128){
     assertTrue("$id ${a.assetId} left cut",bounds[0]-a.footAnchor[0]+x>=0)
     assertTrue("$id ${a.assetId} right cut",bounds[0]+bounds[2]-a.footAnchor[0]+x<m.logicalSize[0])
     assertTrue("$id ${a.assetId} top cut",bounds[1]-a.footAnchor[1]+y>=0)
     assertTrue("$id ${a.assetId} bottom cut",bounds[1]+bounds[3]-a.footAnchor[1]+y<m.logicalSize[1])
    }
   }
  }
 }
 @Test fun actualPngWalkingBodyIsNotBuriedOnAnyAuthoredRoute()=audit(scenes.map{it to manifest(it)},"layer-audit-metrics.json")
 @Test fun expandedLayoutsKeepActualWalkingPixelsVisible()=audit(scenes.flatMap{id->listOf("landscape-left" to (960f to 1200f),"portrait-full" to (1200f to 1920f),"portrait-split" to (1200f to 960f),"narrow" to (320f to 800f)).map{(label,size)->"$id-$label" to SceneLayout.fit(manifest(id),size.first,size.second).manifest}},"display-layout-metrics.json")
 private fun audit(targets:List<Pair<String,Manifest>>,output:String){
  val results=mutableListOf<String>();var total=0
  for((id,m) in targets){var worst=0.0;var location="";var samples=0
   for(from in m.waypoints)for(to in m.waypoints){val start=Point(from.point[0],from.point[1]);val route=listOf(start)+Navigation.route(start,to.id,m.waypoints,m.furniture,m.walkBounds,m.walkRadius)
    for((a,b)in route.zipWithNext()){val count=ceil(hypot(b.x-a.x,b.y-a.y)/8).toInt().coerceAtLeast(1);val direction=Navigation.direction(b.x-a.x,b.y-a.y,"S");val anim=SpriteAnimator.choose(m,"walk",direction)!!
     for(n in 0..count){val p=Point(a.x+(b.x-a.x)*n/count,a.y+(b.y-a.y)*n/count);val r=anim.frameRects[n%anim.frameRects.size];val atlas=mask(anim.atlas);val ox=p.x-anim.footAnchor[0];val oy=p.y-anim.footAnchor[1];val fore=SceneLayers.occluders(SceneLayers.ordered(m,p,false,false,false,direction,null,0f));var body=0;var covered=0
      for(y in 0 until r[3])for(x in 0 until r[2]){if((atlas.alpha[(r[1]+y)*atlas.w+r[0]+x].toInt() and 255)<128)continue;body++;val wx=ox+x+.5f;val wy=oy+y+.5f
       if(fore.any{l->when(l.kind){LayerKind.FURNITURE->{val f=m.furniture[l.index];f.render&&((l.part!="front"&&opaque(f.backAsset?:f.id+"-back.png",f.rect,null,wx,wy))||(l.part!="back"&&opaque(f.frontAsset?:f.id+"-front.png",f.rect,null,wx,wy)))};LayerKind.AMBIENT->{val f=m.ambient[l.index];opaque(f.atlas,f.rect,f.frameRects[0],wx,wy)};LayerKind.COWORKER->{val f=m.coworkers[l.index];opaque(CoworkerPose.atlas(DaySchedule.colleagues[f.slot]),listOf(f.position[0]-f.size/2,f.position[1]-f.size,f.size.toFloat(),f.size.toFloat()),CoworkerPose.frame,wx,wy)};else->false}})covered++
      }
      val fraction=covered.toDouble()/body.coerceAtLeast(1);if(fraction>worst){worst=fraction;location="${from.id}->${to.id}(${p.x},${p.y})"};samples++
     }
    }
   }
   results.add("""{"scene":"$id","sampledWalkingFrames":$samples,"maxBodyOcclusionFraction":$worst,"worstLocation":"$location"}""");total+=samples
   assertTrue("$id torso coverage $worst at $location",worst<=.001)
  }
  File("../../../.cache/reports/$output").also{it.parentFile.mkdirs()}.writeText("""{"walkingFrames":$total,"scope":"all authored waypoint pairs, every 8px, rotating walk frames; all opaque character pixels including feet","scenes":[${results.joinToString(",")}]}""")
 }
}
