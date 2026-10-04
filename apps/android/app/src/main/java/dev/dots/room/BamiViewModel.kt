package dev.dots.room

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import java.time.ZoneId

data class ViewState(val data:AppState=AppState(),val transport:String="연결 안 됨",val paired:Boolean=false,val lastReceived:String?=null,val behavior:Behavior=Behavior(),val localDemo:Boolean=false,val zone:String="Asia/Seoul",val font:Int=17,val reduced:Boolean=false,val paused:Boolean=false,val awake:Boolean=false,val night:Boolean=false,val port:Int=8788,val connectionMode:String="USB",val tailscaleAddress:String="",val selectedTask:String?=null,val error:String="",val messageMode:String="unavailable",val wakeOnMessage:Boolean=true,val controls:ControlState=ControlState(),val draft:String="",val portraitMode:String="split",val desk:DeskState=DeskState(),val deskSupported:Boolean=false,val weather:WeatherState=WeatherState(),val weatherSupported:Boolean=false,val weatherReadError:Boolean=false)
class BamiViewModel @JvmOverloads constructor(app:Application,private val storagePrefix:String=""):AndroidViewModel(app) {
 private val prefs=app.getSharedPreferences("bami-settings"+storagePrefix,0);private val secure=SecureStore(app,storagePrefix);private val reducer=AppReducer();private val decoder=EventClient();private val inbound=AtomicInteger();private val json=protocolJson
 private val mutable=MutableStateFlow(ViewState(paired=secure.load("pairing.enc")!=null,zone=prefs.getString("zone","Asia/Seoul")?:"Asia/Seoul",font=prefs.getInt("font",17),reduced=prefs.getBoolean("reduced",false),paused=prefs.getBoolean("paused",false),awake=prefs.getBoolean("awake",false),night=prefs.getBoolean("night",false),port=prefs.getInt("port",8788),connectionMode=prefs.getString("connectionMode","USB")?:"USB",tailscaleAddress=prefs.getString("tailscaleAddress","")?:"",wakeOnMessage=prefs.getBoolean("wakeOnMessage",true),draft=secure.load("draft.enc")?:"",portraitMode=prefs.getString("portraitMode","split")?:"split"))
 internal val protocolStreamId:String? get()=reducer.streamId
 private val arrivals=MutableSharedFlow<Unit>(extraBufferCapacity=16);val messageArrivals=arrivals.asSharedFlow();private val arrivalTracker=MessageArrivalTracker();
 val state=mutable.asStateFlow();private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(5,java.util.concurrent.TimeUnit.SECONDS).readTimeout(0,java.util.concurrent.TimeUnit.SECONDS).build()
 private val media=MediaCache(app,secure,storagePrefix);private val mediaHttp=http.newBuilder().dispatcher(Dispatcher()).readTimeout(10,java.util.concurrent.TimeUnit.SECONDS).build()
 suspend fun loadMedia(id:String):ByteArray{check(foreground&&state.value.transport=="연결됨");return media.load(id,requireNotNull(secure.load("pairing.enc")),profile(),mediaHttp)}
 private var lastSnapshotStream:String?=null;private var lastSnapshotSeq=0L
 private val commandHttp=http.newBuilder().dispatcher(Dispatcher()).readTimeout(25,java.util.concurrent.TimeUnit.SECONDS).callTimeout(25,java.util.concurrent.TimeUnit.SECONDS).build()
 private var controlJob:Job?=null;private var draftJob:Job?=null;private val uiSaveMutex=Mutex()
 private var deskJob:Job?=null;private var weatherJob:Job?=null;private var nextWeatherRead=0L;private val placements=mutableMapOf<String,Placement>();
 private var connectionJob:Job?=null;private var networkJob:Job?=null;private var pairedServerId=secure.load("server-id.enc")
 private val networkObserver=DefaultNetworkObserver(app){networkChanged()}
 private fun connectionFailureCode(e:Exception):String {
  val causes=generateSequence<Throwable>(e){it.cause}.take(8).toList()
  return when{causes.any{it is java.security.cert.CertificateException||it is java.security.cert.CertPathValidatorException}->"TLS_CERTIFICATE";e is javax.net.ssl.SSLPeerUnverifiedException->"TLS_HOSTNAME";e is javax.net.ssl.SSLException->"TLS_HANDSHAKE";e is java.net.UnknownHostException->"DNS";e is java.net.ConnectException->"CONNECTION";e is java.io.InterruptedIOException->"TIMEOUT";e is ControlFailure->e.code;else->"NETWORK"}
 }
 private fun connectionHint()=if(state.value.connectionMode=="USB")"USB 케이블과 새 reverse 설정을 확인해 주세요."else "Tailscale VPN과 Mac 온라인 상태를 확인해 주세요."
 private fun profile()=ConnectionProfile(state.value.connectionMode,state.value.port,state.value.tailscaleAddress)
 fun savedPlacement(sceneId:String="home"):Placement?=placements[sceneId]?:try{secure.load(if(sceneId=="home")"placement.enc"else "placement-$sceneId.enc")?.let{json.decodeFromString<Placement>(it)}}catch(_:Exception){null}
 fun savePlacement(p:Placement,sceneId:String="home"){placements[sceneId]=p;viewModelScope.launch(Dispatchers.IO){secure.save(if(sceneId=="home")"placement.enc"else "placement-$sceneId.enc",json.encodeToString(p))}}
 private var socket:WebSocket?=null;private var generation=0;private var foreground=false;private var activityVisible=false;private var backgroundActive=false;private var reconnectJob:Job?=null;private var cacheJob:Job?=null;private var demoJob:Job?=null;private var retryCount=0;private var receivedAt=0L;private var requestedAt=-10000L;private var negotiated=false
 private val clientId=prefs.getString("clientId",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("clientId",it).apply()}
 init {
  secure.load("weather.enc")?.let{try{mutable.value=state.value.copy(weather=json.decodeFromString<WeatherState>(it).validate())}catch(_:Exception){}}
  weatherJob=viewModelScope.launch{while(isActive){if(activityVisible&&!state.value.localDemo&&state.value.transport=="연결됨"&&state.value.weatherSupported&&SystemClock.elapsedRealtime()>=nextWeatherRead){nextWeatherRead=SystemClock.elapsedRealtime()+60000;val g=generation;try{val next=json.decodeFromJsonElement<WeatherState>(controlRequest("/v1/weather")).validate();if(g==generation&&activityVisible&&state.value.transport=="연결됨"){mutable.value=state.value.copy(weather=next,weatherReadError=false);withContext(Dispatchers.IO){secure.save("weather.enc",json.encodeToString(next))}}}catch(e:Exception){if(e !is CancellationException&&g==generation)mutable.value=state.value.copy(weatherReadError=true)}};delay(1000)}}
  secure.load("desk-state.enc")?.let{try{val cached=json.decodeFromString<DeskState>(it).validate();mutable.value=state.value.copy(desk=cached.copy(execution=cached.execution.copy(freshness="stale"),notes=cached.notes.copy(freshness="stale")))}catch(_:Exception){}}
  deskJob=viewModelScope.launch{while(isActive){if(foreground&&!state.value.localDemo&&state.value.transport=="연결됨"&&state.value.deskSupported){try{val g=generation;val next=json.decodeFromJsonElement<DeskState>(controlRequest("/v1/desk-state")).validate();if(g!=generation||!foreground||state.value.transport!="연결됨")continue;mutable.value=state.value.copy(desk=next);withContext(Dispatchers.IO){secure.save("desk-state.enc",json.encodeToString(next))}}catch(_:Exception){mutable.value=state.value.copy(desk=state.value.desk.let{it.copy(execution=it.execution.copy(freshness="stale"),notes=it.notes.copy(freshness="stale"))})}};delay(5000)}}
  if(secure.load("command.enc")!=null){mutable.value=mutable.value.copy(controls=ControlState(message="이전 전송 결과를 확인해 주세요. 자동으로 다시 보내지 않아요."));secure.clear("command.enc")}

  secure.load("state.enc")?.let{try{val saved=json.decodeFromString<CachedState>(it);reducer.restore(saved);if(saved.state.messages.isNotEmpty())arrivalTracker.seed(saved.state.messages);mutable.value=mutable.value.copy(data=reducer.state,messageMode=saved.messageMode,behavior=if(saved.state.sourceStatus.taskEvents=="unsupported")ActualBehavior.seed(saved.state,SystemClock.elapsedRealtime())else Behavior(completedIds=(saved.celebratedTaskIds+saved.state.tasks.filter{it.status=="completed"}.map{it.taskId}).distinct().takeLast(512)),transport="마지막 상태 · 연결 안 됨")}catch(_:Exception){secure.clear("state.enc")}}
  viewModelScope.launch {while(isActive){delay(200);if(!foreground)continue;val s=state.value;val now=SystemClock.elapsedRealtime();if(foreground&&!s.localDemo&&receivedAt>0&&now-receivedAt>15000&&s.transport in listOf("연결됨","동기화 중","재동기화 중")){mutable.value=s.copy(transport="연결 오래됨 · 마지막 상태");scheduleReconnect()};val current=state.value;mutable.value=current.copy(behavior=if(current.localDemo)BehaviorScheduler.next(current.data,current.behavior,now,true,current.selectedTask)else ActualBehavior.next(current.data,current.desk,current.behavior,now,current.transport=="연결됨"))}}
 }
 private val visibility=VisibilityOwners();private val defaultVisibilityOwner=Any()
 fun foreground(value:Boolean,owner:Any=defaultVisibilityOwner){activityVisible=visibility.update(owner,value);if(value)nextWeatherRead=0;connectionActive(activityVisible||backgroundActive)}
 fun backgroundReceiving(value:Boolean){backgroundActive=value;connectionActive(activityVisible||backgroundActive)}
 private fun connectionActive(value:Boolean){if(foreground==value)return;foreground=value;if(!value)mediaHttp.dispatcher.cancelAll();if(value){if(!state.value.localDemo)retry()}else{connectionJob?.cancel();reconnectJob?.cancel();generation++;socket?.close(1000,"BACKGROUND");socket=null;mutable.value=state.value.copy(transport=if(state.value.localDemo)"앱내 데모 · USB 연결 아님" else "백그라운드 · 마지막 상태");persist()}}
 /** Explicit user reconnect also exits the local demo, using the existing encrypted pairing. */
 fun connectSaved(){
  if(!state.value.paired||state.value.controls.busy)return
  if(state.value.localDemo){demoJob?.cancel();mutable.value=state.value.copy(localDemo=false,data=reducer.state,behavior=if(reducer.state.sourceStatus.taskEvents=="unsupported")ActualBehavior.seed(reducer.state,SystemClock.elapsedRealtime())else Behavior(),transport="연결 안 됨",messageMode="unavailable",error="")}
  retryCount=0
  if(state.value.transport=="연결됨"&&state.value.data.sourceStatus.state !in listOf("live","manual","demo")&&state.value.controls.canSync)syncSource()else retry()
 }
 fun retry(){
  if(!foreground||state.value.localDemo)return
  reconnectJob?.cancel();connectionJob?.cancel();val token=secure.load("pairing.enc")?:return
  generation++;nextWeatherRead=0;val g=generation;socket?.cancel();socket=null;mediaHttp.dispatcher.cancelAll();decoder.clear();negotiated=false;receivedAt=0
  mutable.value=state.value.copy(transport="연결 중",error="")
  connectionJob=viewModelScope.launch{try{
   val endpoint=profile();endpoint.base();val c=controlRequest("/v1/capabilities");if(g!=generation||!foreground)return@launch
   val serverId=c["serverId"]?.jsonPrimitive?.content?:throw ControlFailure("SERVER_ID_REQUIRED");UUID.fromString(serverId)
   if(pairedServerId!=null&&pairedServerId!=serverId)throw ControlFailure("SERVER_ID_MISMATCH")
   if(pairedServerId==null){secure.save("server-id.enc",serverId);pairedServerId=serverId}
   mutable.value=state.value.copy(deskSupported=c["deskState"]?.jsonPrimitive?.booleanOrNull==true,weatherSupported=c["weather"]?.jsonPrimitive?.booleanOrNull==true,controls=state.value.controls.copy(canSend=c["presetMessages"]?.jsonPrimitive?.booleanOrNull==true,canSync=c["sync"]?.jsonPrimitive?.booleanOrNull==true,canText=c["textMessages"]?.jsonPrimitive?.booleanOrNull==true,maxTextLength=(c["maxTextLength"]?.jsonPrimitive?.intOrNull?:2000).coerceIn(1,2000)))
   socket=http.newWebSocket(Request.Builder().url(endpoint.events()).header("Authorization","Bearer $token").build(),object:WebSocketListener(){
   override fun onOpen(ws:WebSocket,response:Response){viewModelScope.launch{if(g!=generation){ws.close(1000,"SUPERSEDED");return@launch};receivedAt=SystemClock.elapsedRealtime();mutable.value=state.value.copy(transport="동기화 중");val resume=if(reducer.streamId==null)JsonNull else buildJsonObject{put("streamId",reducer.streamId);put("lastAppliedSeq",reducer.seq)};ws.send(buildJsonObject{put("schemaVersion",2);put("type","client.hello");put("clientInstanceId",clientId);put("resume",resume);put("capabilities",buildJsonArray{add("snapshot-v2");add("message-revisions-v1");add("chunk-v1");add("message-observations-v2");add("media-v1")})}.toString())}}
   override fun onMessage(ws:WebSocket,text:String){val bytes=text.toByteArray().size;if(bytes>256*1024||inbound.addAndGet(bytes)>10*1024*1024){if(bytes<=256*1024)inbound.addAndGet(-bytes);ws.cancel();viewModelScope.launch{if(g==generation){mutable.value=state.value.copy(transport="수신 큐 초과 · 마지막 상태");scheduleReconnect()}};return};viewModelScope.launch {try{if(g!=generation)return@launch;val e=decoder.decode(text)?:return@launch;val previousStream=reducer.streamId;if(e.type=="server.hello"){val h=json.decodeFromJsonElement<Hello>(e.payload);require(e.schemaVersion==2&&h.capabilities.contains("snapshot-v2"));negotiated=true;if(reducer.streamId!=e.streamId||e.seq!=reducer.seq+1)return@launch};require(negotiated);if(reducer.apply(e)){if(e.type=="state.snapshot"){lastSnapshotStream=e.streamId;lastSnapshotSeq=e.seq};if((e.type=="state.snapshot"||e.type.startsWith("message."))&&arrivalTracker.changed(reducer.state.messages))arrivals.tryEmit(Unit);receivedAt=SystemClock.elapsedRealtime();retryCount=0;mutable.value=state.value.copy(data=reducer.state,behavior=if(e.type=="state.snapshot"&&previousStream!=e.streamId&&state.value.behavior.reactionMessageId==null)if(reducer.state.sourceStatus.taskEvents=="unsupported")ActualBehavior.seed(reducer.state,SystemClock.elapsedRealtime())else Behavior("listening",since=SystemClock.elapsedRealtime(),completedIds=reducer.state.tasks.filter{it.status=="completed"}.map{it.taskId})else state.value.behavior,messageMode=if(e.type=="state.snapshot"||e.type=="source.status"||e.type.startsWith("message."))e.source.sourceMode else state.value.messageMode,transport="연결됨",lastReceived=e.timestamp,error="");ack();persist()}}catch(_:NeedsResync){resync()}catch(_:Exception){mutable.value=state.value.copy(error="이벤트 형식 오류 · 재동기화 중");resync()}finally{inbound.addAndGet(-bytes)}}}
   override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){viewModelScope.launch{if(g==generation){mutable.value=state.value.copy(transport="연결 끊김 · 마지막 상태",error=if(response?.code==401)"페어링 자격 증명을 확인해 주세요" else connectionHint());scheduleReconnect()}}}
   override fun onClosed(ws:WebSocket,code:Int,reason:String){viewModelScope.launch{if(g==generation){mutable.value=state.value.copy(transport="연결 끊김 · 마지막 상태",error=if(reason=="UPDATE_REQUIRED")"바미 앱 업데이트가 필요해요"else state.value.error);if(reason!="UPDATE_REQUIRED")scheduleReconnect()}}}
  })
  }catch(e:Exception){if(g==generation&&e !is CancellationException){mutable.value=state.value.copy(transport="연결 안 됨",error=when((e as? ControlFailure)?.code){"SERVER_ID_MISMATCH"->"다른 바미 서버예요. 별도 페어링이 필요해요.";"SERVER_ID_REQUIRED"->"무선 테스트 브리지를 사용해 주세요.";"UNAUTHORIZED"->"페어링 자격 증명을 확인해 주세요";else->if(state.value.connectionMode=="USB")"USB 연결과 새 reverse 설정을 확인해 주세요."else "Tailscale VPN과 Mac 온라인 상태·접속 주소를 확인해 주세요. (${connectionFailureCode(e)})"});if(e !is ControlFailure||e.code !in listOf("SERVER_ID_MISMATCH","SERVER_ID_REQUIRED","UNAUTHORIZED"))scheduleReconnect()}}
  }
 }
 private suspend fun controlRequest(path:String,body:JsonObject?=null):JsonObject=withContext(Dispatchers.IO){
  val token=secure.load("pairing.enc")?:throw ControlFailure("UNAUTHORIZED")
  val builder=Request.Builder().url(profile().http(path)).header("Authorization","Bearer $token")
  if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
  val call=commandHttp.newCall(builder.build());val response=suspendCancellableCoroutine<Response>{continuation->continuation.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{override fun onFailure(call:Call,e:java.io.IOException){if(continuation.isActive)continuation.resumeWithException(e)};override fun onResponse(call:Call,response:Response){if(continuation.isActive)continuation.resume(response){_,value,_->value.close()}else response.close()}})}
  response.use{response->val text=response.body?.string()?:throw ControlFailure("CONNECTION_FAILED");val result=json.parseToJsonElement(text).jsonObject;if(!response.isSuccessful)throw ControlFailure(result["code"]?.jsonPrimitive?.content?:"CONNECTION_FAILED");result}
 }
 fun sendPreset(id:String){if(id !in PresetOption.entries.map{it.id}||state.value.controls.busy)return
  if(!state.value.controls.canSend||state.value.localDemo){mutable.value=state.value.copy(controls=state.value.controls.copy(message="현재 소스에서는 메시지를 보낼 수 없어요"));return}
  val commandId=UUID.randomUUID().toString();mutable.value=state.value.copy(controls=state.value.controls.copy(sending=true,message="보내는 중…"))
  controlJob=viewModelScope.launch{secure.save("command.enc",commandId);try{val r=controlRequest("/v1/preset-messages",buildJsonObject{put("commandId",commandId);put("presetId",id)});val status=r["status"]?.jsonPrimitive?.content;val message=when(status){"confirmed"->"메시지를 보냈어요";"failed"->controlError(r["code"]?.jsonPrimitive?.content);else->"전송 결과를 확인해 주세요. 자동으로 다시 보내지 않아요."};mutable.value=state.value.copy(controls=state.value.controls.copy(message=message))}catch(e:Exception){mutable.value=state.value.copy(controls=state.value.controls.copy(message=if(e is ControlFailure)controlError(e.code)else "전송 결과를 확인해 주세요. 자동으로 다시 보내지 않아요."))}finally{secure.clear("command.enc");mutable.value=state.value.copy(controls=state.value.controls.copy(sending=false))}}
 }
 fun setPortraitMode(value:String){if(value !in listOf("scene","chat","split"))return;mutable.value=state.value.copy(portraitMode=value);prefs.edit().putString("portraitMode",value).apply()}
 fun draft(value:String){if(value.codePointCount(0,value.length)>state.value.controls.maxTextLength)return;mutable.value=state.value.copy(draft=value);draftJob?.cancel();draftJob=viewModelScope.launch{delay(250);uiSaveMutex.withLock{withContext(Dispatchers.IO){secure.save("draft.enc",value)}}}}
 fun sendText(){
  val s=state.value;val text=s.draft
  if(s.controls.busy)return
  if(!s.controls.canText||s.localDemo||s.transport!="연결됨"){mutable.value=s.copy(controls=s.controls.copy(message="현재 브리지에서는 자유 메시지를 보낼 수 없어요"));return}
  if(text.isBlank()||text.codePointCount(0,text.length)>s.controls.maxTextLength)return
  val id=UUID.randomUUID().toString();mutable.value=s.copy(controls=s.controls.copy(sending=true,message="보내는 중…"))
  controlJob=viewModelScope.launch{secure.save("command.enc",id);try{
   val r=controlRequest("/v1/messages",buildJsonObject{put("commandId",id);put("text",text)})
   val status=r["status"]?.jsonPrimitive?.content
   if(status=="confirmed"&&state.value.draft==text){draftJob?.cancelAndJoin();uiSaveMutex.withLock{withContext(Dispatchers.IO){secure.save("draft.enc","")}};mutable.value=state.value.copy(draft="")}
   mutable.value=state.value.copy(controls=state.value.controls.copy(message=when(status){"confirmed"->"메시지를 보냈어요";"failed"->controlError(r["code"]?.jsonPrimitive?.content);else->"전송 결과를 원본에서 확인해 주세요. 자동으로 다시 보내지 않아요."}))
  }catch(e:Exception){mutable.value=state.value.copy(controls=state.value.controls.copy(message=if(e is ControlFailure)controlError(e.code)else "전송 결과를 원본에서 확인해 주세요. 자동으로 다시 보내지 않아요."))}finally{secure.clear("command.enc");mutable.value=state.value.copy(controls=state.value.controls.copy(sending=false))}}
 }
 fun syncSource(){if(state.value.controls.busy)return;if(!state.value.paired||state.value.localDemo){mutable.value=state.value.copy(controls=state.value.controls.copy(message=connectionHint()));return}
  mutable.value=state.value.copy(controls=state.value.controls.copy(syncing=true,message="대화와 이미지를 동기화하고 있어요…"))
  controlJob=viewModelScope.launch{try{withTimeout(30000){if(state.value.transport!="연결됨"){retry();state.first{it.transport=="연결됨"}};val r=controlRequest("/v1/sync",buildJsonObject{put("commandId",UUID.randomUUID().toString())});val stream=r.getValue("streamId").jsonPrimitive.content;val seq=r.getValue("asOfSeq").jsonPrimitive.long;while(lastSnapshotStream!=stream||lastSnapshotSeq<seq)delay(50);mutable.value=state.value.copy(controls=state.value.controls.copy(lastSync=Instant.now().toString(),message="동기화를 마쳤어요"))}}catch(e:Exception){mutable.value=state.value.copy(controls=state.value.controls.copy(message=if(e is ControlFailure)controlError(e.code)else "동기화하지 못했어요. "+connectionHint()))}finally{mutable.value=state.value.copy(controls=state.value.controls.copy(syncing=false))}}
 }
 fun configureConnection(mode:String,address:String):Boolean {
  if(state.value.controls.busy){mutable.value=state.value.copy(error="전송·동기화가 끝난 뒤 연결을 바꿔 주세요.");return false}
  try{ConnectionProfile(mode,state.value.port,address).also{it.base()}}catch(_:Exception){mutable.value=state.value.copy(error="Tailscale 주소는 https://기기이름.tailnet.ts.net:포트 형식으로 입력해 주세요.");return false}
  if(mode==state.value.connectionMode&&address.trim()==state.value.tailscaleAddress)return true
  connectionJob?.cancel();reconnectJob?.cancel();generation++;socket?.cancel();socket=null;mediaHttp.dispatcher.cancelAll();decoder.clear()
  prefs.edit().putString("connectionMode",mode).putString("tailscaleAddress",address.trim()).apply()
  mutable.value=state.value.copy(connectionMode=mode,tailscaleAddress=address.trim(),transport="연결 안 됨",error="")
  connectSaved();return true
 }
 private fun networkChanged(){networkJob?.cancel();networkJob=viewModelScope.launch{delay(700);if(foreground&&!state.value.localDemo&&state.value.paired&&state.value.connectionMode=="Tailscale")retry()}}
 private fun scheduleReconnect(){if(!foreground||state.value.localDemo)return;if(reconnectJob?.isActive==true)return;reconnectJob=viewModelScope.launch{val base=(1000L shl retryCount.coerceAtMost(5)).coerceAtMost(30000L);retryCount++;delay((base*(0.8+Math.random()*0.4)).toLong().coerceAtMost(30000L));reconnectJob=null;retry()}}
 private fun resync(){val now=SystemClock.elapsedRealtime();if(now-requestedAt<1000)return;requestedAt=now;decoder.clear();mutable.value=state.value.copy(transport="재동기화 중");socket?.send("{\"schemaVersion\":2,\"type\":\"client.resync\"}")}
 private fun ack(){socket?.send(buildJsonObject{put("schemaVersion",2);put("type","client.ack");put("streamId",reducer.streamId);put("lastAppliedSeq",reducer.seq)}.toString())}
 private fun persist(){if(state.value.localDemo)return;cacheJob?.cancel();cacheJob=viewModelScope.launch{delay(500);val cache=CachedState(reducer.streamId,reducer.seq,reducer.state,state.value.messageMode,state.value.behavior.completedIds);withContext(Dispatchers.IO){try{secure.save("state.enc",json.encodeToString(cache))}catch(_:Exception){}}}}
 fun pair(token:String){if(!Regex("[A-Za-z0-9_-]{43}").matches(token)){mutable.value=state.value.copy(error="페어링 값 형식을 확인해 주세요");return};if(secure.load("pairing.enc")!=token){cacheJob?.cancel();secure.clear("state.enc");secure.clear("server-id.enc");pairedServerId=null;arrivalTracker.clear();media.clear();reducer.restore(CachedState(null,0,AppState()));mutable.value=state.value.copy(data=reducer.state,behavior=Behavior())};secure.save("pairing.enc",token);demoJob?.cancel();mutable.value=state.value.copy(paired=true,localDemo=false);retry()}
 fun forget(){connectionJob?.cancel();networkJob?.cancel();generation++;socket?.cancel();reconnectJob?.cancel();demoJob?.cancel();secure.clear("pairing.enc");secure.clear("server-id.enc");pairedServerId=null;media.clear();mutable.value=state.value.copy(paired=false,localDemo=false,controls=ControlState(),transport="페어링 해제 · 마지막 상태")}
 fun setWakeOnMessage(value:Boolean){mutable.value=state.value.copy(wakeOnMessage=value);prefs.edit().putBoolean("wakeOnMessage",value).apply()}
 fun clearHistory(){arrivalTracker.clear();cacheJob?.cancel();secure.clear("state.enc");media.clear();reducer.restore(CachedState(null,0,AppState()));mutable.value=state.value.copy(data=reducer.state,behavior=Behavior());retry()}
 private val sparkIds=secure.load("spark-ids.enc")?.split("\n")?.filter{it.isNotBlank()}?.toMutableSet()?:mutableSetOf<String>()
 fun claimSpark(id:String):Boolean{if(id in sparkIds)return false;sparkIds.add(id);while(sparkIds.size>512)sparkIds.remove(sparkIds.first());val snapshot=sparkIds.joinToString("\n");viewModelScope.launch{uiSaveMutex.withLock{withContext(Dispatchers.IO){secure.save("spark-ids.enc",snapshot)}}};return true}
 fun speechVisible(){val s=state.value;if(s.data.sourceStatus.taskEvents!="unsupported")return;mutable.value=s.copy(behavior=ConversationReaction.speechVisible(s.behavior,SystemClock.elapsedRealtime()))}
 fun selectTask(id:String?){mutable.value=state.value.copy(selectedTask=id)}
 fun settings(zone:String,font:Int,reduced:Boolean,paused:Boolean,awake:Boolean,night:Boolean,port:Int){try{ZoneId.of(zone);require(port in 1024..65535&&port !in listOf(8765,8787))}catch(_:Exception){mutable.value=state.value.copy(error="시간대 또는 포트를 확인해 주세요");return};val reconnect=port!=state.value.port;mutable.value=state.value.copy(zone=zone,font=font.coerceIn(14,30),reduced=reduced,paused=paused,awake=awake,night=night,port=port);prefs.edit().putString("zone",zone).putInt("font",state.value.font).putBoolean("reduced",reduced).putBoolean("paused",paused).putBoolean("awake",awake).putBoolean("night",night).putInt("port",port).apply();if(reconnect)retry()}
 fun demo(){connectionJob?.cancel();networkJob?.cancel();generation++;socket?.cancel();reconnectJob?.cancel();demoJob?.cancel();val now=Instant.now().toString();mutable.value=state.value.copy(localDemo=true,messageMode="mock",transport="앱내 데모 · USB 연결 아님",error="",data=AppState(sourceStatus=SourceStatus("demo",now,"앱내 데모 · 실제 dots 대화가 아니에요"),messages=listOf(Message("local-user","demo",role="user",revision=1,content="바미야, 오늘 할 일을 정리해 줘",state="complete"))));demoJob=viewModelScope.launch{val text="형아, 중요한 일부터 하나씩 해 보세요. 🌙\n잠깐 쉬는 시간도 챙겨 주세요.";val glyphs=text.codePoints().toArray();while(isActive){val t=Task("local-task","demo","running","reading","형아, 자료를 살펴보고 있어요",50,Instant.now().toString());mutable.value=state.value.copy(data=state.value.data.copy(tasks=listOf(t)));delay(3500);mutable.value=state.value.copy(data=state.value.data.copy(tasks=listOf(t.copy(phase="working",publicSummary="형아, 오늘 할 일을 정리하고 있어요"))));delay(3500);for(i in glyphs.indices step 3){mutable.value=state.value.copy(data=state.value.data.copy(tasks=listOf(t.copy(phase="responding")),messages=state.value.data.messages.filter{it.messageId!="local-reply"}+Message("local-reply","demo",role="assistant",revision=1,content=String(glyphs,0,(i+3).coerceAtMost(glyphs.size)))));delay(180)};mutable.value=state.value.copy(data=state.value.data.copy(tasks=listOf(t.copy(status="completed",phase="none",updatedAt=Instant.now().toString())),messages=state.value.data.messages.map{if(it.messageId=="local-reply")it.copy(state="complete")else it}));delay(16000)}}}
 override fun onCleared(){networkObserver.close();connectionJob?.cancel();networkJob?.cancel();socket?.cancel();http.dispatcher.executorService.shutdown();mediaHttp.dispatcher.executorService.shutdown();commandHttp.dispatcher.executorService.shutdown();super.onCleared()}
}
