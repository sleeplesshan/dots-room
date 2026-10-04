package dev.dots.room

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DeskStateContractTest {
 private val now=Instant.parse("2026-10-02T06:00:00Z")
 private fun notes(items:List<NoteItem>)=WorkNotes(pageId="page_00000000000000000000000000000001",url="https://chatgpt.com/space/page_00000000000000000000000000000001",status="ready",sections=listOf(NoteSection("free","형아 자유 메모",items)),observedAt=now.toString(),lastSuccessAt=now.toString(),freshness="fresh")
 @Test fun staleIndividualTaskCannotKeepWholeDeskWorkingOrCelebrate(){
  val stale=ExecutionTask("old","working","working","fixture",now.minusSeconds(16).toString())
  val e=ExecutionState("known","fixture",now.toString(),"fresh","",listOf(stale))
  assertEquals("unknown",runtimeState(e,true,now))
  val idle=stale.copy(taskId="current",state="idle",observedAt=now.toString())
  assertEquals("idle",runtimeState(e.copy(tasks=listOf(stale,idle)),true,now))
  val complete=stale.copy(state="completed",completionEventId="late")
  val b=ActualBehavior.next(AppState(),DeskState(execution=e.copy(tasks=listOf(complete))),Behavior("work"),2000,true,now)
  assertEquals("listening",b.action);assertTrue(b.completedIds.isEmpty())
 }
 @Test fun longUnicodeNoteRemainsMemoAndPreservesText(){
  val text="메모 📝\n".repeat(300)
  val state=DeskState(notes=notes(listOf(NoteItem("test",text)))).validate()
  val decoded=protocolJson.decodeFromString<DeskState>(protocolJson.encodeToString(DeskState.serializer(),state)).validate()
  assertEquals(text,decoded.notes.sections.single().items.single().text)
  assertEquals("listening",ActualBehavior.next(AppState(),decoded,Behavior(),2000,true,now).action)
 }
 @Test fun oversizedItemsAndAggregateAreRejected(){
  assertThrows(IllegalArgumentException::class.java){DeskState(notes=notes(listOf(NoteItem("long","a".repeat(8001))))).validate()}
  assertThrows(IllegalArgumentException::class.java){DeskState(notes=notes((0..8).map{NoteItem("$it","가".repeat(3000))})).validate()}
 }
 @Test fun invalidFreshnessLinkAndCompletionFieldsAreRejected(){
  assertThrows(IllegalArgumentException::class.java){DeskState(notes=notes(emptyList()).copy(freshness="live")).validate()}
  assertThrows(IllegalArgumentException::class.java){DeskState(notes=notes(emptyList()).copy(pageId=null,url="https://example.com/")).validate()}
  val t=ExecutionTask("id","completed","none","fixture",now.toString(),"a".repeat(201))
  assertThrows(IllegalArgumentException::class.java){DeskState(execution=ExecutionState(tasks=listOf(t))).validate()}
 }
}
