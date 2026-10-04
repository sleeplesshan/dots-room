package dev.dots.room

import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

@Serializable data class Animation(val assetId:String,val action:String,val direction:String,val atlas:String,val frameRects:List<List<Int>>,val frameDurationsMs:List<Long>,val loop:Boolean,val footAnchor:List<Int>,val interactionAnchor:List<Int>,val collisionBounds:List<Int>,val drawLayer:String,val allowedFallback:String?,val sourceReference:String,val generationProvenance:String,val gripAnchor:List<Int>?=null)
@Serializable data class TransitionPolicy(val policy:String,val durationMs:Long,val rules:List<String>)
@Serializable data class AmbientLayer(val atlas:String,val frameRects:List<List<Int>>,val rect:List<Float>,val periodMs:Long=2400,val kind:String="loop",val depth:Float?=null,val drift:Boolean=false,val mask:String?=null,val motionRect:List<Int>?=null)
@Serializable data class WeatherWindow(val id:String,val rect:List<Float>,val mask:String,val landscape:String?=null)
@Serializable data class Coworker(val slot:Int,val position:List<Float>,val size:Int=64)
@Serializable data class Manifest(val version:Int,val logicalSize:List<Int>,val cellSize:Int,val floor:String,val animations:List<Animation>,val furniture:List<Furniture>,val waypoints:List<Waypoint>,val transitions:TransitionPolicy,val visualQa:String,val sceneId:String="home",val walkBounds:List<Float> = listOf(18f,241f,348f,183f),val spawn:List<Float> = listOf(190f,330f),val speechAnchor:List<Float> = listOf(190f,350f),val ambient:List<AmbientLayer> = emptyList(),val coworkers:List<Coworker> = emptyList(),val walkRadius:List<Float> = listOf(7f,3f),val weatherWindows:List<WeatherWindow> = emptyList())
object SpriteAnimator {
 fun index(a:Animation,elapsed:Long):Int {val total=a.frameDurationsMs.sum();var t=if(a.loop)elapsed.coerceAtLeast(0)%total else elapsed.coerceIn(0,total-1);a.frameDurationsMs.forEachIndexed{i,d->if(t<d)return i;t-=d};return a.frameRects.lastIndex}
 fun choose(m:Manifest,action:String,direction:String):Animation?=if(action=="happy")m.animations.find{it.action=="celebrate"&&it.direction==direction}?.copy(action="happy",loop=true)else m.animations.find{it.action==action&&it.direction==direction}
}
class SceneMotion(private val manifest:Manifest) {
 var position by mutableStateOf(Point(manifest.spawn[0],manifest.spawn[1]));private set
 var direction by mutableStateOf("S");private set
 var moving by mutableStateOf(false);private set
 var transition by mutableStateOf(false);private set
 var fallback by mutableStateOf(false);private set
 private var destination="";private var path=mutableListOf<Point>();private var interaction by mutableStateOf<String?>(null);private var targetAction="idle"
 val placement:Placement get()=if(transitionPhase!=null)DropPlacement.resolve(position,manifest)else Placement(position,interaction)
 val occupiedFurnitureId:String? get()=interaction
 val occupiesSeat:Boolean get()=interaction=="chair"||interaction=="desk"&&manifest.furniture.find{it.id=="desk"}?.facing=="N"
 val seatedAtDesk:Boolean get()=occupiesSeat&&!moving&&!transition
 var transitionAction by mutableStateOf<String?>(null);private set
 var transitionElapsed=0L;private set
 var transitionPhase by mutableStateOf<String?>(null);private set
 var transitionProgress by mutableStateOf(0f);private set
 private var transitionFrom=position;private var transitionTo=position;private var transitionDuration=320L
 val occupiesBed:Boolean get()=interaction=="bed"
 val inBed:Boolean get()=interaction=="bed"&&!moving&&!transition
 fun place(p:Placement){position=p.point;val prop=manifest.furniture.find{it.id==p.furniture};direction=prop?.facing?:"S";interaction=p.furniture;destination=prop?.waypoint?:"";path.clear();moving=false;transition=false;transitionAction=null;transitionPhase=null;transitionProgress=0f;fallback=false}
 private fun furnitureTransition(prop:Furniture,to:Point,phase:String):Boolean {
  val own=if(prop.id=="desk"||prop.id=="chair")setOf("desk","chair")else setOf(prop.id)
  if(!Navigation.safe(position,to,manifest.furniture,manifest.walkBounds,manifest.walkRadius,own)){fallback=true;return false}
  interaction=prop.id;transitionFrom=position;transitionTo=to;transitionPhase=phase;transitionProgress=0f;transitionElapsed=0
  transitionDuration=maxOf(manifest.transitions.durationMs,(kotlin.math.hypot(to.x-position.x,to.y-position.y)/110f*1000).toLong()).coerceAtLeast(1)
  transitionAction=if(prop.id=="bed")if(phase=="enter")"bed-lie"else "bed-rise" else null
  moving=false;transition=true;direction=prop.facing?:"S";return true
 }
 private fun advanceTransition(seconds:Float){transitionElapsed+=(seconds.coerceIn(0f,.1f)*1000).toLong();transitionProgress=(transitionElapsed.toFloat()/transitionDuration).coerceIn(0f,1f);position=Point(transitionFrom.x+(transitionTo.x-transitionFrom.x)*transitionProgress,transitionFrom.y+(transitionTo.y-transitionFrom.y)*transitionProgress);if(transitionProgress>=1f){if(transitionPhase=="exit"){interaction=null;destination=""};transitionPhase=null;transition=false;transitionAction=null}}
 fun tick(action:String,seconds:Float,reduced:Boolean,busy:Boolean=false,ambientTarget:String?=null) {
  // A quiet pose stays at its current safe location, including reduced-motion decoration.
  if(ambientTarget=="stay"||reduced&&ambientTarget!=null){path.clear();moving=false;fallback=false;if(transitionPhase!=null)advanceTransition(seconds);else{transition=false;transitionAction=null};return}
  val wanted=ambientTarget?:when(action){"read"->"read";"work","think"->"desk";"sleep"->"sleep";else->"center"}
  val target=if(manifest.waypoints.any{it.id==wanted})wanted else "center"
  if(reduced){
   val node=manifest.waypoints.first{it.id==target}
   val prop=manifest.furniture.find{it.waypoint==target&&it.id in listOf("desk","bed","chair")}
   position=prop?.anchor?.let{Point(it[0],it[1])}?:Point(node.point[0],node.point[1])
   direction=prop?.facing?:"S";interaction=prop?.id;destination=target;path.clear();moving=false;transition=false;transitionPhase=null;transitionAction=null;transitionProgress=0f;fallback=false;targetAction=action;return
  }
  if(transitionPhase!=null){advanceTransition(seconds);return}
  if(target!=destination){
   // Always leave furniture via its own access waypoint before planning the new route.
   if(interaction!=null){val prop=manifest.furniture.first{it.id==interaction};val node=manifest.waypoints.first{it.id==prop.waypoint};if(furnitureTransition(prop,Point(node.point[0],node.point[1]),"exit"))advanceTransition(seconds);return}
   destination=target;targetAction=action;path=Navigation.route(position,target,manifest.waypoints,manifest.furniture,manifest.walkBounds,manifest.walkRadius).toMutableList();fallback=path.isEmpty()
  }
  if(path.isNotEmpty()){val next=Navigation.step(position,path.first(),seconds,if(busy||action=="talk")72f else 38f);if(!Navigation.collides(next,manifest.furniture,bounds=manifest.walkBounds,radius=manifest.walkRadius)){direction=Navigation.direction(next.x-position.x,next.y-position.y,direction);position=next;moving=true;transition=false;if(next==path.first())path.removeAt(0)}else{moving=false;fallback=true;path.clear()};return}
  moving=false
  if(fallback)return
  val prop=manifest.furniture.find{it.waypoint==target&&it.id in listOf("desk","bed","chair")}
  if(prop?.anchor!=null){val anchor=Point(prop.anchor[0],prop.anchor[1]);if(position!=anchor){if(furnitureTransition(prop,anchor,"enter"))advanceTransition(seconds)}else{interaction=prop.id;direction=prop.facing?:"S"}}else{transition=false;direction="S"}
 }
 fun displayAction(action:String)=if(moving)"walk"else if(transition)transitionAction?:if(occupiesSeat)if(targetAction in listOf("work","think"))targetAction else "rest" else "neutral" else if(action in listOf("sleep","idle","listening","look")&&seatedAtDesk)"rest" else action
}
