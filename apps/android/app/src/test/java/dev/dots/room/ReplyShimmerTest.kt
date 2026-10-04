package dev.dots.room

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReplyShimmerTest {
 @Test fun sixFramesFinishBeforeSpeechAndEditsNeverRestartTheClock(){
  val seen=mutableSetOf<String>();var claims=0;val shimmer=ReplyShimmer();val claim:(String)->Boolean={claims++;seen.add(it)}
  for(t in 1000L..1599 step 33)assertTrue(shimmer.waiting("answer",t,false,claim))
  assertEquals(1,claims);assertFalse(shimmer.waiting("answer",1600,false,claim));assertFalse(shimmer.waiting("answer",5000,false,claim));assertEquals(1,claims)
  val m=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/office.json").readText());val a=m.animations.first{it.action=="spark"}
  assertEquals(600,a.frameDurationsMs.sum());assertEquals(6,a.frameRects.size);assertFalse(a.loop)
  assertEquals((0..5).toList(),(0L..500 step 100).map{SpriteAnimator.index(a,it)});assertEquals(5,SpriteAnimator.index(a,5000))
  val message=Message("answer","browser-dots-local",role="assistant",revision=1,content="합성 답변",state="observed",observation=Observation("browser-ui","answer",null,null,"2026-10-02T00:00:00Z",null,"local-observation","unknown","unknown"))
  val state=AppState(sourceStatus=SourceStatus("live",taskEvents="unsupported"),messages=listOf(message))
  val pending=ConversationReaction.next(state,Behavior(),1000,true)
  assertEquals("talk",ConversationReaction.next(state,pending,30000,true).action) // no arrival yet
  val speaking=ConversationReaction.speechVisible(pending,1600)
  val revised=state.copy(messages=listOf(message.copy(revision=2,content="합성 답변 수정")))
  val updated=ConversationReaction.next(revised,speaking,6599,true)
  assertEquals(1600,updated.since);assertEquals("talk",updated.action);assertEquals("work",ConversationReaction.next(revised,updated,6600,true).action)
 }
 @Test fun interruptionReducedMotionAndRecreatedRendererPreserveOnceOnlyHighlight(){
  val seen=mutableSetOf<String>();val claim:(String)->Boolean={seen.add(it)};val shimmer=ReplyShimmer()
  assertTrue(shimmer.waiting("a",10,false,claim));assertTrue(shimmer.visible(Behavior("talk",reactionMessageId="a"),200,false,false))
  assertFalse(shimmer.visible(Behavior("read",reactionMessageId="user"),200,false,false));assertFalse(shimmer.visible(Behavior("talk",reactionMessageId="a"),200,true,false));assertFalse(shimmer.visible(Behavior("talk",reactionMessageId="a"),200,false,true))
  assertFalse(ReplyShimmer().waiting("a",300,false,claim)) // resize/recreation cannot consume again
  assertTrue(shimmer.waiting("b",1000,true,claim));assertTrue(shimmer.active("b",1199));assertFalse(shimmer.active("b",1200));assertEquals(200,shimmer.duration)
 }
 @Test fun allSceneSpeechAnchorsMatchTheReachableTalkDestination(){
  for(id in listOf("home","office","subway-am","subway-pm"))for((w,h)in listOf(960f to 1200f,1200f to 1920f,1200f to 960f,320f to 800f)){
   val base=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/$id.json").readText());val m=SceneLayout.fit(base,w,h).manifest
   assertEquals("$id $w/$h",m.waypoints.first{it.id=="center"}.point,m.speechAnchor)
   val desk=m.furniture.first{it.id=="desk"};val motion=SceneMotion(m);motion.place(Placement(Point(desk.anchor!![0],desk.anchor[1]),"desk"))
   repeat(1800){motion.tick("talk",1f/30,false,true)}
   assertFalse("$id stranded before shimmer",motion.fallback);assertFalse(motion.moving);assertFalse(motion.transition)
   assertEquals(m.speechAnchor[0],motion.position.x,.001f);assertEquals(m.speechAnchor[1],motion.position.y,.001f)
  }
 }
}
