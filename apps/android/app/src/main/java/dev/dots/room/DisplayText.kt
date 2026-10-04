package dev.dots.room

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** Fit visible glyph bounds, then clip to the authored inner screen, never the bezel. */
fun drawDisplayText(c:Canvas,p:Paint,r:List<Float>,lines:List<Pair<String,Float>>,gap:Float=if(lines.size>1)3f else 0f,pad:Float=2f){
 fun measure(line:Pair<String,Float>,scale:Float):Rect {p.textSize=line.second*scale;return Rect().also{p.getTextBounds(line.first,0,line.first.length,it)}}
 val rects=lines.map{measure(it,1f)}
 val height=rects.sumOf{it.height().coerceAtLeast(1)}+gap*(lines.size-1)
 val width=rects.maxOfOrNull{it.width()}?.toFloat()?:1f
 var scale=minOf(1f,((r[2]-pad*2)/width).coerceAtLeast(.01f),((r[3]-pad*2)/height).coerceAtLeast(.01f))
 var bounds=lines.map{measure(it,scale)}
 // Font hinting can round a glyph outward; tighten once if that happens.
 val actualHeight=bounds.sumOf{it.height().coerceAtLeast(1)}+gap*(lines.size-1)
 if(actualHeight>r[3]-pad*2){scale*=((r[3]-pad*2)/actualHeight).coerceAtLeast(.01f);bounds=lines.map{measure(it,scale)}}
 var y=r[1]+(r[3]-bounds.sumOf{it.height().coerceAtLeast(1)}-gap*(lines.size-1))/2
 val token=c.save();c.clipRect(r[0],r[1],r[0]+r[2],r[1]+r[3])
 lines.zip(bounds).forEach{(line,b)->p.textSize=line.second*scale;c.drawText(line.first,r[0]+r[2]/2,y-b.top,p);y+=b.height().coerceAtLeast(1)+gap}
 c.restoreToCount(token)
}
