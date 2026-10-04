package dev.dots.room

import android.app.Activity
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import java.io.File

/** Debug APK only. An explicitly approved, selected USB run-as peer writes a kernel FIFO. */
object UsbPairing {
 const val ACTION="dev.dots.room.PAIR_USB"
 private var job:Job?=null
 fun start(activity:Activity,vm:BamiViewModel){if(activity.intent?.action!=ACTION||job?.isActive==true)return;val owner=activity as? androidx.lifecycle.LifecycleOwner?:return;val pipe=File(activity.filesDir,"usb-pairing.pipe");val result=File(activity.filesDir,"usb-pairing-result.json");job=owner.lifecycleScope.launch{
  result.delete();pipe.delete();var accepted:String?=null
  try{accepted=withContext(Dispatchers.IO){Os.mkfifo(pipe.path,384);val fd=Os.open(pipe.path,OsConstants.O_RDWR or OsConstants.O_NONBLOCK,0);try{val bytes=java.io.ByteArrayOutputStream();val buffer=ByteArray(128);val deadline=android.os.SystemClock.elapsedRealtime()+60000;while(android.os.SystemClock.elapsedRealtime()<deadline){ensureActive();try{val n=Os.read(fd,buffer,0,buffer.size);if(n>0){require(bytes.size()+n<=64);bytes.write(buffer,0,n);if(buffer.take(n).contains(10.toByte()))break}}catch(e:ErrnoException){if(e.errno!=OsConstants.EAGAIN)throw e};delay(50)};val token=bytes.toString("US-ASCII").trim();require(Regex("[A-Za-z0-9_-]{43}").matches(token));token}finally{Os.close(fd);pipe.delete()}};vm.pair(requireNotNull(accepted));check(vm.state.value.paired);result.writeText("{\"paired\":true,\"encrypted\":true}")
  }catch(e:CancellationException){throw e}catch(_:Exception){result.writeText("{\"paired\":false,\"error\":\"USB_PAIRING_FAILED\"}")}
  finally{accepted=null;pipe.delete();activity.intent.action=android.content.Intent.ACTION_MAIN}
 }}
}
