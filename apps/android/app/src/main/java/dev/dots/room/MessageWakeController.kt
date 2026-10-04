package dev.dots.room

import android.content.Context
import android.os.PowerManager

/** Wake the existing display; never starts another activity or dismisses the user's lock. */
class MessageWakeController(context:Context) {
 private val power=context.getSystemService(PowerManager::class.java)
 private var displayLock:PowerManager.WakeLock?=null
 @Suppress("DEPRECATION")
 fun onMessage(enabled:Boolean) {
  if(!enabled||power.isInteractive)return
  release()
  // Required on the selected API 33 tablet. Deprecated from API 33; newer OEM behavior needs QA.
  displayLock=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,"bami:message-display").apply{setReferenceCounted(false);acquire(15000)}
 }
 fun release(){displayLock?.let{if(it.isHeld)it.release()};displayLock=null}
}
