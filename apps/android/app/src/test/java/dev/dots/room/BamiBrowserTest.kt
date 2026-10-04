package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.*
import java.util.UUID
class BamiBrowserTest {
 private val stream=UUID.randomUUID().toString()
 private val source=Source("browser-dots-v2","live",null)
 private val observation=Observation("browser-ui","raw-id",null,null,"2026-10-01T00:00:00Z",null,"local-observation","unknown","unknown")
 private fun event(seq:Long,p:Observed)=Event(2,stream,UUID.randomUUID().toString(),seq,"2026-10-01T00:00:00Z",source,"message.observed","browser-dots-local",payload=protocolJson.encodeToJsonElement(p).jsonObject)
 private fun reducer():AppReducer=AppReducer().apply{restore(CachedState(stream,1,AppState(sourceStatus=SourceStatus("live",taskEvents="unsupported"))))}
 @Test fun browserChangesKeepBubbleAndUnknownProvenance(){val r=reducer();val p=Observed("raw-id","assistant",1,"한글 🌙",observation,emptyList());r.apply(event(2,p));r.apply(event(3,p.copy(revision=2,content="수정 🌙")));r.apply(event(4,p));assertEquals(1,r.state.messages.size);assertEquals("수정 🌙",r.state.messages.single().content);assertEquals("observed",r.state.messages.single().state);assertNull(r.state.messages.single().completedAt);assertNull(r.state.messages.single().observation?.sourceTimestamp);assertEquals("talk",BehaviorScheduler.next(r.state,Behavior("sleep"),900000,true).action);assertEquals("talk",BehaviorScheduler.next(r.state,Behavior("celebrate"),10,true).action)}
 @Test fun imageOnlyAndSameTextDifferentIdsRemainSeparate(){val r=reducer();val media=MediaAttachment("a".repeat(64),"available",120,120,"바미",null);val p=Observed("raw-id","assistant",1,"",observation,listOf(media));r.apply(event(2,p));r.apply(event(3,p.copy(messageId="other",observation=observation.copy(sourceMessageId="other"))));assertEquals(2,r.state.messages.size);assertEquals(media,r.state.messages.first().images.single())}
 @Test fun invalidMediaSnapshotIsAtomic(){val r=reducer();val p=Observed("raw-id","assistant",1,"",observation,listOf(MediaAttachment("bad","available",1025,1,"",null)));try{r.apply(event(2,p));fail("invalid")}catch(_:IllegalArgumentException){};assertTrue(r.state.messages.isEmpty());assertEquals(1,r.seq)}
 @Test fun browserGoldenMatchesSharedContract(){val raw=javaClass.getResourceAsStream("/browser-snapshot.json")!!.bufferedReader().readText();val r=AppReducer();r.apply(protocolJson.decodeFromString<Event>(raw));assertEquals("observed",r.state.messages.single().state);assertEquals("browser-ui",r.state.messages.single().observation?.origin);assertNull(r.state.messages.single().observation?.sourceConversationId)}
}
