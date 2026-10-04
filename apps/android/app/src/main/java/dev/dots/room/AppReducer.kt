package dev.dots.room

import kotlinx.serialization.json.*
import java.util.UUID
import java.time.Instant

class NeedsResync(message:String):Exception(message)
class AppReducer {
 var state=AppState(); private set
 var streamId:String?=null; private set
 var seq=0L; private set
 private val ids=LinkedHashSet<String>()
 fun restore(cache:CachedState){validate(cache.state);ids.clear();state=cache.state;streamId=cache.streamId;seq=cache.seq}
 fun apply(e:Event):Boolean {
  require(e.schemaVersion==2 && e.seq>0 && e.seq<=9007199254740991L)
  UUID.fromString(e.streamId);UUID.fromString(e.eventId);Instant.parse(e.timestamp)
  require(e.source.sourceMode in listOf("live","manual","mock"))
  if(e.type=="state.snapshot") {
   val s=protocolJson.decodeFromJsonElement<Snapshot>(e.payload);require(s.asOfSeq==e.seq);UUID.fromString(s.snapshotId)
   if(streamId==e.streamId&&e.seq<=seq)return false
   validate(s.state());state=s.state();streamId=e.streamId;seq=e.seq;ids.clear();ids.add(e.eventId);return true
  }
  if(e.streamId!=streamId)throw NeedsResync("STREAM_CHANGED")
  if(e.seq<=seq)return false
  if(e.seq!=seq+1||ids.contains(e.eventId))throw NeedsResync("SEQ_GAP")
  if(e.type.startsWith("message.")){val id=e.payload["messageId"]?.jsonPrimitive?.content;val old=state.messages.find{it.messageId==id};if(old!=null)require(old.conversationId==e.conversationId&&old.role==e.payload["role"]?.jsonPrimitive?.content&&old.taskId==e.taskId)}
  if(e.type.startsWith("task.")){state.tasks.find{it.taskId==e.taskId}?.let{require(it.conversationId==e.conversationId)}}
  var next=state
  when(e.type){
   "server.hello"->protocolJson.decodeFromJsonElement<Hello>(e.payload)
   "heartbeat"->require(e.payload.isEmpty())
   "server.error"->protocolJson.decodeFromJsonElement<ServerError>(e.payload)
   "source.status"->next=state.copy(sourceStatus=protocolJson.decodeFromJsonElement(e.payload))
   "task.upsert"->{val p=protocolJson.decodeFromJsonElement<TaskPayload>(e.payload);val t=Task(requireNotNull(e.taskId),requireNotNull(e.conversationId),p.status,p.phase,p.publicSummary,p.priority,p.updatedAt);next=state.copy(tasks=state.tasks.filter{it.taskId!=t.taskId}+t)}
   "task.remove"->{require(e.payload.isEmpty()&&e.taskId!=null&&e.conversationId!=null);next=state.copy(tasks=state.tasks.filter{it.taskId!=e.taskId})}
   "message.observed"->{val p=protocolJson.decodeFromJsonElement<Observed>(e.payload);val old=state.messages.find{it.messageId==p.messageId};if(old==null||p.revision>old.revision)next=upsert(Message(p.messageId,requireNotNull(e.conversationId),e.taskId,p.role,p.revision,p.content,"observed",observation=p.observation,images=p.images))}
   "message.start"->{val p=protocolJson.decodeFromJsonElement<Start>(e.payload);require(p.revision>0&&p.role in listOf("user","assistant"));val old=state.messages.find{it.messageId==p.messageId};if(old==null||p.revision>old.revision)next=upsert(Message(p.messageId,requireNotNull(e.conversationId),e.taskId,p.role,p.revision))}
   "message.delta"->{val p=protocolJson.decodeFromJsonElement<Delta>(e.payload);require(p.revision>0&&p.deltaIndex>=0&&p.text.length<=128*1024);val m=state.messages.find{it.messageId==p.messageId};if(m!=null&&p.revision<m.revision)Unit else if(m==null||p.revision!=m.revision)throw NeedsResync("DELTA_REVISION") else if(m.state=="complete"||p.deltaIndex<m.nextDelta)Unit else {if(p.deltaIndex!=m.nextDelta)throw NeedsResync("DELTA_GAP");require(p.role==m.role&&e.conversationId==m.conversationId);next=upsert(m.copy(content=m.content+p.text,nextDelta=m.nextDelta+1))}}
   "message.completed","message.upsert"->{val p=protocolJson.decodeFromJsonElement<Completed>(e.payload);require(p.revision>0);Instant.parse(p.completedAt);val old=state.messages.find{it.messageId==p.messageId};if(old==null||p.revision>old.revision||(p.revision==old.revision&&old.state!="complete"))next=upsert(Message(p.messageId,requireNotNull(e.conversationId),e.taskId,p.role,p.revision,p.content,"complete",old?.nextDelta?:0,p.completedAt))}
   "usage.update"->{val p=protocolJson.decodeFromJsonElement<UsageUpdate>(e.payload);next=state.copy(usage=p.usage,quotas=p.quotas)}
   else->throw IllegalArgumentException("UNKNOWN_EVENT")
  }
  val messages=next.messages.toMutableList();var bytes=messageArrayBytes(messages);var truncated=next.history.truncated
  while(messages.size>500||bytes>5*1024*1024){val removed=messages.removeAt(0);bytes-=messageBytes(removed)+(if(messages.isEmpty())0 else 1);truncated=true}
  next=next.copy(messages=messages,history=History(truncated,messages.size));validate(next)
  state=next;seq=e.seq;ids.add(e.eventId);if(ids.size>10000)ids.remove(ids.first());return true
 }
 private fun upsert(m:Message):AppState {require(m.content.toByteArray().size<=2*1024*1024);val i=state.messages.indexOfFirst{it.messageId==m.messageId};val a=state.messages.toMutableList();if(i<0)a.add(m)else a[i]=m;return state.copy(messages=a)}
 private fun messageBytes(m:Message)=protocolJson.encodeToString(Message.serializer(),m).toByteArray().size
 private fun messageArrayBytes(messages:List<Message>)=2+messages.sumOf{messageBytes(it)}+(messages.size-1).coerceAtLeast(0)
 private fun validateObservation(m:Message){m.observation?.let{o->require(o.origin=="browser-ui"&&o.sourceMessageId==m.messageId&&o.revisionOrigin=="local-observation"&&o.finality=="unknown"&&o.statusProvenance=="unknown"&&m.state=="observed");Instant.parse(o.observedAt);o.sourceTimestamp?.let{Instant.parse(it)};require(o.sourceRevision==null||o.sourceRevision>0)};require(m.images.size<=8);m.images.forEach{i->require(i.status in listOf("pending","available","error","expired")&&i.alt.length<=1000&&(i.assetId==null||Regex("[a-f0-9]{64}").matches(i.assetId))&&(i.width==null||i.width in 1..1024)&&(i.height==null||i.height in 1..1024));if(i.status=="error")require(!i.error.isNullOrBlank());if(i.status=="available")require(i.assetId!=null&&i.width!=null&&i.height!=null)}}
 private fun validate(s:AppState){require(s.tasks.size<=256&&s.messages.size<=500&&messageArrayBytes(s.messages)<=5*1024*1024&&s.quotas.size<=32);require(s.tasks.map{it.taskId}.distinct().size==s.tasks.size&&s.messages.map{it.messageId}.distinct().size==s.messages.size);require(s.sourceStatus.state in listOf("live","unavailable","stale","manual","demo"));s.tasks.forEach{require(it.status in listOf("queued","running","waiting_user","completed","failed","canceled","unknown")&&it.phase in listOf("reading","thinking","working","responding","none")&&it.priority in 0..100);require(protocolJson.encodeToJsonElement(it.publicSummary).toString().toByteArray().size<=4000);Instant.parse(it.updatedAt)};s.messages.forEach{validateObservation(it);require(it.role in listOf("user","assistant")&&it.revision>0&&it.state in listOf("streaming","complete","observed")&&it.content.toByteArray().size<=2*1024*1024)};s.quotas.forEach{require(it.knowledge in listOf("known","unknown")&&it.freshness in listOf("fresh","stale","manual","unknown")&&(it.usedPercent==null||it.usedPercent in 0.0..100.0)&&(it.windowDurationMins==null||it.windowDurationMins>0));it.resetAt?.let{time->Instant.parse(time)}};listOf(s.usage.inputTokens,s.usage.outputTokens).forEach{require(it.knowledge in listOf("known","unknown")&&it.freshness in listOf("fresh","stale","manual","unknown")&&(it.value==null||it.value>=0))}}
}
