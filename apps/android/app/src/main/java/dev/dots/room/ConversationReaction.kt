package dev.dots.room

/** Local visual reactions only. Never creates tasks, message completion, or source facts. */
object ConversationReaction {
 fun pose(m:Message):String {
  val tags=m.images.joinToString(" "){it.alt}.lowercase()
  val imageRules=listOf("sleep" to "sleep","read" to "read","work" to "work","think" to "think","confused" to "confused","sad" to "confused","joy" to "happy","happy" to "happy","listen" to "listening")
  imageRules.firstOrNull{(tag,_)->Regex("bami[-_ ]$tag(?:[-_ .]|$)").containsMatchIn(tags)}?.let{return it.second}
  val text=m.content.takeLast(8000).lowercase()
  if(listOf("안 졸", "잠 못", "못 자", "잠이 안").any{text.contains(it)})return "confused"
  if(listOf("잘 자", "잘자", "자러", "잠들", "졸려", "졸리", "sleepy", "good night").any{text.contains(it)})return "sleep"
  if(listOf("책 읽", "책을 읽", "독서", "읽어", "reading").any{text.contains(it)})return "read"
  if(listOf("컴퓨터", "코딩", "작업해", "작업 중", "일하는", "working", "coding").any{text.contains(it)})return "work"
  if(listOf("생각", "고민", "궁금", "thinking", "🤔").any{text.contains(it)})return "think"
  if(listOf("슬프", "속상", "걱정", "아프", "미안", "힘들", "모르겠", "당황", "confused", "sad", "😢", "😭").any{text.contains(it)})return "confused"
  if(listOf("기쁘지 않", "안 기뻐", "행복하지 않", "안 행복", "좋지 않", "안 좋아").any{text.contains(it)})return "confused"
  if(listOf("고마", "좋아", "기뻐", "행복", "축하", "사랑", "신나", "반가", "happy", "joy", "😊", "🥰", "💙", "🎉").any{text.contains(it)})return "happy"
  return if(m.role=="assistant"&&m.content.isNotBlank())"talk"else "listening"
 }
 // DOM order is conversation order. A late image/revision in an older bubble is not a new reply.
 fun latest(s:AppState):Message?=s.messages.lastOrNull{it.observation?.origin=="browser-ui"&&(it.content.isNotBlank()||it.images.isNotEmpty())}
 private fun fingerprint(m:Message):String=java.security.MessageDigest.getInstance("SHA-256").digest((m.content+"\u0000"+m.images.joinToString("\u0000"){it.alt}).toByteArray()).joinToString(""){"%02x".format(it)}
 fun isRequest(m:Message):Boolean {
  if(m.role!="user"||m.content.isBlank())return false
  val text=m.content.trim().lowercase()
  if(listOf("?","？","해줘","해 줘","알려","찾아","검색","조사","읽어","해주세요","부탁","please","can you","could you").any{text.contains(it)})return true
  if(pose(m) in listOf("sleep","happy","confused"))return false
  return !Regex("^(바미야[!,. ]*)?(안녕|하이|hi|hello|바미야)[!,.~ 💙]*$").matches(text)
 }
 private fun acknowledgement(m:Message):Boolean {
  val text=m.content.lowercase().replace(" ","")
  return listOf("확인할게","확인해볼게","살펴볼게","알아볼게","찾아볼게","검토할게","진행할게","해볼게","검색해볼게","잠시기다려","i’llcheck","i'llcheck","letmecheck").any{text.contains(it)}
 }
 private fun busy(old:Behavior,now:Long):Behavior {
  val phase=(now-old.requestSince).coerceAtLeast(0)%24000
  val action=when {phase<8000->"read";phase<12000->"think";else->"work"}
  return old.copy(action=action,since=if(old.action==action)old.since else now,ambientTarget=null)
 }
 /** Local decoration only; quiet time never changes source tasks or message completion. */
 private fun ambient(old:Behavior,now:Long):Behavior {
  val start=old.idleSince.takeIf{it>=0}?:now
  val phase=(now-start).coerceAtLeast(0)%160000
  val (action,target)=when {
   phase<20000->"idle" to "left-lane"
   phase<40000->"read" to "read"
   phase<60000->"idle" to "center"
   phase<80000->"idle" to "window"
   else->"sleep" to "sleep"
  }
  return old.copy(action=action,since=if(action==old.action&&target==old.ambientTarget)old.since else now,idleSince=start,ambientTarget=target,requestMessageId=null,answering=false)
 }
 fun speechVisible(old:Behavior,now:Long):Behavior=if(old.action=="talk"&&!old.speechReady)old.copy(speechReady=true,since=now,reactionUntil=now+5000)else old
 private fun advance(old:Behavior,now:Long):Behavior {
  if(old.requestMessageId!=null&&!old.answering)return busy(old,now)
  if(old.action=="talk"&&now>=old.reactionUntil){
   // A short acknowledgement keeps the local computer routine longer than a full answer.
   return old.copy(action="work",since=now,workUntil=now+old.workUntil,ambientTarget=null)
  }
  if(old.answering&&old.action=="work")return if(now<old.workUntil)old else ambient(old.copy(idleSince=now),now)
  if(old.ambientTarget!=null||now>=old.reactionUntil&&old.action!="sleep")return ambient(old,now)
  return old
 }
 fun next(s:AppState,old:Behavior,now:Long,connected:Boolean):Behavior {
  val m=latest(s)
  if(!connected||s.sourceStatus.state !in listOf("live","demo","manual"))return old.copy(action="listening",taskId=null,running=0,warnings=0,reactionUntil=now+20000,requestMessageId=null,answering=false,ambientTarget=null,idleSince=-1)
  if(m==null)return ambient(old,now)
  val signature=fingerprint(m)
  val newBubble=m.messageId!=old.reactionMessageId
  val changed=signature!=old.reactionFingerprint
  if(newBubble||changed){
   // Streaming edits and delayed images in the same replying bubble never reset the speech clock.
   if(!newBubble&&old.answering)return advance(old.copy(reactionRevision=m.revision,reactionFingerprint=signature),now)
   val requesting=isRequest(m)
   val replying=m.role=="assistant"&&(old.requestMessageId!=null||pose(m)=="talk"||acknowledgement(m))
   val action=if(replying)"talk"else if(requesting)"read"else pose(m)
   return old.copy(action=action,taskId=null,running=0,warnings=0,since=now,reactionMessageId=m.messageId,reactionRevision=m.revision,reactionFingerprint=signature,reactionUntil=if(requesting||action=="sleep"||replying)Long.MAX_VALUE else now+20000,requestMessageId=if(requesting)m.messageId else if(replying)old.requestMessageId else null,requestSince=if(requesting)now else old.requestSince,answering=replying,speechReady=false,workUntil=if(acknowledgement(m))90000 else 30000,idleSince=-1,ambientTarget=null)
  }
  return advance(old.copy(reactionRevision=m.revision),now)
 }
 fun seed(s:AppState,now:Long,completedIds:List<String> = emptyList()):Behavior {
  val m=latest(s)?:return Behavior("listening",since=now,reactionUntil=now+20000,completedIds=completedIds)
  val action=pose(m).let{if(it=="talk")"listening"else it}
  return Behavior(action,since=now,completedIds=completedIds,reactionMessageId=m.messageId,reactionRevision=m.revision,reactionFingerprint=fingerprint(m),reactionUntil=if(action=="sleep")Long.MAX_VALUE else now+20000)
 }
}
