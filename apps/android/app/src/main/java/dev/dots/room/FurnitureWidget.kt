package dev.dots.room

import androidx.compose.foundation.background
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** These hit regions use scene coordinates, so resizing does not detach them from the furniture. */
enum class RoomWidget(val label:String,val furnitureId:String){CLOCK("시계","clock"),CALENDAR("달력","calendar"),MONITOR("모니터","desk"),MEMO("업무 메모","memo"),WEATHER("날씨","window")}
fun roomWidgetFurniture(manifest:Manifest,widget:RoomWidget):Furniture? {
 val id=if(widget==RoomWidget.MEMO&&manifest.sceneId=="home")"bookcase"else widget.furnitureId
 return manifest.furniture.firstOrNull{it.id==id}
}
fun roomWidgetAt(manifest:Manifest,x:Float,y:Float):RoomWidget? = RoomWidget.entries.filter{it!=RoomWidget.WEATHER}.firstOrNull{widget->
 val furniture=roomWidgetFurniture(manifest,widget)?:return@firstOrNull false;val r=furniture.widgetRect?:if(widget==RoomWidget.MONITOR)furniture.screen!! else furniture.rect
 val padding=if(widget==RoomWidget.MEMO&&manifest.sceneId!="home")12f else if(widget==RoomWidget.MONITOR)6f else 0f
 x>=r[0]-padding&&x<=r[0]+r[2]+padding&&y>=r[1]-padding&&y<=r[1]+r[3]+padding
} ?: if(manifest.weatherWindows.any{w->x>=w.rect[0]&&x<=w.rect[0]+w.rect[2]&&y>=w.rect[1]&&y<=w.rect[1]+w.rect[3]})RoomWidget.WEATHER else null
fun calendarCells(month:YearMonth):List<LocalDate?> = List((month.atDay(1).dayOfWeek.value%7+month.lengthOfMonth()+6)/7*7){index->
 val day=index-month.atDay(1).dayOfWeek.value%7+1
 if(day in 1..month.lengthOfMonth())month.atDay(day) else null
}
@Composable fun FurnitureWidget(widget:RoomWidget,s:ViewState,onSync:()->Unit={},onConnection:()->Unit={},nowOverride:ZonedDateTime?=null,close:()->Unit){
 var now by remember(s.zone){mutableStateOf(nowOverride?:ZonedDateTime.now(ZoneId.of(s.zone)))}
 LaunchedEffect(s.zone){while(true){now=nowOverride?:ZonedDateTime.now(ZoneId.of(s.zone));delay(1000)}}
 AlertDialog(onDismissRequest=close,title={Text(if(widget==RoomWidget.MONITOR)"주간 사용 한도"else widget.label,fontWeight=FontWeight.Bold)},text={
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
   when(widget){
    RoomWidget.WEATHER->WeatherPanel(s,now)
    RoomWidget.MEMO->WorkNotesPanel(s)
    RoomWidget.CLOCK->{Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),fontSize=42.sp,fontWeight=FontWeight.Bold,color=navy);Text(now.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE",Locale.KOREAN)),fontSize=18.sp);Text(s.zone,fontSize=14.sp)}
    RoomWidget.CALENDAR->CalendarWidget(now)
    RoomWidget.MONITOR->{
     val quota=weeklyQuotaForView(s,now.toInstant());val left=remainingPercent(quota)
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(resetText(quota,now.toInstant()),Modifier.weight(1f),fontSize=18.sp);Text(remainingPercentText(quota),fontSize=28.sp,fontWeight=FontWeight.Bold)}
     if(left!=null)Box(Modifier.fillMaxWidth().height(8.dp).progressSemantics((left/100).toFloat()).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(0xFFE0DBD4))){Box(Modifier.fillMaxWidth((left/100).toFloat()).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFFFF7024),RoundedCornerShape(4.dp)))}
     quotaResetDate(quota,s.zone)?.let{Text("초기화: $it",fontSize=14.sp)}
     quotaAnnotation(quota)?.let{Text(it,fontSize=12.sp)}
     Button(onClick=onSync,enabled=!s.controls.busy&&s.paired&&!s.localDemo){if(s.controls.syncing){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))};Text(if(s.controls.syncing)"동기화 중…"else "동기화")}
     OutlinedButton(onClick=onConnection){Text("무선 연결 설정")}
     ExecutionPanel(s)
     s.controls.lastSync?.let{Text("마지막 동기화: "+Instant.parse(it).atZone(ZoneId.of(s.zone)).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm:ss")),fontSize=12.sp)}
     if(s.controls.message.isNotBlank())Text(s.controls.message,fontSize=13.sp)

    }
   }
  }
 },confirmButton={TextButton(onClick=close){Text("닫기")}})
}
@Composable private fun CalendarWidget(now:ZonedDateTime){
 var month by remember{mutableStateOf(YearMonth.from(now))};var followingToday by remember{mutableStateOf(true)}
 LaunchedEffect(YearMonth.from(now)){if(followingToday)month=YearMonth.from(now)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  TextButton(onClick={followingToday=false;month=month.minusMonths(1)}){Text("이전")}
  Text("${month.year}년 ${month.monthValue}월",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
  TextButton(onClick={followingToday=false;month=month.plusMonths(1)}){Text("다음")}
 }
 Row(Modifier.fillMaxWidth()){listOf("일","월","화","수","목","금","토").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,fontSize=14.sp)}}}
 calendarCells(month).chunked(7).forEach{week->Row(Modifier.fillMaxWidth()){week.forEach{day->Box(Modifier.weight(1f).height(26.dp).background(if(day==now.toLocalDate())navy.copy(alpha=.15f) else cream),contentAlignment=Alignment.Center){Text(day?.dayOfMonth?.toString()?:"",fontSize=18.sp,color=ink,fontWeight=if(day==now.toLocalDate())FontWeight.Bold else FontWeight.Normal)}}}}
 TextButton(onClick={followingToday=true;month=YearMonth.from(now)}){Text("오늘 · ${now.monthValue}월 ${now.dayOfMonth}일")}
 Text(now.zone.id,fontSize=12.sp)
 }
}
