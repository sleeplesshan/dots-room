package dev.dots.room
import org.junit.Test
import org.junit.Assert.*

class MessageArrivalTrackerTest {
 private fun m(id:String="a",text:String="응답",revision:Int=1,images:List<MediaAttachment> = emptyList())=Message(id,"fixture",role="assistant",revision=revision,content=text,images=images)
 @Test fun initialHistoryDuplicatesAndObservationOnlyEditsDoNotWake(){val t=MessageArrivalTracker();assertFalse(t.changed(listOf(m())));assertFalse(t.changed(listOf(m())));assertFalse(t.changed(listOf(m(revision=2))));assertFalse(t.changed(emptyList()))}
 @Test fun newIdsAndVisibleTextEditsWake(){val t=MessageArrivalTracker();t.seed(listOf(m()));assertTrue(t.changed(listOf(m(),m("b"))));assertTrue(t.changed(listOf(m(),m("b","수정"))));assertFalse(t.changed(listOf(m(),m("b","수정",revision=99))))}
 @Test fun loadedImageAndImageOnlyMessageWakeButErrorsDoNot(){val t=MessageArrivalTracker();val pending=MediaAttachment(null,"pending",null,null,"fixture",null);val ready=pending.copy(assetId="f".repeat(64),status="available",width=120,height=120);t.seed(emptyList());assertTrue(t.changed(listOf(m(text="",images=listOf(pending)))));assertTrue(t.changed(listOf(m(text="",images=listOf(ready)))));assertFalse(t.changed(listOf(m(text="",images=listOf(ready)))));assertFalse(t.changed(listOf(m(text="",images=listOf(ready.copy(status="expired"))))))}
 @Test fun reconnectBaselineAndClearedHistoryDoNotReplay(){val t=MessageArrivalTracker();t.seed(listOf(m()));assertFalse(t.changed(listOf(m(revision=200))));assertTrue(t.changed(listOf(m(),m("new"))));t.clear();assertFalse(t.changed(listOf(m(),m("new"))))}
}
