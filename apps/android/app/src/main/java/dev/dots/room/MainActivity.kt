package dev.dots.room

import android.os.Bundle
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.scrollBy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val cream=Color(0xFFF8F1E7);val ink=Color(0xFF332B33);val navy=Color(0xFF163E85)
class MainActivity:ComponentActivity(){private var model:BamiViewModel?=null
 private val batteryReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){model?.state?.value?.let{updatePower(it)}}}
 private fun immersive(){WindowCompat.getInsetsController(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
 private fun updatePower(s:ViewState){val battery=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val charging=(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)>0;if(s.awake&&charging&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);window.attributes=window.attributes.apply{screenBrightness=if(s.night)0.18f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE}}
 override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
 override fun onDestroy(){model?.foreground(false,this);unregisterReceiver(batteryReceiver);super.onDestroy()}

 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);registerReceiver(batteryReceiver,IntentFilter(Intent.ACTION_BATTERY_CHANGED));WindowCompat.setDecorFitsSystemWindows(window,false);WindowCompat.getInsetsController(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE};setContent{val vm=(application as BamiApplication).model;model=vm;LaunchedEffect(vm){UsbPairing.start(this@MainActivity,vm)};DisposableEffect(vm){val observer=LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_START)vm.foreground(true,this@MainActivity) else if(event==Lifecycle.Event.ON_STOP)vm.foreground(false,this@MainActivity) };lifecycle.addObserver(observer);vm.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),this@MainActivity);onDispose{lifecycle.removeObserver(observer)}};val s by vm.state.collectAsState();LaunchedEffect(s.paired,s.localDemo,s.wakeOnMessage){val receive=Intent(this@MainActivity,BamiReceiveService::class.java);if(s.paired&&!s.localDemo&&s.wakeOnMessage)androidx.core.content.ContextCompat.startForegroundService(this@MainActivity,receive)else stopService(receive)};MaterialTheme(colorScheme=lightColorScheme(primary=navy,background=cream,surface=cream,onSurface=ink),typography=BamiTypography){DeskScreen(s,vm)};LaunchedEffect(s.awake,s.night){updatePower(s)}};}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);model?.let{UsbPairing.start(this,it)}}
 override fun onStart(){super.onStart();model?.foreground(true,this)}
 override fun onResume(){super.onResume();model?.foreground(true,this);model?.state?.value?.let{updatePower(it)}}
 override fun onStop(){model?.foreground(false,this);window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onStop()}
}
@Composable fun DeskScreen(s:ViewState,vm:BamiViewModel,sceneOverride:Manifest?=null,ambientOffsetMs:Long=0,nowOverride:ZonedDateTime?=null){
 var settings by rememberSaveable{mutableStateOf(false)};var connectionSettings by rememberSaveable{mutableStateOf(false)};var composer by rememberSaveable{mutableStateOf(false)};var widget by remember{mutableStateOf<RoomWidget?>(null)}
 val list=rememberLazyListState();val follow=rememberSaveable{mutableStateOf(true)}
 Surface(Modifier.fillMaxSize()){Box(Modifier.fillMaxSize()){
  BoxWithConstraints(Modifier.fillMaxSize()){
   val landscape=maxWidth>maxHeight
   if(landscape)Row(Modifier.fillMaxSize()){
    DayRoom(s,vm,Modifier.weight(1f).fillMaxHeight(),sceneOverride,ambientOffsetMs,nowOverride){widget=it}
    ChatPane(s,vm,Modifier.weight(1f).fillMaxHeight(),list,follow,onCompose={composer=true})
   }else when(s.portraitMode){
    "scene"->DayRoom(s,vm,Modifier.fillMaxSize(),sceneOverride,ambientOffsetMs,nowOverride){widget=it}
    "chat"->ChatPane(s,vm,Modifier.fillMaxSize(),list,follow,onCompose={composer=true})
    else->Column(Modifier.fillMaxSize()){
     DayRoom(s,vm,Modifier.weight(1f).fillMaxWidth(),sceneOverride,ambientOffsetMs,nowOverride){widget=it}
     ChatPane(s,vm,Modifier.weight(1f).fillMaxWidth(),list,follow,onCompose={composer=true})
    }
   }
   if(!landscape)TextButton(onClick={vm.setPortraitMode(when(s.portraitMode){"scene"->"chat";"chat"->"split";else->"scene"})},modifier=Modifier.align(Alignment.BottomEnd).padding(6.dp)){
    Text(when(s.portraitMode){"scene"->"바미 전체";"chat"->"대화 전체";else->"상하 분할"},fontFamily=Paperlogy,fontSize=12.sp)
   }
  }
  TextButton(onClick={settings=true},modifier=Modifier.align(Alignment.TopEnd).padding(4.dp).semantics{contentDescription="설정, ${s.transport}, ${sourceLabel(s.data.sourceStatus.state)}"}){Text("⋯",fontSize=26.sp,color=Color(0xFF6B6260))}
 }}
 if(connectionSettings)ConnectionDialog(s,vm){connectionSettings=false}
 if(settings)SettingsScreen(s,vm){settings=false}
 if(composer)MessageComposer(s,vm){composer=false}
 widget?.let{FurnitureWidget(it,s,onSync=vm::syncSource,onConnection={widget=null;connectionSettings=true},nowOverride=nowOverride){widget=null}}
}

