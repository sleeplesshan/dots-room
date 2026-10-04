package dev.dots.room

import kotlin.math.*

data class AmbientPlayback(val first:Int,val next:Int,val blend:Float){
 companion object {
  fun at(a:AmbientLayer,elapsed:Long,still:Boolean=false):AmbientPlayback {
   if(still||a.kind=="plant"||a.kind=="static"||a.frameRects.size<=1)return AmbientPlayback(0,0,0f)
   val phase=Math.floorMod(elapsed,a.periodMs.coerceAtLeast(1)).toDouble()/a.periodMs.coerceAtLeast(1)*a.frameRects.size
   val first=floor(phase).toInt();val t=(phase-first).toFloat()
   // Authored opaque cat cels keep crisp contours; do not dissolve the whole animal.
   if(a.kind=="cat")return AmbientPlayback(0,0,0f)
   return AmbientPlayback(first,(first+1)%a.frameRects.size,t*t*(3-2*t))
  }
 }
}

/** Deform only the authored upper back; face, paws, tail and coat remain the same master. */
object CatBreathing {
 fun rise(a:AmbientLayer,elapsed:Long,still:Boolean=false):Float {
  if(still)return 0f
  val phase=Math.floorMod(elapsed,a.periodMs.coerceAtLeast(1)).toDouble()/a.periodMs.coerceAtLeast(1)
  return ((1-cos(phase*2*PI))*.5).toFloat()
 }
}
