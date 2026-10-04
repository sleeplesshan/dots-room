package dev.dots.room

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.serialization.json.*

class EventClient {
 private data class Assembly(val chunk:Chunk,val parts:MutableMap<Int,ByteArray> = mutableMapOf(),val started:Long=System.nanoTime())
 private var assembly:Assembly?=null
 fun clear(){assembly=null}
 fun decode(raw:String):Event? {
  require(raw.toByteArray().size<=256*1024)
  val obj=protocolJson.parseToJsonElement(raw).jsonObject
  if(obj["type"]?.jsonPrimitive?.content!="server.chunk")return protocolJson.decodeFromJsonElement(obj)
  val c=protocolJson.decodeFromJsonElement<Chunk>(obj)
  require(c.schemaVersion==2&&c.count in 2..64&&c.index in 0 until c.count&&c.totalBytes in 1..8*1024*1024&&c.data.length<=220000)
  var a=assembly
  if(a==null){a=Assembly(c);assembly=a}
  require(System.nanoTime()-a.started<15_000_000_000L)
  require(a.chunk.eventId==c.eventId&&a.chunk.streamId==c.streamId&&a.chunk.seq==c.seq&&a.chunk.count==c.count&&a.chunk.totalBytes==c.totalBytes)
  val bytes=Base64.getDecoder().decode(c.data);require(bytes.size<=160*1024)
  val old=a.parts[c.index];require(old==null||old.contentEquals(bytes));a.parts[c.index]=bytes
  require(a.parts.values.sumOf{it.size}<=c.totalBytes)
  if(a.parts.size<c.count)return null
  val out=ByteArrayOutputStream();(0 until c.count).forEach{out.write(a.parts.getValue(it))};val b=out.toByteArray();require(b.size==c.totalBytes);assembly=null
  val e=protocolJson.decodeFromString<Event>(String(b,Charsets.UTF_8));require(e.eventId==c.eventId&&e.streamId==c.streamId&&e.seq==c.seq);return e
 }
}
