package dev.dots.room

data class Behavior(val action:String="idle",val taskId:String?=null,val warnings:Int=0,val running:Int=0,val since:Long=0,val lastCompletion:String?=null,val completedIds:List<String> = emptyList(),val reactionMessageId:String?=null,val reactionRevision:Int=0,val reactionUntil:Long=0,val requestMessageId:String?=null,val requestSince:Long=0,val answering:Boolean=false,val reactionFingerprint:String?=null,val speechReady:Boolean=false,val workUntil:Long=0,val idleSince:Long=-1,val ambientTarget:String?=null,val pendingAction:String?=null,val pendingTask:String?=null,val pendingSince:Long=0,val lifeSince:Long=-1,val lifeNextAt:Long=0,val lifeStep:Int=0,val lifeChoice:String="")
object BehaviorScheduler {
 fun next(s:AppState,old:Behavior,now:Long,connected:Boolean,selected:String?=null):Behavior {
  if(s.sourceStatus.taskEvents=="unsupported")return ConversationReaction.next(s,old,now,connected)
  val running=s.tasks.filter{it.status in listOf("running","queued")};val warnings=s.tasks.count{it.status in listOf("waiting_user","failed","unknown")}
  val seen=old.completedIds.filter{it !in running.map{task->task.taskId}}.toMutableList()
  val existingCompleted=s.tasks.filter{it.status=="completed"}.map{it.taskId}.toSet()
  fun retained():List<String>{while(seen.size>512){val index=seen.indexOfFirst{it !in existingCompleted};if(index<0)break;seen.removeAt(index)};return seen.toList()}
  if(!connected||s.sourceStatus.state in listOf("unavailable","stale"))return old.copy(action=if(old.action in listOf("idle","sleep","walk"))"listening" else old.action,warnings=warnings,running=running.size,completedIds=retained())
  if(old.action=="celebrate"&&now-old.since<900)return old.copy(warnings=warnings,running=running.size,completedIds=retained())
  val completed=s.tasks.filter{it.status=="completed"&&it.taskId !in seen}.maxByOrNull{it.updatedAt}
  if(completed!=null){seen.add(completed.taskId);return Behavior("celebrate",null,warnings,running.size,now,completed.taskId,retained())}
  val ranked=running.sortedWith(compareByDescending<Task>{it.priority}.thenByDescending{it.updatedAt})
  val chosen=ranked.find{it.taskId==selected}?:ranked.find{it.taskId==old.taskId&&now-old.since<4000}?:ranked.getOrNull(((now/8000)%ranked.size.coerceAtLeast(1)).toInt())
  val action=if(chosen!=null)when(chosen.phase){"reading"->"read";"thinking"->"think";"responding"->"talk";else->"work"}else if(s.tasks.any{it.status in listOf("waiting_user","unknown")})"listening" else if(s.tasks.any{it.status=="failed"})"confused" else if(old.action=="sleep"||now-old.since>180000)"sleep" else "idle"
  return if(action==old.action&&chosen?.taskId==old.taskId)old.copy(warnings=warnings,running=running.size,completedIds=retained()) else Behavior(action,chosen?.taskId,warnings,running.size,now,old.lastCompletion,retained())
 }
}
