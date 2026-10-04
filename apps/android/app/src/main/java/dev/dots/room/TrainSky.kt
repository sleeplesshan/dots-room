package dev.dots.room

import kotlin.math.cos
import kotlin.math.min

/** The entire cloud stays inside glass; its source aspect ratio is unchanged. */
object TrainSky {
 fun rect(a:AmbientLayer,elapsed:Long,still:Boolean):List<Float> {
  val src=a.frameRects.first();val w=min(64f,(a.rect[2]-12f).coerceAtLeast(1f));val h=w*src[3]/src[2]
  val phase=if(still)0.0 else Math.floorMod(elapsed,a.periodMs.coerceAtLeast(1)).toDouble()/a.periodMs.coerceAtLeast(1)
  val t=((1-cos(phase*Math.PI*2))*.5).toFloat()
  return listOf(a.rect[0]+6+(a.rect[2]-w-12)*t,a.rect[1]+a.rect[3]*.14f,w,h)
 }
}
