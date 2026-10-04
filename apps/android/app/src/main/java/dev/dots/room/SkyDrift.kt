package dev.dots.room

/** Identical opaque clouds move left at constant speed; copies wrap outside the glass. */
object SkyDrift {
 fun shift(a:AmbientLayer,elapsed:Long,still:Boolean)=if(still)0f else Math.floorMod(elapsed,a.periodMs.coerceAtLeast(1)).toDouble().div(a.periodMs.coerceAtLeast(1)).times(a.rect[2]+24).toFloat()
 fun positions(a:AmbientLayer,elapsed:Long,still:Boolean):List<Float>{val span=a.rect[2]+24;val offset=shift(a,elapsed,still);return (-1..2).map{a.rect[0]+a.rect[2]*.25f+it*span-offset}}
}
