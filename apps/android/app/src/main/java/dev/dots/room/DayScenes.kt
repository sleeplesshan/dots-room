package dev.dots.room

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.min
import kotlin.math.max

object DaySchedule {
 fun label(id:String)=when(id){"office"->"회사";"subway-am"->"아침 지하철";"subway-pm"->"저녁 지하철";else->"집"}
 val colleagues=listOf("maru","moni","baerong","madi","ul")
 fun colleagueName(id:String)=when(id){"maru"->"마루";"moni"->"모니";"baerong"->"배롱";"madi"->"마디";"ul"->"울";else->id}
 fun scene(now:ZonedDateTime):String {
  if(now.dayOfWeek in listOf(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY))return "home"
  return when(now.hour){7->"subway-am";in 8..16->"office";17->"subway-pm";else->"home"}
 }
 fun roster(now:ZonedDateTime):List<String>{val absent=Math.floorMod(now.toLocalDate().toEpochDay(),5).toInt();return colleagues.filterIndexed{i,_->i!=absent}}
}
fun poseLabel(id:String)=when(id){"spark"->"노랗게 반짝이는 중";"work"->"컴퓨터를 보는 중";"read"->"책 읽는 중";"walk"->"걷는 중";"sleep","bed-sleep"->"자는 중";"rest"->"앉아서 쉬는 중";"curious"->"궁금한 표정";"held"->"들려 있는 중";"dizzy"->"어지러운 표정";"talk"->"말하는 중";"think"->"생각하는 중";else->"쉬는 중"}
/** Same transform for bitmap drawing, alpha hits, widgets and dragging. */
data class RoomViewport(val width:Float,val height:Float,val logicalWidth:Float=384f,val logicalHeight:Float=448f,val fill:Boolean=false){
 val fit=min(width/logicalWidth,height/logicalHeight).coerceAtLeast(.01f)
 val scale=if(fill)max(width/logicalWidth,height/logicalHeight).coerceAtLeast(.01f)else fit
 val left=(width-logicalWidth*scale)/2;val top=(height-logicalHeight*scale)/2
 fun point(x:Float,y:Float)=Point((x-left)/scale,(y-top)/scale)
 fun screen(p:Point)=Point(left+p.x*scale,top+p.y*scale)
}
@Composable fun DayRoom(s:ViewState,vm:BamiViewModel,modifier:Modifier=Modifier,sceneOverride:Manifest?=null,ambientOffsetMs:Long=0,nowOverride:ZonedDateTime?=null,onWidget:(RoomWidget)->Unit={}){
 val context=LocalContext.current
 var now by remember(s.zone){mutableStateOf(nowOverride?:ZonedDateTime.now(ZoneId.of(s.zone)))}
 var held by remember{mutableStateOf(false)}
 var displayed by remember{mutableStateOf(DaySchedule.scene(now))}
 LaunchedEffect(s.zone){while(true){now=nowOverride?:ZonedDateTime.now(ZoneId.of(s.zone));if(!held)displayed=DaySchedule.scene(now);delay(1000)}}
 val manifest=remember(displayed,sceneOverride){sceneOverride?:context.assets.open("bami/day/$displayed.json").bufferedReader().use{protocolJson.decodeFromString<Manifest>(it.readText())}}
 androidx.compose.animation.Crossfade(targetState=manifest,animationSpec=androidx.compose.animation.core.tween(if(s.reduced)0 else 220),modifier=modifier,label="하루 장면"){m->
  key(m.sceneId){RoomRenderer(s,Modifier,vm::speechVisible,vm::sendPreset,remember(m.sceneId){vm.savedPlacement(m.sceneId)},{vm.savePlacement(it,m.sceneId)},onWidget,m,DaySchedule.roster(now),onHeld={held=it},onSpark=vm::claimSpark,ambientOffsetMs=ambientOffsetMs,nowOverride=nowOverride)}
 }
}
