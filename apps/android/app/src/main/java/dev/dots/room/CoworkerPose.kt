package dev.dots.room

import kotlin.math.*

/** A seated master never changes its head, silhouette or hip anchor. */
object CoworkerPose {
 val frame=listOf(0,0,64,64)
 val arms=listOf(listOf(5,43,13,13),listOf(46,43,13,13))
 fun atlas(id:String)="day/$id-office-v3.png"
 fun socialAtlas(id:String)="day/$id-office-social.png"
 fun source(p:OfficePose,still:Boolean)=p.cell(still)?.let{listOf(it*68+2,2,64,64)}?:frame
 fun atlas(id:String,p:OfficePose)=if(p.front)socialAtlas(id)else atlas(id)
 fun rect(n:Coworker,p:OfficePose)=listOf(n.position[0]-n.size/2,n.position[1]-4-(if(p.front)52f else 60f)*n.size/64,n.size.toFloat(),n.size.toFloat())
 fun regions(p:OfficePose)=when(p.action){"coffee"->listOf(listOf(23,36,18,16));"wave"->listOf(listOf(3,30,13,16));"work"->arms;else->emptyList()}
 fun rise(slot:Int,arm:Int,elapsed:Long,still:Boolean):Float {
  if(still)return 0f
  val phase=((elapsed+slot*1100L+arm*2200L)%4400).toDouble()/4400
  return ((1-cos(phase*2*PI))*.5).toFloat()
 }
}
