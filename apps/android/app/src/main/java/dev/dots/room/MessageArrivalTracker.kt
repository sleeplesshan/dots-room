package dev.dots.room

/** Tracks visible changes, not observation times/revisions or heartbeat frames. Bounded by chat retention. */
class MessageArrivalTracker {
 private data class Visible(val role:String,val text:String,val images:List<Pair<String?,String>>)
 private var previous:Map<String,Visible> = emptyMap()
 private var initialized=false
 fun seed(messages:List<Message>){previous=visible(messages);initialized=true}
 private fun visible(messages:List<Message>)=messages.takeLast(500).filter{it.role in listOf("user","assistant")&&(it.content.isNotBlank()||it.images.isNotEmpty())}.associate{it.messageId to Visible(it.role,it.content,it.images.map{image->image.assetId to image.status})}
 fun changed(messages:List<Message>):Boolean {
  val next=visible(messages)
  val changed=initialized&&next.any{(id,value)->
   val old=previous[id]
   old==null||old.role!=value.role||old.text!=value.text||value.images.any{it.second=="available"&&it !in old.images}
  }
  previous=next;initialized=true;return changed
 }
 fun clear(){previous=emptyMap();initialized=false}
}
