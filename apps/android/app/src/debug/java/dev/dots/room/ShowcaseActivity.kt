package dev.dots.room

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.ZonedDateTime
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Offline, synthetic showcase. This Activity is absent from release builds. */
class ShowcaseActivity:ComponentActivity(){
 private var fixtureElapsed by mutableLongStateOf(0L)
 private fun fixtureClock()=100000L+fixtureElapsed
 private var replyReceiver:BroadcastReceiver?=null
 override fun onDestroy(){replyReceiver?.let{unregisterReceiver(it)};super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  WindowCompat.setDecorFitsSystemWindows(window,false)
  WindowCompat.getInsetsController(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
  val scene=intent.getStringExtra("scene")?.takeIf{it in listOf("home","office","subway-am","subway-pm")}?:"home"
  val time=when(scene){"office"->"10:30";"subway-am"->"07:30";"subway-pm"->"17:30";else->"18:40"}
  val now=ZonedDateTime.parse("2026-10-05T$time:00+09:00[Asia/Seoul]")
  val manifest=assets.open("bami/day/$scene.json").bufferedReader().use{protocolJson.decodeFromString<Manifest>(it.readText())}
  val vm=BamiViewModel(application,"-public-showcase")
  val fixed=showcaseState(now,intent.getBooleanExtra("animate",false))
  if(intent.getBooleanExtra("animate",false)){
   replyReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){fixtureElapsed=intent.getLongExtra("elapsed",0).coerceIn(0,20000)}}
   ContextCompat.registerReceiver(this,replyReceiver,IntentFilter("dev.dots.room.SHOWCASE_REPLY"),ContextCompat.RECEIVER_EXPORTED)
  }
  vm.draft("오늘 만든 화면을 같이 살펴볼까?\n잠깐 쉬었다 이야기해도 좋아.")
  setContent{val live by vm.state.collectAsState();val state=fixed.copy(draft=live.draft,portraitMode=if(intent.getBooleanExtra("full",false))"scene"else live.portraitMode)
   MaterialTheme(colorScheme=lightColorScheme(primary=navy,background=cream,surface=cream,onSurface=ink),typography=BamiTypography){
    if(intent.getBooleanExtra("animate",false)){
     var animation by remember{mutableStateOf(fixed.copy(data=fixed.data.copy(messages=fixed.data.messages.dropLast(1)),behavior=Behavior("idle",since=fixtureClock(),reactionMessageId="demo-2")))}
     LaunchedEffect(fixtureElapsed){val data=fixed.data.copy(messages=if(fixtureElapsed>=1000)fixed.data.messages else fixed.data.messages.dropLast(1));animation=animation.copy(data=data,behavior=ActualBehavior.next(data,animation.desk,animation.behavior,fixtureClock(),true,now.toInstant()))}
     Row(Modifier.fillMaxSize().background(cream)){RoomRenderer(animation,Modifier.weight(1f).fillMaxHeight(),onSpeechVisible={animation=animation.copy(behavior=ConversationReaction.speechVisible(animation.behavior,fixtureClock()))},manifestOverride=manifest,nowOverride=now,clockOverride={fixtureClock()});ChatPane(animation,vm,Modifier.weight(1f).fillMaxHeight())}
    }else DeskScreen(state,vm,manifest,nowOverride=now)
    intent.getStringExtra("widget")?.let{n->RoomWidget.entries.firstOrNull{it.name==n}?.let{FurnitureWidget(it,state,nowOverride=now){finish()}}}
    if(intent.getBooleanExtra("composer",false))MessageComposer(state,vm){finish()}
   }
  }
 }
}
fun showcaseState(now:ZonedDateTime,animate:Boolean=false):ViewState {
 val observed=now.toInstant().toString()
 val rows=listOf("user" to "오늘 만들던 화면을 한 번 더 봐줄래?","assistant" to "응, 책상에서 차분히 살펴볼게. 답변이 준비되면 앞으로 나와서 이야기할게.","user" to "오늘은 잠깐 쉬어도 돼.","assistant" to "알겠어. 흰색 책도 조금 읽고, 방 안을 천천히 돌아다닐게. 새 이야기가 오면 다시 만나자!")
 val messages=rows.mapIndexed{i,(role,text)->Message("demo-$i","demo-conversation",role=role,revision=1,content=text,state="observed",observation=Observation("browser-ui","demo-$i",null,null,observed,null,"local","unknown","unavailable"))}
 val quota=Quota("known",72.0,"manual","account",observed,"manual","2026-10-11T09:00:00Z","percent","demo-weekly","주간 사용 한도",10080.0,28.0)
 val notes=WorkNotes(status="ready",revision=1,sections=listOf(NoteSection("doing","지금 하는 일",listOf(NoteItem("d1","새 화면의 작은 움직임 살펴보기"))),NoteSection("waiting","형아 확인이 필요한 일",listOf(NoteItem("w1","퇴근길 배경의 색 확인하기"))),NoteSection("done","오늘 끝낸 일",listOf(NoteItem("f1","시계와 달력 정리"))),NoteSection("free","형아 자유 메모",listOf(NoteItem("n1","커피 한 잔 마시고 천천히 해도 좋아.")))),observedAt=observed,lastSuccessAt=observed,freshness="fresh",error="")
 val behavior=if(animate)Behavior("talk",since=SystemClock.elapsedRealtime(),reactionMessageId="demo-3",answering=true)else Behavior("idle",since=SystemClock.elapsedRealtime())
 return ViewState(data=AppState(sourceStatus=SourceStatus("demo",observed,"합성 예시",taskEvents="unsupported"),messages=messages,quotas=listOf(quota)),transport="연결됨",behavior=behavior,localDemo=true,messageMode="mock",controls=ControlState(canSend=true,canSync=true,canText=true),desk=DeskState(notes=notes),weather=WeatherState(status="ready",fetchedAt=observed,attemptedAt=observed,error="",reading=WeatherReading(observed,2,22.0,21.0,55.0,0.0,2.1,true,16.0,24.0)))
}