fun sourceLabel(state:String)=when(state){"demo"->"데모";"manual"->"수동 입력";"live"->"실제 소스";"stale"->"원천 오래됨";else->"dots 미연동"}
fun taskLabel(t:Task)=when(t.status){"waiting_user"->"형아 확인 기다리는 중";"completed"->"완료";"failed"->"문제 발생";"canceled"->"취소됨";"queued"->"대기 중";"running"->when(t.phase){"reading"->"읽는 중";"responding"->"응답 중";"thinking"->"공개 진행 중";else->"작업 중"};else->"상태 확인 불가"}
private enum class ChatAnchor { END }
@Composable fun ChatPane(s:ViewState,vm:BamiViewModel,modifier:Modifier=Modifier,list:LazyListState=rememberLazyListState(),follow:MutableState<Boolean>?=null,onCompose:(()->Unit)?=null,loadMedia:suspend (String)->ByteArray=vm::loadMedia){val scope=rememberCoroutineScope();val followingState=follow?:rememberSaveable{mutableStateOf(true)};var following by followingState;var readCount by remember{mutableIntStateOf(0)};val dragging by list.interactionSource.collectIsDraggedAsState()
 // Only explicit gestures/accessibility scrolling change follow mode; our own jump must not disable it.
 LaunchedEffect(dragging){if(dragging)following=false}
 // Follow the actual end, including a tall bubble and media that finishes loading later.
 LaunchedEffect(list){snapshotFlow{Triple(following&&!dragging,list.canScrollForward,list.layoutInfo.let{Triple(it.totalItemsCount,it.viewportEndOffset,it.visibleItemsInfo.map{item->Triple(item.index,item.offset,item.size)})})}.collect{(followEnd,canForward,layout)->
  if(followEnd&&canForward&&layout.first>0)list.scrollToItem(layout.first-1)
 }}
 LaunchedEffect(s.data.messages,following){if(following){readCount=s.data.messages.size}}
 LaunchedEffect(list){snapshotFlow{list.isScrollInProgress}.drop(1).collect{scrolling->if(!scrolling&&!dragging&&!list.canScrollForward)following=true}}
 Column(modifier.padding(horizontal=18.dp,vertical=6.dp)){
  val actual=runtimeState(s.desk.execution,s.transport=="연결됨");if(actual in listOf("blocked","waiting_user"))Text(runtimeLabel(actual),fontSize=s.font.sp,color=navy)
  // Connection/source diagnostics live in Settings. The main surface is reserved for room and conversation.
  if(s.data.tasks.any{it.status in listOf("running","waiting_user","failed")}) {
   val t=s.data.tasks.find{it.taskId==s.selectedTask}?:s.data.tasks.last{it.status in listOf("running","waiting_user","failed")}
   TextButton(onClick={vm.selectTask(if(s.selectedTask==t.taskId)null else t.taskId)},contentPadding=PaddingValues(0.dp)){Text(t.publicSummary,fontSize=13.sp,color=navy)}
  }
  LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics{scrollBy{_,y->following=false;scope.launch{list.animateScrollBy(y);following=!list.canScrollForward;if(following)readCount=s.data.messages.size};true}},state=list,contentPadding=PaddingValues(top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   items(s.data.messages,key={it.messageId}){m->
    val user=m.role=="user"
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start){
     Column(Modifier.padding(start=if(user)38.dp else 0.dp,end=if(user)0.dp else 18.dp).widthIn(max=420.dp).background(if(user)Color(0xFFECE4D8)else Color(0xFFE8EDF5),RoundedCornerShape(topStart=16.dp,topEnd=16.dp,bottomEnd=if(user)4.dp else 16.dp,bottomStart=if(user)16.dp else 4.dp)).padding(horizontal=14.dp,vertical=12.dp)){
      if(!user)Text(if(m.observation?.origin=="browser-ui")"바미 - GPT DOTS" else when(s.messageMode){"mock"->"바미 · 데모";"manual"->"바미 · 수동";"live"->"바미 · 실제";else->"바미"},fontSize=12.sp,color=navy,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,fontFamily=Paperlogy)
      if(m.content.isNotEmpty())SelectionContainer{Text(m.content,fontSize=s.font.sp,lineHeight=(s.font*1.5).sp,color=ink,fontFamily=Paperlogy)}
      m.images.forEach{MessageImage(it,loadMedia,s.transport=="연결됨",listOf(s.controls.lastSync,s.connectionMode,s.tailscaleAddress).joinToString("|"))}
      if(m.state=="streaming")Text("응답 중",fontSize=11.sp,color=Color.Gray,fontFamily=Paperlogy)
     }
    }
   }
   item(key=ChatAnchor.END){Spacer(Modifier.height(1.dp))}
  }
  if(!following&&list.canScrollForward)TextButton(onClick={following=true;readCount=s.data.messages.size}){Text("아래로 돌아가기 · 새 메시지 ${(s.data.messages.size-readCount).coerceAtLeast(0)}개")}
  onCompose?.let{Row(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.Center){KeyboardButton(it)}}

 }
}
@Composable fun SettingsScreen(s:ViewState,vm:BamiViewModel,close:()->Unit){var token by remember{mutableStateOf("")};var zone by remember{mutableStateOf(s.zone)};var port by remember{mutableStateOf(s.port.toString())};var font by remember{mutableIntStateOf(s.font)};var reduced by remember{mutableStateOf(s.reduced)};var paused by remember{mutableStateOf(s.paused)};var awake by remember{mutableStateOf(s.awake)};var night by remember{mutableStateOf(s.night)};var wakeOnMessage by remember{mutableStateOf(s.wakeOnMessage)}
 AlertDialog(onDismissRequest=close,title={Text("바미 설정")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
  item{Text("브리지: ${s.transport}\n소스: ${sourceLabel(s.data.sourceStatus.state)}\n${s.data.sourceStatus.detail}\n마지막 수신: ${s.lastReceived?:"없음"}\n메시지 출처: ${when(s.messageMode){"mock"->"데모";"manual"->"수동 입력";"live"->"브라우저/실제 소스";else->"확인 불가"}}\n동기화한 대화: ${s.data.messages.size}개\n주간 한도: ${remainingPercentText(weeklyQuota(s.data.quotas))}",fontSize=12.sp)}
  if(s.error.isNotBlank())item{Text(s.error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
  item{Text("공식 dots API: NOT_IMPLEMENTED\n브라우저 수집: browser-dots · 표시된 대화만 읽어요.\n실제 상태를 읽을 수 없으면 미확인으로 유지해요. 짧은 산책과 독서는 생활 연출이며, 실제 작업 완료로 표시하지 않아요.",fontSize=13.sp)}
  item{OutlinedTextField(zone,{zone=it},label={Text("시간대")},singleLine=true)}
  item{OutlinedTextField(port,{port=it},label={Text("USB loopback 포트")},singleLine=true)}
  item{Text("대화 글자 크기: $font");Slider(font.toFloat(),{font=it.toInt()},valueRange=14f..30f,steps=15)}
  item{Toggle("움직임 줄이기",reduced){reduced=it};Toggle("애니메이션 중지",paused){paused=it};Toggle("충전 중 전면 화면 유지",awake){awake=it};Toggle("새 대화가 오면 화면 켜기",wakeOnMessage){wakeOnMessage=it};Toggle("야간 밝기 완화",night){night=it}}
  item{Text("Mac의 바미 전용 페어링 값을 직접 입력해 주세요. Android Keystore로 암호화해 저장해요.",fontSize=12.sp);OutlinedTextField(token,{token=it},label={Text("페어링 값")},visualTransformation=PasswordVisualTransformation(),singleLine=true);TextButton(onClick={vm.setWakeOnMessage(wakeOnMessage);vm.settings(zone,font,reduced,paused,awake,night,port.toIntOrNull()?:0);vm.pair(token);token="";close()}){Text(if(s.paired)"자격 증명 교체" else "페어링 저장")}}
  item{TextButton(onClick={close()}){Text("무선 연결은 모니터 위젯에서 설정해요")}}
  item{Row{TextButton(onClick={vm.retry();close()}){Text("즉시 재연결")};TextButton(onClick={vm.forget();close()}){Text("페어링 해제")}}}
  item{Row{TextButton(onClick={vm.demo();close()}){Text("앱내 데모")};TextButton(onClick={vm.clearHistory();close()}){Text("로컬 기록 삭제")}}}
 }},confirmButton={TextButton(onClick={vm.setWakeOnMessage(wakeOnMessage);vm.settings(zone,font,reduced,paused,awake,night,port.toIntOrNull()?:0);close()}){Text("설정 저장")}},dismissButton={TextButton(onClick=close){Text("닫기")}})
}
@Composable fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=13.sp);Switch(value,change)}}
