package dev.dots.room

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Left-hand daylight belongs to the original floor artwork, underneath all props. */
fun drawRoomLighting(c:Canvas,m:Manifest,night:Boolean){
 if(night)c.drawRect(0f,0f,m.logicalSize[0].toFloat(),m.logicalSize[1].toFloat(),Paint().apply{color=Color.argb(75,15,15,28)})
}
