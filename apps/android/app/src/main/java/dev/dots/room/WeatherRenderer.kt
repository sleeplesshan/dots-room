package dev.dots.room

import android.graphics.*
import kotlin.math.*

class WeatherBlend {
 var current:String="";private set
 var previous:String="";private set
 private var since=0L
 fun update(key:String,now:Long,still:Boolean){if(current!=key){previous=if(current.isBlank())key else current;current=key;since=now};if(still)previous=current}
 fun progress(now:Long,still:Boolean)=if(still)1f else ((now-since)/1000f).coerceIn(0f,1f)
}
object WeatherRenderer {
 private val kinds=listOf("clear","cloud","overcast","rain","snow","fog")
 fun draw(c:Canvas,w:WeatherWindow,key:String,elapsed:Long,still:Boolean,images:Map<String,Bitmap>,paint:Paint,alpha:Int=255){
  val d=w.rect;val day=key.substringBefore(':')=="day";val kind=key.substringAfter(':');val i=kinds.indexOf(kind).coerceAtLeast(0)
  val token=c.saveLayerAlpha(RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),alpha);c.clipRect(d[0],d[1],d[0]+d[2],d[1]+d[3]);paint.alpha=255
  val sky=images.getValue("life/weather-${if(day)"day"else "night"}-v1.png");c.drawBitmap(sky,Rect(i%2*128,i/2*96,i%2*128+128,i/2*96+96),RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),paint)
  w.landscape?.let{name->val landscape=images.getValue(name);val height=d[2]*landscape.height/landscape.width;c.drawBitmap(landscape,null,RectF(d[0],d[1]+d[3]-height,d[0]+d[2],d[1]+d[3]),paint)}
  if(kind in listOf("cloud","overcast")){
   val cloud=images.getValue("day/train-cloud-v3.png");val width=min(52f,d[2]*.48f);val height=width*cloud.height/cloud.width
   val drift=if(still).5f else ((1-cos(Math.floorMod(elapsed,240000L)/240000.0*2*PI))*.5).toFloat()
   val x=d[0]+12+(d[2]-width-24).coerceAtLeast(0f)*drift;val y=d[1]+d[3]*.23f
   paint.alpha=if(day)160 else 55;c.drawBitmap(cloud,null,RectF(x,y,x+width,y+height),paint);paint.alpha=255
  }
  if(kind in listOf("rain","snow")){
   paint.color=if(kind=="rain")Color.argb(if(day)125 else 80,170,196,218)else Color.argb(190,239,244,246)
   for(n in 0..11){val x=d[0]+Math.floorMod(n*37,100)/100f*d[2];val speed=if(kind=="rain")2400L else 12000L;val t=if(still)Math.floorMod(n*313L,speed)else Math.floorMod(elapsed+n*313L,speed);val y=d[1]+t.toFloat()/speed*d[3];c.drawRect(x,y,x+1,y+if(kind=="rain")4 else 1,paint)}
  }
  paint.alpha=255;paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
  c.drawBitmap(images.getValue(w.mask),null,RectF(d[0],d[1],d[0]+d[2],d[1]+d[3]),paint);paint.xfermode=null
  c.restoreToCount(token)
 }
}
