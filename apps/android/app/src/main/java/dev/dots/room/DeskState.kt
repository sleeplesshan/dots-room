package dev.dots.room
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class ExecutionTask(val taskId:String,val state:String,val phase:String="none",val origin:String,val observedAt:String,val completionEventId:String?=null)
@Serializable data class ExecutionState(val knowledge:String="unknown",val source:String="unavailable",val observedAt:String?=null,val freshness:String="unknown",val reason:String="REAL_STATE_UNAVAILABLE",val tasks:List<ExecutionTask> = emptyList())
@Serializable data class NoteItem(val id:String,val text:String)
@Serializable data class NoteSection(val id:String,val title:String,val items:List<NoteItem> = emptyList())
@Serializable data class WorkNotes(val pageId:String?=null,val url:String?=null,val status:String="unconfigured",val revision:Int=0,val sections:List<NoteSection> = emptyList(),val sourceUpdatedAt:String?=null,val observedAt:String?=null,val lastSuccessAt:String?=null,val freshness:String="unknown",val error:String="NOT_CONFIGURED",val writer:String="user",val declaredUpdateText:String="")
@Serializable data class DeskState(val schemaVersion:Int=1,val execution:ExecutionState=ExecutionState(),val notes:WorkNotes=WorkNotes()){
 fun validate():DeskState {
  require(schemaVersion==1&&execution.tasks.size<=64&&execution.knowledge in listOf("known","unknown"))
  require(execution.source in listOf("codex-app-server","browser-ui","fixture","unavailable")&&execution.freshness in listOf("fresh","stale","unknown"))
  require(execution.reason.length<=200)
  execution.tasks.forEach{require(it.taskId.length in 1..200&&it.state in listOf("working","waiting_user","blocked","responding","idle","completed","unknown")&&it.phase in listOf("none","reading","working","responding")&&it.origin in listOf("codex-app-server","fixture")&&(it.completionEventId?.length?:0)<=200);Instant.parse(it.observedAt)}
  require(notes.status in listOf("ready","empty","error","unconfigured")&&notes.revision>=0&&notes.writer=="user"&&notes.sections.size<=4)
  require(notes.freshness in listOf("fresh","stale","unknown")&&notes.error.length<=120&&notes.declaredUpdateText.length<=200)
  notes.url?.let{require(Regex("https://chatgpt\\.com/space/page_[a-f0-9]{32}").matches(it))}
  notes.pageId?.let{require(Regex("page_[a-f0-9]{32}").matches(it)&&notes.url=="https://chatgpt.com/space/$it")}
  notes.sections.forEach{require(it.id in listOf("doing","waiting","done","free")&&it.title.length<=80&&it.items.size<=100);it.items.forEach{n->require(n.text.length<=8000&&n.id.length<=100)}}
  require(notes.sections.sumOf{it.items.sumOf{n->n.text.toByteArray().size}}<=65536)
  listOfNotNull(execution.observedAt,notes.sourceUpdatedAt,notes.observedAt,notes.lastSuccessAt).forEach{Instant.parse(it)}
  return this
 }
}
fun recentObservation(time:String?,now:Instant)=time!=null&&runCatching{java.time.Duration.between(Instant.parse(time),now).seconds in -5..15}.getOrDefault(false)
fun runtimeState(e:ExecutionState,connected:Boolean,now:Instant=Instant.now()):String {
 if(!connected||e.knowledge!="known"||e.freshness!="fresh"||!recentObservation(e.observedAt,now))return "unknown"
 val tasks=e.tasks.filter{recentObservation(it.observedAt,now)}
 return when{tasks.any{it.state=="blocked"}->"blocked";tasks.any{it.state=="waiting_user"}->"waiting_user";tasks.any{it.state=="responding"}->"responding";tasks.any{it.state=="working"}->"working";tasks.isNotEmpty()&&tasks.all{it.state in listOf("idle","completed")}->"idle";else->"unknown"}
}
fun runtimeLabel(id:String)=when(id){"working"->"작업 중";"waiting_user"->"형아 확인·승인 대기";"blocked"->"오류로 막힘";"responding"->"응답 중";"idle"->"유휴";else->"실제 상태 미확인"}
/** State is factual; poses are a separate scheduler. No conversation-word inference. */
object ActualBehavior {
 fun seed(s:AppState,now:Long)=Behavior("listening",since=now,reactionMessageId=ConversationReaction.latest(s)?.messageId)
 fun next(s:AppState,desk:DeskState,old:Behavior,now:Long,connected:Boolean,wall:Instant=Instant.now()):Behavior {
  val e=desk.execution;val state=runtimeState(e,connected,wall);val tasks=if(state=="unknown")emptyList()else e.tasks.filter{recentObservation(it.observedAt,wall)};val live=tasks.filter{it.state in listOf("working","responding")};val warnings=tasks.count{it.state in listOf("waiting_user","blocked")}
  val latest=ConversationReaction.latest(s);var base=old
  // New collected reply -> one visual speech, revisions/history/recollection never restart it.
  if(connected&&latest!=null&&latest.messageId!=old.reactionMessageId){base=old.copy(reactionMessageId=latest.messageId,reactionRevision=latest.revision,requestMessageId=null,answering=latest.role=="assistant",speechReady=false,reactionUntil=Long.MAX_VALUE);if(latest.role=="assistant")base=base.copy(action="talk",since=now,ambientTarget=null,lifeSince=-1,lifeChoice="")}
  if(state=="unknown"&&(!connected||s.sourceStatus.state in listOf("unavailable","stale")))return base.copy(action="listening",since=if(base.action=="listening")base.since else now,ambientTarget="stay",running=0,warnings=warnings,answering=false,requestMessageId=null,lifeSince=-1,lifeChoice="",pendingAction=null,pendingTask=null)
  if(state in listOf("blocked","waiting_user"))return base.copy(action=if(state=="blocked")"confused"else "listening",since=if(base.action==if(state=="blocked")"confused"else "listening")base.since else now,ambientTarget=null,running=live.size,warnings=warnings,answering=false,lifeSince=-1,lifeChoice="",pendingAction=null,pendingTask=null)
  if(base.answering&&base.action=="talk"&&(!base.speechReady||now<base.reactionUntil))return base.copy(running=live.size,warnings=warnings)
  val completed=tasks.firstOrNull{it.state=="completed"&&it.completionEventId!=null&&it.completionEventId !in base.completedIds}
  if(completed!=null)return base.copy(action="celebrate",since=now,lastCompletion=completed.taskId,completedIds=(base.completedIds+completed.completionEventId!!).takeLast(512),running=live.size,warnings=warnings,answering=false,ambientTarget=null,lifeSince=-1,lifeChoice="")
  if(base.action=="celebrate"&&now-base.since<900&&state!="unknown")return base.copy(running=live.size,warnings=warnings)
  val chosen=live.firstOrNull{it.taskId==base.taskId}?:live.firstOrNull()
  if(state in listOf("idle","unknown"))return LifeDirector.next(base.copy(taskId=null,running=live.size,warnings=warnings),now,state=="idle")
  val action=if(state=="responding")"talk"else if(chosen?.phase=="reading")"read"else "work"
  val changed=action!=base.action||chosen?.taskId!=base.taskId
  if(changed){if(base.pendingAction!=action||base.pendingTask!=chosen?.taskId)return base.copy(pendingAction=action,pendingTask=chosen?.taskId,pendingSince=now,running=live.size,warnings=warnings,answering=false,lifeSince=-1,lifeChoice="");if(now-base.pendingSince<1000)return base.copy(running=live.size,warnings=warnings,answering=false,lifeSince=-1,lifeChoice="")}
  return base.copy(action=action,taskId=chosen?.taskId,since=if(action==base.action&&base.ambientTarget==null)base.since else now,running=live.size,warnings=warnings,answering=false,ambientTarget=null,idleSince=-1,pendingAction=null,pendingTask=null,lifeSince=-1,lifeChoice="")
 }
}
