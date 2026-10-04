package dev.dots.room

/** A reply is highlighted once, before its visible five-second speech clock starts. */
class ReplyShimmer {
 var messageId:String?=null;private set
 var since:Long=-1000;private set
 var duration:Long=600;private set
 fun waiting(id:String,now:Long,reduced:Boolean,claim:(String)->Boolean):Boolean {
  if(messageId!=id){
   if(!claim(id))return false
   messageId=id;since=now;duration=if(reduced)200 else 600
  }
  return active(id,now)
 }
 fun active(id:String?,now:Long)=id!=null&&id==messageId&&now-since in 0 until duration
 fun visible(b:Behavior,now:Long,paused:Boolean,held:Boolean)=!paused&&!held&&b.action=="talk"&&active(b.reactionMessageId,now)
}
