package dev.dots.room
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.Base64

class BamiChunkTest {
 private fun frames():Pair<Event,List<String>>{val e=Event(2,UUID.randomUUID().toString(),UUID.randomUUID().toString(),1,"2026-09-30T12:00:00Z",Source("fixture","mock",null),"message.completed","c",payload=protocolJson.encodeToJsonElement(Completed("m","assistant",1,"형아 🌙".repeat(30000),"2026-09-30T12:00:00Z")).jsonObject);val bytes=protocolJson.encodeToString(e).toByteArray();val parts=bytes.toList().chunked(160*1024).map{it.toByteArray()};return e to parts.mapIndexed{index,part->protocolJson.encodeToString(Chunk(2,"server.chunk",e.eventId,e.streamId,e.seq,index,parts.size,bytes.size,Base64.getEncoder().encodeToString(part)))}}
 @Test fun outOfOrderChunksApplyOnlyAfterCompletion(){val(e,parts)=frames();val decoder=EventClient();parts.drop(1).reversed().forEach{assertNull(decoder.decode(it))};assertEquals(e,decoder.decode(parts.first()))}
 @Test fun duplicateChunkMustHaveIdenticalBytes(){val(_,parts)=frames();val decoder=EventClient();assertNull(decoder.decode(parts.first()));assertNull(decoder.decode(parts.first()));val c=protocolJson.decodeFromString<Chunk>(parts.first());try{decoder.decode(protocolJson.encodeToString(c.copy(data=Base64.getEncoder().encodeToString(byteArrayOf(0)))));fail("changed fragment accepted")}catch(_:IllegalArgumentException){}}
 @Test fun mismatchedCountOrCompletedSizeNeverApplies(){val(_,parts)=frames();val c=protocolJson.decodeFromString<Chunk>(parts.first());val decoder=EventClient();decoder.decode(parts.first());try{decoder.decode(protocolJson.encodeToString(c.copy(count=c.count+1)));fail("wrong count")}catch(_:IllegalArgumentException){};decoder.clear();val smaller=parts.map{protocolJson.decodeFromString<Chunk>(it).copy(totalBytes=c.totalBytes-1)};try{smaller.forEach{assertNull(decoder.decode(protocolJson.encodeToString(it)))};fail("wrong bytes")}catch(_:IllegalArgumentException){}}
}
