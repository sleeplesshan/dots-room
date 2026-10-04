package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
class ConversationReactionTest {
 private fun m(text:String,role:String="assistant",id:String="m",revision:Int=1,alt:String="",observedAt:String="2026-10-01T00:00:00Z")=Message(id,"browser-dots-local",role=role,revision=revision,content=text,state="observed",observation=Observation("browser-ui",id,null,null,observedAt,null,"local-observation","unknown","unknown"),images=if(alt.isBlank())emptyList()else listOf(MediaAttachment(null,"pending",null,null,alt,null)))
 private fun s(vararg messages:Message)=AppState(sourceStatus=SourceStatus("live",taskEvents="unsupported"),messages=messages.toList())
 @Test fun contentAndImageCuesDriveDistinctPoses(){for((text,action)in listOf("책을 읽어 줄게" to "read","컴퓨터로 코딩해" to "work","생각 중이야 🤔" to "think","속상하고 걱정돼" to "confused","고마워 💙" to "happy","잘 자 형아" to "sleep","오늘 이야기를 해줄게" to "talk")){assertEquals(action,ConversationReaction.pose(m(text)))};assertEquals("happy",ConversationReaction.pose(m("",alt="bami-joy-120.png")));assertEquals("listening",ConversationReaction.pose(m("안녕","user")))}
 @Test fun negativesDoNotTriggerJoyOrSleep(){assertEquals("confused",ConversationReaction.pose(m("잠이 안 와")));assertEquals("confused",ConversationReaction.pose(m("안 행복해")))}
 @Test fun reactionsNeverCreateCompletionAndDuplicatesDoNotRestart(){val state=s(m("고마워"));val first=BehaviorScheduler.next(state,Behavior(),100,true);assertEquals("happy",first.action);assertNull(first.lastCompletion);assertTrue(first.completedIds.isEmpty());val duplicate=BehaviorScheduler.next(state,first,200,true);assertEquals(first.since,duplicate.since);assertEquals("idle",BehaviorScheduler.next(state,first,21000,true).action);assertTrue(state.tasks.isEmpty());assertNull(state.messages.single().completedAt)}
 @Test fun revisionUpdatesAndNewUserMessagesInterruptPriorPose(){val first=BehaviorScheduler.next(s(m("잘 자")),Behavior(),0,true);assertEquals("sleep",first.action);val revised=BehaviorScheduler.next(s(m("생각 중",revision=2)),first,100,true);assertEquals("think",revised.action);val next=BehaviorScheduler.next(s(m("생각 중",revision=2),m("바미야","user","user",observedAt="2026-10-01T00:00:01Z")),revised,200,true);assertEquals("listening",next.action)}
 @Test fun editedEarlierUserDoesNotRestartAnAnsweredRequest(){val state=s(m("컴퓨터로 코딩해","user","earlier",revision=2,observedAt="2026-10-01T00:00:02Z"),m("","assistant","image",alt="bami-joy-120.png"));assertNull(ConversationReaction.next(state,Behavior(),0,true).requestMessageId)}
 @Test fun staleAndReconnectDoNotReplayConversationMotion(){val state=s(m("고마워"));val first=ConversationReaction.next(state,Behavior(),0,true);val stale=ConversationReaction.next(state.copy(sourceStatus=SourceStatus("stale",taskEvents="unsupported")),first,100,false);assertEquals("listening",stale.action);assertEquals("listening",ConversationReaction.next(state,stale,200,true).action)}
 @Test fun snapshotSeedDoesNotSpeakHistoricalText(){assertEquals("listening",ConversationReaction.seed(s(m("옛날 이야기")),0).action);assertEquals("happy",ConversationReaction.seed(s(m("",alt="bami-joy-120.png")),0).action)}
 @Test fun requestsCycleThroughResearchUntilAnActualVisibleReply(){val request=m("내일 날씨를 찾아줘","user","u");val state=s(request);val first=ConversationReaction.next(state,Behavior(),100,true);assertEquals("read",first.action);assertEquals("u",first.requestMessageId);assertEquals("think",ConversationReaction.next(state,first,8500,true).action);assertEquals("work",ConversationReaction.next(state,first,13000,true).action);assertEquals("read",ConversationReaction.next(state,first,25000,true).action);val reply=s(request,m("찾아봤어","assistant","answer",observedAt="2026-10-01T00:00:02Z"));val answer=ConversationReaction.next(reply,first,14000,true);assertEquals("talk",answer.action);assertTrue(answer.answering);assertTrue(answer.completedIds.isEmpty());assertTrue(reply.tasks.isEmpty());assertEquals("talk",ConversationReaction.next(reply,answer,29000,true).action);val visible=ConversationReaction.speechVisible(answer,30000);assertEquals("work",ConversationReaction.next(reply,visible,35000,true).action)}
 @Test fun streamingEditsKeepReplyAnimationAndNewRequestsInterrupt(){val request=m("책을 찾아줘","user","u");val first=ConversationReaction.next(s(request),Behavior(),0,true);val a=m("첫 답변","assistant","a",observedAt="2026-10-01T00:00:02Z");val talking=ConversationReaction.next(s(request,a),first,100,true);val edit=ConversationReaction.next(s(request,a.copy(content="긴 답변",revision=2)),talking,400,true);assertEquals("talk",edit.action);assertEquals(talking.since,edit.since);val next=ConversationReaction.next(s(request,a,m("다른 것도 조사해줘","user","u2",observedAt="2026-10-01T00:00:03Z")),edit,500,true);assertEquals("read",next.action);assertFalse(next.answering);assertEquals("u2",next.requestMessageId)}
 @Test fun oldAssistantImageDoesNotAnswerANewerRequest(){val old=m("옛 응답","assistant","old",observedAt="2026-10-01T00:00:00Z");val request=m("새 자료 찾아줘","user","u",observedAt="2026-10-01T00:00:01Z");val first=ConversationReaction.next(s(old,request),Behavior(),0,true);val edited=old.copy(revision=2,observation=old.observation!!.copy(observedAt="2026-10-01T00:00:02Z"));val next=ConversationReaction.next(s(edited,request),first,100,true);assertFalse(next.answering);assertEquals("u",next.requestMessageId)}

