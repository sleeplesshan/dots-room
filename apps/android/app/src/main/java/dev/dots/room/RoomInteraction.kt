package dev.dots.room

import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.*

@Serializable data class Placement(val point:Point,val furniture:String?=null)
object DropPlacement {
 fun resolve(point:Point,m:Manifest):Placement {
  val bed=m.furniture.find{it.id=="bed"};val chair=m.furniture.find{it.id=="chair"}
  fun inside(p:Furniture):Boolean=point.x in p.rect[0]..p.rect[0]+p.rect[2]&&point.y in p.rect[1]..p.rect[1]+p.rect[3]
  val desk=m.furniture.find{it.id=="desk"}
  val prop=if(bed!=null&&inside(bed))bed else if(chair!=null&&inside(chair))(if(chair.anchor!=null&&chair.anchor!=desk?.anchor)chair else desk?:chair) else null
  if(prop?.anchor!=null)return Placement(Point(prop.anchor[0],prop.anchor[1]),prop.id)
  // Only project onto floor connected to the authored navigation graph.
  val candidates=mutableListOf<Point>();for(x in m.walkBounds[0].toInt()..(m.walkBounds[0]+m.walkBounds[2]).toInt() step 3)for(y in m.walkBounds[1].toInt()..(m.walkBounds[1]+m.walkBounds[3]).toInt() step 3){val p=Point(x.toFloat(),y.toFloat());if(!Navigation.collides(p,m.furniture,bounds=m.walkBounds,radius=m.walkRadius))candidates.add(p)}
  val result=candidates.sortedBy{hypot(it.x-point.x,it.y-point.y)}.firstOrNull{p->m.waypoints.any{Navigation.safe(p,Point(it.point[0],it.point[1]),m.furniture,m.walkBounds,m.walkRadius)}}?:Point(m.speechAnchor[0],m.speechAnchor[1])
  return Placement(result)
 }
 fun restore(p:Placement,m:Manifest):Placement=m.furniture.find{it.id==p.furniture&&it.anchor!=null}?.let{Placement(Point(it.anchor!![0],it.anchor[1]),it.id)}?:resolve(p.point,m)
}
class CircularDrag {
 private data class Sample(val p:Point,val time:Long)
 private val samples=ArrayDeque<Sample>()
 fun reset(){samples.clear()}
 fun add(p:Point,now:Long):Boolean {
  if(samples.lastOrNull()?.let{hypot(p.x-it.p.x,p.y-it.p.y)<3f}==true)return false
  samples.add(Sample(p,now));while(samples.firstOrNull()?.let{now-it.time>3000}==true)samples.removeFirst();while(samples.size>160)samples.removeFirst()
  if(samples.size<8)return false
  val points=samples.map{it.p};val cx=points.map{it.x}.average();val cy=points.map{it.y}.average()
  if(points.map{hypot(it.x-cx,it.y-cy)}.average()<18)return false
  val length=points.zipWithNext().sumOf{(a,b)->hypot((b.x-a.x).toDouble(),(b.y-a.y).toDouble())}
  var clockwise=0.0;var counter=0.0
  val angles=points.filter{hypot(it.x-cx,it.y-cy)>=12}.map{atan2(it.y-cy,it.x-cx)}
  angles.zipWithNext().forEach{(a,b)->val delta=atan2(sin(b-a),cos(b-a));if(delta>0)clockwise+=delta else counter-=delta}
  return length>=240&&max(clockwise,counter)>=4*PI-.02&&min(clockwise,counter)<=.25*max(clockwise,counter)
 }
}
/** Local interaction only. Network state and public task state remain authoritative. */
class RoomInteraction {
 var phase by mutableStateOf("normal");private set
 var point by mutableStateOf<Point?>(null);private set
 var since by mutableStateOf(0L);private set
 var until by mutableStateOf(0L);private set
 var placement by mutableStateOf<Placement?>(null);private set
 var dizzy by mutableStateOf(false);private set
 private val circles=CircularDrag();private var baseRequest:String?=null
 val freezes:Boolean get()=phase!="normal"
 val menu:Boolean get()=phase=="menu"
 fun tap(p:Point,now:Long){if(phase=="curious"&&now<until){phase="menu";since=now;until=Long.MAX_VALUE}else{phase="curious";point=p;since=now;until=now+5000}}
 fun open(p:Point,now:Long){phase="menu";point=p;since=now;until=Long.MAX_VALUE}
 fun close(){phase="normal";point=null;placement=null}
 fun lift(p:Point,now:Long){phase="held";point=p;since=now;until=Long.MAX_VALUE;placement=null;dizzy=false;circles.reset()}
 fun drag(p:Point,now:Long){if(phase!="held")return;point=p;if(circles.add(p,now))dizzy=true}
 fun release(p:Placement,now:Long,busy:Boolean,request:String?){placement=p;point=p.point;phase=if(dizzy)"dizzy"else "landing";since=now;until=now+if(dizzy)4000 else 2000;baseRequest=request}
 fun restore(p:Placement,now:Long){placement=p;point=p.point;phase="resting";since=now;until=now+20000}
 fun tick(now:Long,busy:Boolean,request:String?){
  if(phase=="resting"&&(busy||request!=baseRequest)){close();return}
  if(now<until)return
  when(phase){"curious"->close();"landing","dizzy"->if(busy){close()}else{phase="resting";since=now;until=now+20000};"resting"->close()}
 }
 fun action():String?=when(phase){"curious","menu"->"curious";"held"->if(dizzy)"dizzy"else "held";"dizzy"->"dizzy";"landing","resting"->when(placement?.furniture){"desk"->"work";"chair"->"rest";"bed"->"sleep";else->"idle"};else->null}
}
