package dev.dots.room

import androidx.compose.runtime.*

data class OfficePose(val action:String="work",val elapsed:Long=0,val duration:Long=0) {
 val front:Boolean get()=action!="work"
 fun cell(still:Boolean=false):Int?=if(!front)null else if(!still&&(elapsed<220||elapsed>duration-220))0 else when(action){"coffee"->2;"wave"->3;else->1}
 val label:String get()=when(action){"look"->"돌아보는 중";"coffee"->"커피 마시는 중";"wave"->"인사하는 중";"chat"->"동료와 이야기하는 중";else->"업무 중"}
}

/** Decorative office life only; no messages or task state are created. */
class OfficeLife {
 private data class Touch(val action:String,val time:Long,val duration:Long)
 private val touches=mutableStateMapOf<Int,Touch>();private val counts=mutableMapOf<Int,Int>()
 fun tap(slot:Int,now:Long):Boolean {
  if(touches[slot]?.let{now-it.time<350}==true)return false
  val index=(counts[slot]?:0)%3;counts[slot]=index+1
  touches[slot]=Touch(listOf("look","wave","coffee")[index],now,if(index==2)8000 else 6000);return true
 }
 fun at(slot:Int,now:Long,born:Long,still:Boolean):OfficePose {
  touches[slot]?.let{t->if(now-t.time in 0 until t.duration)return OfficePose(t.action,now-t.time,t.duration)}
  return automatic(slot,(now-born).coerceAtLeast(0),still)
 }
 companion object {
  fun automatic(slot:Int,elapsed:Long,still:Boolean):OfficePose {
   if(still)return OfficePose()
   val phase=elapsed%120000;val coffee=24000+slot*9000L;val chat=82000+(slot/2)*12000L
   return when(phase){in coffee until coffee+9000->OfficePose("coffee",phase-coffee,9000);in chat until chat+8000->OfficePose("chat",phase-chat,8000);else->OfficePose()}
  }
 }
}
