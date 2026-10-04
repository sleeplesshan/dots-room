package dev.dots.room

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlin.math.*

@Composable fun RoomRenderer(s:ViewState,modifier:Modifier=Modifier,onSpeechVisible:()->Unit={},onPreset:(String)->Unit={},initialPlacement:Placement?=null,onPlacement:(Placement)->Unit={},onWidget:(RoomWidget)->Unit={},manifestOverride:Manifest?=null,roster:List<String> = emptyList(),onHeld:(Boolean)->Unit={},onSpark:(String)->Boolean={true},motionOverride:SceneMotion?=null,adaptToViewport:Boolean=true,nowOverride:ZonedDateTime?=null,officeElapsedOverride:Long?=null,ambientOffsetMs:Long=0,clockOverride:(()->Long)?=null) {
 val context=LocalContext.current
 val base=remember(manifestOverride){manifestOverride?:context.assets.open("bami/manifest.json").bufferedReader().use{protocolJson.decodeFromString<Manifest>(it.readText())}}
 val livePlacement=remember(base.sceneId){arrayOf(initialPlacement)}
 BoxWithConstraints(modifier){
  val layout=remember(base,maxWidth,maxHeight,adaptToViewport,motionOverride){if(adaptToViewport&&motionOverride==null)SceneLayout.fit(base,maxWidth.value,maxHeight.value)else SceneLayout(base)}
  key(base.sceneId,layout.manifest.logicalSize){RoomCanvas(s,Modifier.fillMaxSize(),onSpeechVisible,onPreset,livePlacement[0]?.let(layout::project),{val saved=layout.canonical(it);livePlacement[0]=saved;onPlacement(saved)},onWidget,layout.manifest,roster,onHeld,onSpark,motionOverride,nowOverride,adaptToViewport&&motionOverride==null,officeElapsedOverride,ambientOffsetMs,{livePlacement[0]=layout.canonical(it)},clockOverride)}
 }
}
@Composable private fun RoomCanvas(s:ViewState,modifier:Modifier,onSpeechVisible:()->Unit,onPreset:(String)->Unit,initialPlacement:Placement?,onPlacement:(Placement)->Unit,onWidget:(RoomWidget)->Unit,manifestOverride:Manifest,roster:List<String>,onHeld:(Boolean)->Unit,onSpark:(String)->Boolean,motionOverride:SceneMotion?,nowOverride:ZonedDateTime?,fill:Boolean,officeElapsedOverride:Long?,ambientOffsetMs:Long,onLivePlacement:(Placement)->Unit,clockOverride:(()->Long)?) {
 val context=LocalContext.current
 val lifecycle=(context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
 var active by remember{mutableStateOf(lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)?:true)}
 DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,_->active=lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)?:true};lifecycle?.addObserver(observer);onDispose{lifecycle?.removeObserver(observer)}}
 val manifest=manifestOverride
 val files=remember(manifest,roster){(listOf(manifest.floor)+if(manifest.sceneId=="home")listOf("life/home-bare-floor-v1.png","life/home-rug-v1.png","life/home-left-light-v1.png")else emptyList()) .let{floorFiles->(floorFiles+manifest.animations.map{it.atlas}+manifest.furniture.filter{it.render}.flatMap{listOf(it.backAsset?:it.id+"-back.png",it.frontAsset?:it.id+"-front.png")}+manifest.ambient.flatMap{listOf(it.atlas)+listOfNotNull(it.mask)+if(it.drift&&it.kind=="window")listOf("day/window-base.png","day/clouds-calm-v2.png")else emptyList()}+manifest.weatherWindows.flatMap{listOf(it.mask)+listOfNotNull(it.landscape)}+listOf("life/weather-day-v1.png","life/weather-night-v1.png","day/train-cloud-v3.png")+roster.flatMap{listOf(CoworkerPose.atlas(it),CoworkerPose.socialAtlas(it))}).distinct()}}
 val images=remember(files){files.associateWith{context.assets.open("bami/$it").use{stream->BitmapFactory.decodeStream(stream)}}}
 DisposableEffect(images){onDispose{images.values.forEach{it.recycle()}}}
 val motion=remember(manifest,motionOverride){motionOverride?:SceneMotion(manifest)};val interaction=remember(manifest){RoomInteraction()};var roomSize by remember{mutableStateOf(IntSize.Zero)};var menuSize by remember{mutableStateOf(IntSize.Zero)}
 var frame by remember{mutableLongStateOf(0)};val shimmer=remember(manifest){ReplyShimmer()};val weatherBlend=remember(manifest){WeatherBlend()}
 val officeLife=remember(manifest){OfficeLife()};val officeBorn=remember(manifest){SystemClock.elapsedRealtime()};var friendTouched by remember{mutableLongStateOf(-10000)}
 fun greet(slot:Int){val now=SystemClock.elapsedRealtime();if(officeLife.tap(slot,now)){friendTouched=now;frame=now}}
 val latest by rememberUpdatedState(s);val send by rememberUpdatedState(onPreset);val savePlacement by rememberUpdatedState(onPlacement);val openWidget by rememberUpdatedState(onWidget);val heldChanged by rememberUpdatedState(onHeld);val claimSpark by rememberUpdatedState(onSpark);val speechVisible by rememberUpdatedState(onSpeechVisible)
 fun busy(current:ViewState)=current.behavior.running>0||current.behavior.ambientTarget==null&&current.behavior.action in listOf("read","think","work","talk")
 fun viewport(w:Float,h:Float)=RoomViewport(w,h,manifest.logicalSize[0].toFloat(),manifest.logicalSize[1].toFloat(),fill)
 LaunchedEffect(manifest){initialPlacement?.let{val p=DropPlacement.restore(it,manifest);motion.place(p);interaction.restore(p,SystemClock.elapsedRealtime())}}
 fun settle(){val p=DropPlacement.resolve(interaction.point?:motion.position,manifest);motion.place(p);interaction.release(p,SystemClock.elapsedRealtime(),busy(latest),latest.behavior.requestMessageId);savePlacement(p);heldChanged(false)}
 LaunchedEffect(active){if(!active&&interaction.phase=="held")settle()}
 DisposableEffect(Unit){onDispose{savePlacement(if(interaction.phase=="held")DropPlacement.resolve(interaction.point?:motion.position,manifest)else motion.placement);if(interaction.phase=="held")heldChanged(false)}}
 val renderClock by rememberUpdatedState(clockOverride?:{SystemClock.elapsedRealtime()})
 LaunchedEffect(active,manifest){if(!active)return@LaunchedEffect;var before=renderClock();var previous=0L;while(true){val tick=withFrameNanos{it};if(tick-previous<32_000_000L)continue;previous=tick;val now=renderClock();val current=latest
  interaction.tick(now,busy(current),current.behavior.requestMessageId)
  if(!current.paused&&!interaction.freezes)motion.tick(current.behavior.action,(now-before)/1000f,current.reduced,current.behavior.requestMessageId!=null,current.behavior.ambientTarget)
  val arrived=current.reduced||!motion.moving&&!motion.transition&&hypot(motion.position.x-manifest.speechAnchor[0],motion.position.y-manifest.speechAnchor[1])<1f
  if(!current.paused&&!interaction.freezes&&current.behavior.action=="talk"&&!current.behavior.speechReady&&arrived){
   val id=current.behavior.reactionMessageId
   val waiting=id!=null&&current.data.messages.any{it.messageId==id&&it.role=="assistant"}&&shimmer.waiting(id,now,current.reduced,claimSpark)
   if(!waiting)speechVisible()
  }
  onLivePlacement(if(interaction.phase=="held")DropPlacement.resolve(interaction.point?:motion.position,manifest)else motion.placement);before=now;frame=now
 }}
 val held=interaction.phase=="held";val actorPoint=interaction.point?:motion.position
 val seated=!held&&motion.occupiesSeat;val inBed=!held&&motion.occupiesBed
 val officePoses=manifest.coworkers.associate{it.slot to officeLife.at(it.slot,officeElapsedOverride?.let{t->officeBorn+t}?:frame,officeBorn,s.reduced||s.paused)}
 val officeGreeting=!held&&!seated&&!inBed&&!busy(s)&&s.behavior.action in listOf("idle","listening","unknown")&&(frame-friendTouched in 0..1800||manifest.coworkers.any{n->val p=officePoses.getValue(n.slot);p.action=="chat"&&p.elapsed in 220..1800&&hypot(actorPoint.x-n.position[0],actorPoint.y-n.position[1])<200})
 val baseAction=interaction.action()?:if(officeGreeting)"happy"else motion.displayAction(s.behavior.action)
 val facing=if(interaction.freezes&&baseAction !in listOf("work","rest"))"S"else motion.direction
 val sparkling=shimmer.visible(s.behavior,frame,s.paused,interaction.freezes)
 val action=if(sparkling&&!s.reduced)"spark"else if(baseAction in listOf("curious","dizzy","held"))baseAction else if(motion.transitionAction!=null)motion.transitionAction!! else if(inBed&&interaction.phase=="landing"&&frame-interaction.since<320)"bed-lie"else if(inBed)"bed-sleep"else if(baseAction in listOf("idle","listening","unknown"))if(!s.reduced&&!s.paused&&LifeDirector.blinkElapsed(frame,s.behavior.since)!=null)"blink"else "breath" else if(baseAction=="look"&&frame-s.behavior.since>=1920)"breath"else baseAction
 val anim=SpriteAnimator.choose(manifest,action,facing)?:SpriteAnimator.choose(manifest,action,"S")?:SpriteAnimator.choose(manifest,"neutral",facing)
 val elapsed=if(s.reduced)0 else if(motion.transitionAction!=null&&anim!=null)(motion.transitionProgress*anim.frameDurationsMs.sum()).toLong() else if(s.paused)0 else if(sparkling)frame-shimmer.since else if(action=="blink")LifeDirector.blinkElapsed(frame,s.behavior.since)?:0L else frame-if(interaction.freezes)interaction.since else s.behavior.since
 val frameRect=anim?.frameRects?.get(SpriteAnimator.index(anim,elapsed))
 val blend=if(motion.transitionPhase=="enter")motion.transitionProgress else if(motion.transitionPhase=="exit")1f-motion.transitionProgress else 1f
 val lift=if(motion.transitionPhase!=null)(sin(motion.transitionProgress*PI)*if(inBed)12 else 26).toFloat()else 0f
 val seatedOffset=if(inBed)listOf(0f,-8f-lift)else if(seated&&facing=="N")manifest.furniture.find{it.id=="desk"}?.seatedOffset?.let{listOf(it[0]*blend,it[1]*blend-lift)}else if(seated&&lift>0)listOf(0f,-lift)else null
 fun spriteOrigin():Point=Point(actorPoint.x+(seatedOffset?.get(0)?:0f)-(anim?.footAnchor?.get(0)?:48),actorPoint.y+(seatedOffset?.get(1)?:0f)-(anim?.footAnchor?.get(1)?:90))
 val layerOrder=SceneLayers.ordered(manifest,actorPoint,held,seated,inBed,facing,motion.transitionPhase,motion.transitionProgress,officePoses.filterValues{it.front}.keys)
 fun opaque(file:String,rect:List<Float>,source:List<Int>?,point:Point):Boolean {
  if(point.x !in rect[0]..<rect[0]+rect[2]||point.y !in rect[1]..<rect[1]+rect[3])return false
  val b=images.getValue(file);val r=source?:listOf(0,0,b.width,b.height);val x=r[0]+((point.x-rect[0])/rect[2]*r[2]).toInt().coerceIn(0,r[2]-1);val y=r[1]+((point.y-rect[1])/rect[3]*r[3]).toInt().coerceIn(0,r[3]-1)
  return android.graphics.Color.alpha(b.getPixel(x,y))>=128
 }
 fun playback(a:AmbientLayer)=AmbientPlayback.at(a,frame,s.reduced||s.paused)
 fun ambientFrame(a:AmbientLayer)=a.frameRects[playback(a).first]
 fun layerCovers(layer:SceneLayer,point:Point):Boolean=when(layer.kind){
 LayerKind.FURNITURE->{val p=manifest.furniture[layer.index]
  p.render&&((layer.part!="front"&&opaque(p.backAsset?:p.id+"-back.png",p.rect,null,point))||(layer.part!="back"&&opaque(p.frontAsset?:p.id+"-front.png",p.rect,null,point)))
 }
 LayerKind.AMBIENT->{val a=manifest.ambient[layer.index];val phase=playback(a)
  fun alpha(r:List<Int>):Int {val d=a.rect;if(point.x !in d[0]..<d[0]+d[2]||point.y !in d[1]..<d[1]+d[3])return 0;val b=images.getValue(a.atlas);return android.graphics.Color.alpha(b.getPixel(r[0]+((point.x-d[0])/d[2]*r[2]).toInt(),r[1]+((point.y-d[1])/d[3]*r[3]).toInt()))}
  alpha(a.frameRects[phase.first])*(1-phase.blend)+alpha(a.frameRects[phase.next])*phase.blend>=128
 }
 LayerKind.COWORKER->{val n=manifest.coworkers[layer.index];val p=officePoses.getValue(n.slot);roster.getOrNull(n.slot)?.let{opaque(CoworkerPose.atlas(it,p),CoworkerPose.rect(n,p),CoworkerPose.source(p,s.reduced||s.paused),point)}?:false}
 LayerKind.ACTOR->false
 }
 val hitActor by rememberUpdatedState { tap:Offset,w:Float,h:Float ->
  if(anim==null||frameRect==null)false else{
   val p=viewport(w,h).point(tap.x,tap.y);val o=spriteOrigin();val x=(p.x-o.x).toInt();val y=(p.y-o.y).toInt();val r=frameRect;val b=images.getValue(anim.atlas)
   val padding=if(interaction.phase=="curious")20 else 6
   val opaque=x in 0 until r[2]&&y in 0 until r[3]&&(max(0,y-padding)..min(r[3]-1,y+padding)).any{py->(max(0,x-padding)..min(r[2]-1,x+padding)).any{px->android.graphics.Color.alpha(b.getPixel(r[0]+px,r[1]+py))>=128}}
   opaque&&SceneLayers.occluders(layerOrder).none{layerCovers(it,p)}
  }
 }
 val dragCoordinates by rememberUpdatedState {tap:Offset,w:Float,h:Float->val p=viewport(w,h).point(tap.x,tap.y);Point(p.x.coerceIn(42f,manifest.logicalSize[0]-42f),(p.y+66).coerceIn(82f,manifest.logicalSize[1]-8f))}
 val hitCoworker by rememberUpdatedState {tap:Offset,w:Float,h:Float->
  val p=viewport(w,h).point(tap.x,tap.y)
  layerOrder.asReversed().firstOrNull{l->l.kind==LayerKind.COWORKER&&layerCovers(l,p)&&layerOrder.drop(layerOrder.indexOf(l)+1).none{it.kind!=LayerKind.ACTOR&&layerCovers(it,p)}}?.let{manifest.coworkers[it.index].slot}
 }
 val accessibilityActions=manifest.furniture.filter{it.id in listOf("desk","bed","chair")&&it.anchor!=null}.map{prop->CustomAccessibilityAction(if(prop.id=="bed")"바미를 침대에 눕히기"else "바미를 의자에 앉히기"){val p=Placement(Point(prop.anchor!![0],prop.anchor[1]),prop.id);motion.place(p);interaction.release(p,SystemClock.elapsedRealtime(),busy(latest),latest.behavior.requestMessageId);savePlacement(p);true}}+listOf(CustomAccessibilityAction("바미에게 말하기"){interaction.open(motion.position,SystemClock.elapsedRealtime());frame=SystemClock.elapsedRealtime();true},CustomAccessibilityAction("바미를 방 가운데에 놓기"){val p=DropPlacement.resolve(Point(manifest.speechAnchor[0],manifest.speechAnchor[1]),manifest);motion.place(p);interaction.release(p,SystemClock.elapsedRealtime(),busy(latest),latest.behavior.requestMessageId);savePlacement(p);true})+RoomWidget.entries.filter{w->roomWidgetFurniture(manifest,w)!=null}.map{w->CustomAccessibilityAction(w.label+" 확대 보기"){openWidget(w);true}}+(if(manifest.weatherWindows.isNotEmpty()&&roomWidgetFurniture(manifest,RoomWidget.WEATHER)==null)listOf(CustomAccessibilityAction("서울 날씨 보기"){openWidget(RoomWidget.WEATHER);true})else emptyList())+manifest.coworkers.mapNotNull{n->roster.getOrNull(n.slot)?.let{id->CustomAccessibilityAction("${DaySchedule.colleagueName(id)}에게 인사하기"){greet(n.slot);true}}}
 BackHandler(interaction.menu){interaction.close();frame=SystemClock.elapsedRealtime()}
 val paint=remember{Paint().apply{isAntiAlias=false;isFilterBitmap=false}};val text=remember{Paint().apply{isAntiAlias=true;textAlign=Paint.Align.CENTER;typeface=androidx.core.content.res.ResourcesCompat.getFont(context,R.font.paperlogy_regular)}}
 Box(modifier.background(Color(0xFF241D1A)).onSizeChanged{if(roomSize!=it&&held)settle();roomSize=it}){
 Canvas(Modifier.fillMaxSize().semantics{contentDescription="바미의 ${DaySchedule.label(manifest.sceneId)} 장면. ${poseLabel(if(sparkling)"spark"else baseAction)}. 주간 한도 ${remainingPercentText(weeklyQuota(latest.data.quotas))}"+manifest.coworkers.joinToString(""){n->roster.getOrNull(n.slot)?.let{". ${DaySchedule.colleagueName(it)}: ${officePoses.getValue(n.slot).label}"}?:""};customActions=accessibilityActions}.pointerInput(manifest){awaitEachGesture{
  val down=awaitFirstDown();val actor=hitActor(down.position,size.width.toFloat(),size.height.toFloat())
  if(interaction.menu){waitForUpOrCancellation();interaction.close();frame=SystemClock.elapsedRealtime();return@awaitEachGesture}
  if(actor){var released:androidx.compose.ui.input.pointer.PointerInputChange?=null;var cancelled=false
   val beforeHold=withTimeoutOrNull(500L){var done=false;while(!done){val event=awaitPointerEvent();val c=event.changes.firstOrNull{it.id==down.id};if(c==null||c.isConsumed||event.changes.count{it.pressed}>1||(c.position-down.position).getDistance()>viewConfiguration.touchSlop){cancelled=true;done=true}else if(!c.pressed){released=c;done=true}};true}
   if(beforeHold==null&&!cancelled){interaction.lift(dragCoordinates(down.position,size.width.toFloat(),size.height.toFloat()),SystemClock.elapsedRealtime());heldChanged(true);frame=SystemClock.elapsedRealtime();try{drag(down.id){c->interaction.drag(dragCoordinates(c.position,size.width.toFloat(),size.height.toFloat()),SystemClock.elapsedRealtime());c.consume();frame=SystemClock.elapsedRealtime()}}finally{settle();frame=SystemClock.elapsedRealtime()}}
   else if(released!=null&&!cancelled){interaction.tap(motion.position,SystemClock.elapsedRealtime());frame=SystemClock.elapsedRealtime()}
  }else{val friend=hitCoworker(down.position,size.width.toFloat(),size.height.toFloat());val up=waitForUpOrCancellation();if(up!=null){if(friend!=null&&hitCoworker(up.position,size.width.toFloat(),size.height.toFloat())==friend)greet(friend)else{val p=viewport(size.width.toFloat(),size.height.toFloat()).point(up.position.x,up.position.y);roomWidgetAt(manifest,p.x,p.y)?.let{interaction.close();openWidget(it)}}}}
 }}){
  frame
  drawIntoCanvas{canvas->val c=canvas.nativeCanvas;val v=viewport(size.width,size.height);val save=c.save();c.translate(v.left,v.top);c.scale(v.scale,v.scale)
   paint.alpha=255;val floor=images.getValue(manifest.floor);if(manifest.sceneId=="home")HomeBackdrop.draw(c,floor,images,v.logicalWidth,v.logicalHeight,paint)else c.drawBitmap(floor,null,RectF(0f,0f,v.logicalWidth,v.logicalHeight),paint)
   fun bitmap(file:String,r:List<Float>){val b=images.getValue(file);paint.alpha=255;c.drawBitmap(b,null,RectF(r[0],r[1],r[0]+r[2],r[1]+r[3]),paint)}
   val now=nowOverride?:ZonedDateTime.now(ZoneId.of(s.zone));val weatherKey=WeatherVisual.key(s.weather,now);weatherBlend.update(weatherKey,frame,s.reduced||s.paused)
   val weatherMix=weatherBlend.progress(frame,s.reduced||s.paused)
   // Weather is strictly inside each glass mask and behind furniture/characters.
   manifest.weatherWindows.forEach{w->if(weatherMix<1)WeatherRenderer.draw(c,w,weatherBlend.previous,frame+ambientOffsetMs,s.reduced||s.paused,images,paint);WeatherRenderer.draw(c,w,weatherBlend.current,frame+ambientOffsetMs,s.reduced||s.paused,images,paint,(weatherMix*255).roundToInt())}
   val quota=weeklyQuotaForView(s,now.toInstant())
   fun information(p:Furniture){val r=p.widgetRect?:p.rect
    if(p.id.startsWith("colleague-monitor-")){
     val screen=p.screen!!;paint.color=android.graphics.Color.rgb(32,43,48);c.drawRect(screen[0],screen[1],screen[0]+screen[2],screen[1]+screen[3],paint)
     val slot=p.id.substringAfterLast('-').toInt();val cursor=if(s.reduced||s.paused||officePoses[slot]?.front==true)0f else ((frame+slot*900)%6400)/6400f
     paint.color=android.graphics.Color.rgb(100,150,140)
     for(row in 0..2)c.drawRect(screen[0]+3,screen[1]+3+row*4,screen[0]+7+cursor*20,screen[1]+4+row*4,paint)
    }
    when(p.id){
     "memo"->{val notes=s.desk.notes;text.color=android.graphics.Color.rgb(52,39,32);val lines=if(notes.status in listOf("ready","empty"))listOf((notes.sections.firstOrNull{it.id=="doing"}?.items?.firstOrNull()?.text?.take(5)?:"업무 메모") to 6f,("확인 "+(notes.sections.firstOrNull{it.id=="waiting"}?.items?.size?:0)+"개"+(if(notes.freshness!="fresh")" · 지연"else "")) to 6f)else listOf("업무 메모" to 6f,(if(notes.status=="unconfigured")"연결 준비"else "갱신 지연") to 5f);drawDisplayText(c,text,p.screen?:r,lines,gap=1f,pad=.5f)}
     "clock"->{text.color=android.graphics.Color.rgb(231,243,213);val home=manifest.sceneId=="home";val lines=mutableListOf(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) to if(home)30f else min(25f,r[3]*.55f))
      if(!p.render&&p.screen!=null){val inner=p.screen;paint.color=android.graphics.Color.rgb(35,39,46);c.drawRect(inner[0],inner[1],inner[0]+inner[2],inner[1]+inner[3],paint)}
      if(home)lines.add(now.format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 E",java.util.Locale.KOREAN)) to 11f)
      drawDisplayText(c,text,p.screen?:r,lines)
     }
     "calendar"->{text.color=android.graphics.Color.rgb(52,39,32);drawDisplayText(c,text,r,listOf("${now.monthValue}월" to 9f,"${now.dayOfMonth}" to 20f))}
     "desk"->{val screen=p.screen?:return;text.color=android.graphics.Color.rgb(231,243,213);paint.color=android.graphics.Color.rgb(27,36,39);c.drawRect(screen[0],screen[1],screen[0]+screen[2],screen[1]+screen[3],paint)
      if(baseAction in listOf("work","think")&&seated&&!s.reduced&&!s.paused){paint.color=android.graphics.Color.rgb(79,102,100);val progress=((frame%3500)/3500f)*screen[2];c.drawRect(screen[0],screen[1]+screen[3]-3,screen[0]+progress,screen[1]+screen[3]-2,paint)}
      drawDisplayText(c,text,screen,listOf(remainingPercentText(quota) to 14f,resetText(quota,now.toInstant(),short=true) to 9f))
     }
    }
   }
   fun prop(p:Furniture,part:String="all"){
    if(p.render){if(part!="front")bitmap(p.backAsset?:p.id+"-back.png",p.rect);if(part!="back")bitmap(p.frontAsset?:p.id+"-front.png",p.rect)}
    if(part!="front")information(p)
   }
   layerOrder.forEach{layer->when(layer.kind){
   LayerKind.FURNITURE->prop(manifest.furniture[layer.index],layer.part)
   LayerKind.AMBIENT->{val a=manifest.ambient[layer.index];val d=a.rect;val phase=playback(a)
    if(a.kind in listOf("window","clouds")&&manifest.weatherWindows.isNotEmpty()){
     if(a.kind=="window")bitmap("day/window-base.png",d).also{val w=manifest.weatherWindows.firstOrNull{it.id=="window"};if(w!=null){if(weatherMix<1)WeatherRenderer.draw(c,w,weatherBlend.previous,frame+ambientOffsetMs,s.reduced||s.paused,images,paint);WeatherRenderer.draw(c,w,weatherBlend.current,frame+ambientOffsetMs,s.reduced||s.paused,images,paint,(weatherMix*255).roundToInt())}}
    }else if(a.kind=="cat"&&a.motionRect!=null){
     val b=images.getValue(a.atlas);val r=a.frameRects[0];val q=a.motionRect
     val left=d[0]+q[0]*d[2]/r[2];val top=d[1]+q[1]*d[3]/r[3];val right=left+q[2]*d[2]/r[2];val bottom=top+q[3]*d[3]/r[3]
     val token=c.save();c.clipOutRect(left,top,right,bottom);paint.alpha=255;c.drawBitmap(b,Rect(r[0],r[1],r[0]+r[2],r[1]+r[3]),RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),paint);c.restoreToCount(token)
     val back=c.save();c.clipRect(left,top,right,bottom)
     val rise=CatBreathing.rise(a,frame+ambientOffsetMs,s.reduced||s.paused)*d[3]/r[3]
     c.drawBitmap(b,Rect(r[0]+q[0],r[1]+q[1],r[0]+q[0]+q[2],r[1]+q[1]+q[3]),RectF(left,top-rise,right,bottom),paint);c.restoreToCount(back)
    }else if(a.drift){
     if(a.kind=="clouds"){
      val r=a.frameRects.first();val q=TrainSky.rect(a,frame+ambientOffsetMs,s.reduced||s.paused);paint.alpha=255
      c.drawBitmap(images.getValue(a.atlas),Rect(r[0],r[1],r[0]+r[2],r[1]+r[3]),RectF(q[0],q[1],q[0]+q[2],q[1]+q[3]),paint)
     }else{
     if(a.kind=="window")bitmap("day/window-base.png",d)
     val token=c.saveLayer(RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),null);c.clipRect(d[0],d[1],d[0]+d[2],d[1]+d[3])
     val cloud=images.getValue("day/clouds-calm-v2.png");val w=if(a.kind=="window")58f else d[2]*.52f;val h=if(a.kind=="window")38f else d[3]*.65f;val y=d[1]+if(a.kind=="window")20 else 0
     paint.alpha=255;SkyDrift.positions(a,frame+ambientOffsetMs,s.reduced||s.paused).forEach{x->c.drawBitmap(cloud,Rect(2,2,66,66),RectF(x,y,x+w,y+h),paint)}
     a.mask?.let{paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN);val b=images.getValue(it);c.drawBitmap(b,null,RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),paint);paint.xfermode=null}
     c.restoreToCount(token)
     }
    }else{
    fun draw(index:Int,alpha:Int){if(alpha<=0)return;val r=a.frameRects[index];paint.alpha=alpha;c.drawBitmap(images.getValue(a.atlas),Rect(r[0],r[1],r[0]+r[2],r[1]+r[3]),RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),paint)}
    // Blend in a temporary layer so two opaque frames do not darken each other.
    if(phase.blend<=0)draw(phase.first,255)else{
     val token=c.saveLayer(RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),null)
     draw(phase.first,((1-phase.blend)*255).roundToInt());paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD);draw(phase.next,(phase.blend*255).roundToInt());paint.xfermode=null
     c.restoreToCount(token)
    };paint.alpha=255
    }
   }
   LayerKind.COWORKER->{val npc=manifest.coworkers[layer.index];roster.getOrNull(npc.slot)?.let{id->
    val p=officePoses.getValue(npc.slot);val src=CoworkerPose.source(p,s.reduced||s.paused);val b=images.getValue(CoworkerPose.atlas(id,p));val r=CoworkerPose.rect(npc,p);val x=r[0];val y=r[1];val scale=npc.size/64f;val regions=CoworkerPose.regions(p)
    val token=c.save();regions.forEach{q->c.clipOutRect(x+q[0]*scale,y+q[1]*scale,x+(q[0]+q[2])*scale,y+(q[1]+q[3])*scale)}
    paint.alpha=255;c.drawBitmap(b,Rect(src[0],src[1],src[0]+64,src[1]+64),RectF(x,y,x+npc.size,y+npc.size),paint);c.restoreToCount(token)
    regions.forEachIndexed{arm,q->val t=c.save();val left=x+q[0]*scale;val top=y+q[1]*scale;val right=left+q[2]*scale;val bottom=top+q[3]*scale;c.clipRect(left,top,right,bottom)
     c.drawBitmap(b,Rect(src[0]+q[0],src[1]+q[1],src[0]+q[0]+q[2],src[1]+q[1]+q[3]),RectF(left,top-CoworkerPose.rise(npc.slot,arm,frame,s.reduced||s.paused)*scale,right,bottom),paint);c.restoreToCount(t)
    }
    if(p.action in listOf("look","wave","chat")&&p.elapsed in 220..2200){paint.color=android.graphics.Color.rgb(250,238,214);c.drawRoundRect(RectF(npc.position[0]-11,npc.position[1]-78,npc.position[0]+11,npc.position[1]-63),3f,3f,paint);text.color=android.graphics.Color.rgb(82,63,45);text.textSize=11f;c.drawText(if(p.action=="look")"?"else if(p.action=="wave")"♪"else "···",npc.position[0],npc.position[1]-67,text)}
   }}
   LayerKind.ACTOR->{
    if(anim!=null&&frameRect!=null){val r=frameRect;val o=spriteOrigin();paint.color=android.graphics.Color.argb(40,44,25,16);if(!inBed)c.drawOval(RectF(actorPoint.x-20,actorPoint.y-3,actorPoint.x+20,actorPoint.y+3),paint);paint.alpha=255;c.drawBitmap(images.getValue(anim.atlas),Rect(r[0],r[1],r[0]+r[2],r[1]+r[3]),RectF(o.x,o.y,o.x+r[2],o.y+r[3]),paint)
     if(sparkling&&s.reduced){paint.colorFilter=android.graphics.PorterDuffColorFilter(android.graphics.Color.argb(100,255,219,102),android.graphics.PorterDuff.Mode.SRC_ATOP);c.drawBitmap(images.getValue(anim.atlas),Rect(r[0],r[1],r[0]+r[2],r[1]+r[3]),RectF(o.x,o.y,o.x+r[2],o.y+r[3]),paint);paint.colorFilter=null}
    }
   }
   }}
   if(interaction.phase=="curious"||interaction.menu){text.color=android.graphics.Color.rgb(255,225,151);text.textSize=24f;c.drawText("?",actorPoint.x,actorPoint.y-92,text)}
   if(interaction.dizzy&&interaction.phase in listOf("held","dizzy")){text.color=android.graphics.Color.rgb(255,226,133);text.textSize=14f;for(i in 0..2){val a=if(s.reduced)i*2.1 else frame/400.0+i*2.1;c.drawText("✦",actorPoint.x+(cos(a)*26).toFloat(),actorPoint.y-80+(sin(a)*6).toFloat(),text)}}
   drawRoomLighting(c,manifest,s.night)
   c.restoreToCount(save)
  }
 }
 val density=LocalDensity.current;val width=with(density){(roomSize.width.toDp()-24.dp).coerceAtLeast(120.dp).coerceAtMost(320.dp)}
 val screen=viewport(roomSize.width.toFloat(),roomSize.height.toFloat()).screen(actorPoint)
 val menuX=(screen.x-with(density){width.toPx()}/2).toInt().coerceIn(8,(roomSize.width-with(density){width.toPx()}.toInt()-8).coerceAtLeast(8))
 val menuY=(screen.y-110*viewport(roomSize.width.toFloat(),roomSize.height.toFloat()).scale-menuSize.height).toInt().coerceIn(8,(roomSize.height-menuSize.height-8).coerceAtLeast(8))
 AnimatedVisibility(interaction.menu,Modifier.offset{IntOffset(menuX,menuY)},enter=fadeIn(tween(if(s.reduced)0 else 220))+scaleIn(tween(if(s.reduced)0 else 220),initialScale=.92f),exit=fadeOut(tween(if(s.reduced)0 else 120))){Surface(Modifier.width(width).onSizeChanged{menuSize=it},shape=RoundedCornerShape(20.dp),color=cream,shadowElevation=8.dp){Column(Modifier.padding(8.dp).heightIn(max=with(density){(roomSize.height-16).coerceAtLeast(100).toDp()}).verticalScroll(androidx.compose.foundation.rememberScrollState())){PresetOption.entries.forEach{option->TextButton(onClick={send(option.id);interaction.close();frame=SystemClock.elapsedRealtime()},enabled=!s.controls.busy,modifier=Modifier.fillMaxWidth()){Text(option.text,fontFamily=Paperlogy,color=navy,modifier=Modifier.fillMaxWidth())}}}}}
 var notice by remember{mutableStateOf(false)};LaunchedEffect(s.controls.message){notice=s.controls.message.isNotBlank();delay(6000);notice=false}
 if(notice&&s.controls.message.isNotBlank())Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp),shape=RoundedCornerShape(12.dp),color=cream.copy(alpha=.96f)){Text(s.controls.message,Modifier.padding(10.dp),fontFamily=Paperlogy,color=ink)}
 }
}