 @Test fun acknowledgementSpeaksForFiveVisibleSecondsThenWorksWithoutRevisionReset(){
  val request=m("자료 찾아줘","user","u");val started=ConversationReaction.next(s(request),Behavior(),0,true)
  val reply=m("확인할게요","assistant","a");val state=s(request,reply)
  val walking=ConversationReaction.next(state,started,100,true)
  val talking=ConversationReaction.speechVisible(walking,3100)
  assertEquals("talk",ConversationReaction.next(state,talking,8099,true).action)
  val revised=reply.copy(revision=2,observation=reply.observation!!.copy(observedAt="2026-10-01T00:10:00Z"))
  val working=ConversationReaction.next(s(request,revised),talking,8100,true)
  assertEquals("work",working.action);assertEquals(98100,working.workUntil)
  val streamed=ConversationReaction.next(s(request,revised.copy(content="확인할게요. 자료를 살펴볼게요",revision=3)),working,20000,true)
  assertEquals("work",streamed.action);assertEquals(working.workUntil,streamed.workUntil)
  assertTrue(state.tasks.isEmpty());assertNull(reply.completedAt);assertTrue(streamed.completedIds.isEmpty())
 }
 @Test fun emptyPlaceholderDoesNotStopResearchAndOlderImagesCannotInterruptNewestAnswer(){
  val request=m("찾아줘","user","u");val start=ConversationReaction.next(s(request),Behavior(),0,true)
  assertEquals("work",ConversationReaction.next(s(request,m("","assistant","blank")),start,13000,true).action)
  val answer=m("답변이야","assistant","a");val talking=ConversationReaction.speechVisible(ConversationReaction.next(s(request,answer),start,14000,true),15000)
  val old=request.copy(revision=2,observation=request.observation!!.copy(observedAt="2026-10-01T02:00:00Z"))
  assertEquals("a",ConversationReaction.latest(s(old,answer))!!.messageId)
  assertEquals("work",ConversationReaction.next(s(old,answer),talking,20000,true).action)
 }
 @Test fun quietRoutineWalksReadsAndSleepsButNewRequestsAndStaleInterrupt(){
  val state=s(m("과거 대화"));val seed=ConversationReaction.seed(state,0)
  var b=ConversationReaction.next(state,seed,20000,true);assertEquals("left-lane",b.ambientTarget)
  b=ConversationReaction.next(state,b,40000,true);assertEquals("read",b.action)
  b=ConversationReaction.next(state,b,80000,true);assertEquals("window",b.ambientTarget)
  b=ConversationReaction.next(state,b,100000,true);assertEquals("sleep",b.action)
  val request=ConversationReaction.next(s(state.messages[0],m("자료 찾아줘","user","new")),b,100200,true)
  assertEquals("read",request.action);assertNull(request.ambientTarget)
  val stale=ConversationReaction.next(state.copy(sourceStatus=SourceStatus("stale",taskEvents="unsupported")),b,100200,false)
  assertEquals("listening",stale.action);assertNull(stale.ambientTarget)
  val unknown=ConversationReaction.next(state.copy(sourceStatus=SourceStatus("unknown",taskEvents="unsupported")),b,100200,true)
  assertEquals("listening",unknown.action);assertNull(unknown.ambientTarget)
 }
 @Test fun finalAnswerReturnsToWorkThenLocalQuietRoutineWithoutClaimingCompletion(){
  val state=s(m("자료 찾아줘","user","u"),m("여기 정리했어","assistant","a"))
  val speaking=ConversationReaction.speechVisible(ConversationReaction.next(state,Behavior(),10,true),100)
  val working=ConversationReaction.next(state,speaking,5100,true);assertEquals("work",working.action)
  assertEquals("work",ConversationReaction.next(state,working,35099,true).action)
  val quiet=ConversationReaction.next(state,working,35100,true);assertEquals("left-lane",quiet.ambientTarget)
  assertNull(quiet.lastCompletion);assertTrue(quiet.completedIds.isEmpty())
 }
}
