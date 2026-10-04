package dev.dots.room

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

object HomeFloorGeometry {
 fun rugOffset(w:Float,h:Float)=Point((w-384f)/2,(h-448f)*.55f)
 fun rugBounds(w:Float,h:Float):List<Float>{val p=rugOffset(w,h);return listOf(10f+p.x,253f+p.y,364f,144f)}
}

/** Extend wall/floor textures, never stretch the rug, sunlight, boards or pillars. */
object HomeBackdrop {
 fun draw(c:Canvas,b:Bitmap,images:Map<String,Bitmap>,w:Float,h:Float,p:Paint){
  fun patch(s:Rect,x:Float,y:Float){c.drawBitmap(b,s,RectF(x,y,x+s.width(),y+s.height()),p)}
  fun tile(s:Rect,x:Float,y:Float,right:Float,bottom:Float){val save=c.save();c.clipRect(x,y,right,bottom);var py=y;while(py<bottom){var px=x;while(px<right){patch(s,px,py);px+=s.width()};py+=s.height()};c.restoreToCount(save)}
  fun layer(name:String,x:Float=0f,y:Float=0f){val im=images.getValue("life/$name");c.drawBitmap(im,x,y,p)}
  // Continue the same original board rows across the canvas. The generated
  // candidate had a different hue; a second opaque rectangle must not appear.
  tile(Rect(338,160,350,194),0f,160f,w,minOf(194f,h))
  tile(Rect(372,194,384,448),0f,194f,w,minOf(448f,h))
  if(h>448f)tile(Rect(192,400,384,448),0f,448f,w,h)
  tile(Rect(180,4,340,140),0f,0f,w,146f)
  patch(Rect(34,0,350,160),34f,0f)
  if(w>384){tile(Rect(180,4,340,140),350f,0f,w-34,140f);tile(Rect(34,140,346,160),350f,140f,w-34,160f)}
  layer("home-bare-floor-v1.png")
  layer("home-left-light-v1.png")
  val rug=HomeFloorGeometry.rugOffset(w,h);layer("home-rug-v1.png",rug.x,rug.y)
  patch(Rect(0,0,34,194),0f,0f)
  patch(Rect(350,0,384,194),w-34,0f)
 }
}
