package dev.dots.room
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class) class PublicShowcaseTest {
 @Test fun captureSyntheticUi(){
  val instrument=InstrumentationRegistry.getInstrumentation();val ctx=instrument.targetContext
  check(android.os.Build.HARDWARE in listOf("ranchu","goldfish")){"Showcase capture is emulator-only"}
  fun shell(command:String){instrument.uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
  shell("settings put secure immersive_mode_confirmations confirmed")
  shell("settings put global policy_control immersive.full=dev.dots.room")
  fun capture(name:String,scene:String="home",portrait:Boolean=false,full:Boolean=false,widget:String?=null,composer:Boolean=false,options:Boolean=false){
   shell(if(portrait)"wm size 1200x1920"else "wm size 1920x1200")
   val intent=Intent(ctx,ShowcaseActivity::class.java).putExtra("scene",scene).putExtra("portrait",portrait).putExtra("full",full).putExtra("composer",composer);widget?.let{intent.putExtra("widget",it)}
   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
   ctx.startActivity(intent)
   run {
    val deadline=android.os.SystemClock.elapsedRealtime()+20000
    while(instrument.uiAutomation.rootInActiveWindow?.packageName?.toString()!="dev.dots.room"&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(200)
    assertEquals("Capture must target the app, never the launcher", "dev.dots.room",instrument.uiAutomation.rootInActiveWindow?.packageName?.toString())
    Thread.sleep(5000)
    if(options){
     val size=instrument.uiAutomation.takeScreenshot();val x=size.width*.25f;val y=size.height*.735f;size.recycle()
     repeat(2){val t=android.os.SystemClock.uptimeMillis();instrument.sendPointerSync(android.view.MotionEvent.obtain(t,t,android.view.MotionEvent.ACTION_DOWN,x,y,0));instrument.sendPointerSync(android.view.MotionEvent.obtain(t,t+70,android.view.MotionEvent.ACTION_UP,x,y,0));Thread.sleep(250)}
     Thread.sleep(600)
    }
    var bitmap=instrument.uiAutomation.takeScreenshot();assertNotNull(bitmap)
    repeat(10){
     val colors=mutableSetOf<Int>();for(x in 0 until bitmap.width step 20)for(y in 0 until bitmap.height step 20)colors.add(bitmap.getPixel(x,y))
     if(colors.size<64){bitmap.recycle();Thread.sleep(500);bitmap=instrument.uiAutomation.takeScreenshot()}
    }
    assertEquals(if(portrait)1200 else 1920,bitmap.width);assertEquals(if(portrait)1920 else 1200,bitmap.height)
    val colors=mutableSetOf<Int>();for(x in 0 until bitmap.width step 20)for(y in 0 until bitmap.height step 20)colors.add(bitmap.getPixel(x,y));assertTrue("Rendered scene instead of blank launch surface",colors.size>=64)
    val out=File(ctx.filesDir,"showcase/$name.png");out.parentFile!!.mkdirs();out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    bitmap.recycle()
   }
  }
  capture("home-landscape");capture("office",scene="office");capture("subway-morning",scene="subway-am");capture("subway-evening",scene="subway-pm")
  capture("quick-chat",options=true);capture("typing",composer=true)
  capture("weekly-limit",widget="MONITOR");capture("work-notes",widget="MEMO");capture("weather",widget="WEATHER")
  capture("portrait-split",portrait=true);capture("portrait-scene",portrait=true,full=true)
 }
}
