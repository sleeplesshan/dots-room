@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package dev.dots.room

import kotlinx.serialization.*
import kotlinx.serialization.json.*

val protocolJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
@Serializable data class Source(val adapterId:String,val sourceMode:String,val sourceSessionId:String?)
@Serializable data class SourceStatus(val state:String="unavailable",val lastObservedAt:String?=null,val detail:String="형아, 연결을 기다리고 있어요",@EncodeDefault(EncodeDefault.Mode.NEVER) val taskEvents:String?=null)
@Serializable data class Task(val taskId:String,val conversationId:String,val status:String,val phase:String,val publicSummary:String,val priority:Int,val updatedAt:String)
@Serializable data class TaskPayload(val status:String,val phase:String,val publicSummary:String,val priority:Int,val updatedAt:String)
@Serializable data class Message(val messageId:String,val conversationId:String,@EncodeDefault(EncodeDefault.Mode.NEVER) val taskId:String?=null,val role:String,val revision:Int,val content:String="",val state:String="streaming",val nextDelta:Int=0,val completedAt:String?=null,@EncodeDefault(EncodeDefault.Mode.NEVER) val observation:Observation?=null,@EncodeDefault(EncodeDefault.Mode.NEVER) val images:List<MediaAttachment> = emptyList())
@Serializable data class Metric(val knowledge:String="unknown",val value:Double?=null,val origin:String="unavailable",val scope:String="unavailable",val observedAt:String?=null,val freshness:String="unknown",val resetAt:String?=null,val unit:String="tokens")
@Serializable data class Usage(val inputTokens:Metric=Metric(),val outputTokens:Metric=Metric())
@Serializable data class Quota(val knowledge:String,val value:Double?,val origin:String,val scope:String,val observedAt:String?,val freshness:String,val resetAt:String?,val unit:String,val limitId:String,val name:String,val windowDurationMins:Double?,val usedPercent:Double?)
@Serializable data class History(val truncated:Boolean=false,val retainedMessageCount:Int=0)
@Serializable data class AppState(val sourceStatus:SourceStatus=SourceStatus(),val tasks:List<Task> = emptyList(),val messages:List<Message> = emptyList(),val usage:Usage=Usage(),val quotas:List<Quota> = emptyList(),val history:History=History())
@Serializable data class Snapshot(val snapshotId:String,val asOfSeq:Long,val sourceStatus:SourceStatus,val tasks:List<Task>,val messages:List<Message>,val usage:Usage,val quotas:List<Quota>,val history:History) { fun state()=AppState(sourceStatus,tasks,messages,usage,quotas,history) }
@Serializable data class Event(val schemaVersion:Int,val streamId:String,val eventId:String,val seq:Long,val timestamp:String,val source:Source,val type:String,@EncodeDefault(EncodeDefault.Mode.NEVER) val conversationId:String?=null,@EncodeDefault(EncodeDefault.Mode.NEVER) val taskId:String?=null,val payload:JsonObject)
@Serializable data class Delta(val messageId:String,val role:String,val revision:Int,val deltaIndex:Int,val text:String,val isFinal:Boolean)
@Serializable data class Start(val messageId:String,val role:String,val revision:Int)
@Serializable data class Completed(val messageId:String,val role:String,val revision:Int,val content:String,val completedAt:String)
@Serializable data class UsageUpdate(val usage:Usage,val quotas:List<Quota>)
@Serializable data class Hello(val heartbeatMs:Long,val staleMs:Long,val capabilities:List<String>)
@Serializable data class ServerError(val code:String,val message:String,val recoverable:Boolean)
@Serializable data class Chunk(val schemaVersion:Int,val type:String,val eventId:String,val streamId:String,val seq:Long,val index:Int,val count:Int,val totalBytes:Int,val data:String)
@Serializable data class CachedState(val streamId:String?,val seq:Long,val state:AppState,val messageMode:String="unavailable",val celebratedTaskIds:List<String> = emptyList())

fun metricText(m:Metric):String = if(m.knowledge=="unknown"||m.value==null) "정보 제공 안 됨" else "${m.value.toLong()} ${m.unit}" + when(m.freshness){"manual"->" · 수동";"stale"->" · 오래된 값";else->""}

@Serializable data class Observation(val origin:String,val sourceMessageId:String,val sourceConversationId:String?,val sourceTimestamp:String?,val observedAt:String,val sourceRevision:Int?,val revisionOrigin:String,val finality:String,val statusProvenance:String)
@Serializable data class MediaAttachment(val assetId:String?,val status:String,val width:Int?,val height:Int?,val alt:String,val error:String?)
@Serializable data class Observed(val messageId:String,val role:String,val revision:Int,val content:String,val observation:Observation,val images:List<MediaAttachment>)
