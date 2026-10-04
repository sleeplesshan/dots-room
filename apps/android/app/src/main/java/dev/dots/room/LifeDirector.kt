package dev.dots.room

/** Decorative life never edits execution knowledge, tasks or completion events. */
object LifeDirector {
 private val choices=listOf("look","left-lane","read","window","center")
 private fun seed(b:Behavior)=b.reactionMessageId?.hashCode()?:17
 fun next(old:Behavior,now:Long,knownIdle:Boolean):Behavior {
  if(old.lifeSince<0)return old.copy(action="listening",since=now,ambientTarget="stay",answering=false,idleSince=now,lifeSince=now,lifeNextAt=now+1200,lifeStep=0,lifeChoice="settle",pendingAction=null,pendingTask=null)
  if(now<old.lifeNextAt)return old.copy(answering=false)
  val settling=old.lifeChoice=="settle"
  val step=if(settling)old.lifeStep else old.lifeStep+1
  val candidates=if(knownIdle)choices+"sleep"else choices
  var choice=if(settling)"breath"else candidates[Math.floorMod(seed(old)+step*7,candidates.size)]
  if(choice==old.lifeChoice)choice=candidates[(candidates.indexOf(choice)+1)%candidates.size]
  val action=when(choice){"read"->"read";"sleep"->"sleep";"look"->"look";else->"idle"}
  val target=if(choice in listOf("read","sleep","left-lane","window","center"))choice else "stay"
  val hold=25000L+Math.floorMod(seed(old)+step*7919,20001)
  return old.copy(action=action,since=now,ambientTarget=target,answering=false,lifeStep=step,lifeChoice=choice,lifeNextAt=now+hold,pendingAction=null,pendingTask=null)
 }
 /** Blink occasionally; breathing and facial motion are separate clocks. */
 fun blinkElapsed(now:Long,epoch:Long):Long? {
  val elapsed=(now-epoch).coerceAtLeast(0);val cycle=elapsed/12000;val offset=8000+Math.floorMod(cycle*7919,3600);val t=elapsed%12000-offset
  return if(t in 0..419)t else null
 }
}
