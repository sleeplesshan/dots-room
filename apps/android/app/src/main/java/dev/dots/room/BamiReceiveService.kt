package dev.dots.room

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*

/** Explicitly started from the visible paired app; no boot launch or conversation notification. */
class BamiReceiveService:Service() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private lateinit var vm:BamiViewModel
 private lateinit var wake:MessageWakeController
 private var cpu:PowerManager.WakeLock?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){
  super.onCreate();vm=(application as BamiApplication).model;wake=MessageWakeController(this)
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("bami-wireless","바미 무선 테스트 대화 수신",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);description="화면이 꺼진 동안 대화 수신을 유지합니다"})
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,BamiReceiveService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"bami-wireless").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("바미 무선 테스트 대화 수신 중").setContentText("새 대화가 오면 화면을 켭니다").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_SECRET).addAction(Notification.Action.Builder(null,"수신 중지",stop).build()).build()
  if(Build.VERSION.SDK_INT>=34)startForeground(8788,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)else startForeground(8788,notification)
  vm.backgroundReceiving(true)
  scope.launch{vm.messageArrivals.collect{wake.onMessage(vm.state.value.wakeOnMessage)}}
  scope.launch{vm.state.collect{s->if(!s.paired||s.localDemo||!s.wakeOnMessage){stopSelf()}else if(s.transport!="연결됨")releaseCpu()else if(cpu?.isHeld!=true)leaseCpu()}}
  scope.launch{while(isActive){
   // Keep the receiver runnable while the USB socket is healthy. A bounded lease is renewed only then.
   if(vm.state.value.transport=="연결됨"){
    leaseCpu()
   }else releaseCpu()
   delay(30000)
  }}
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(intent?.action=="STOP"){vm.setWakeOnMessage(false);stopSelf()};return START_NOT_STICKY}
 private fun leaseCpu(){if(cpu==null)cpu=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"bami:wireless-receiver").apply{setReferenceCounted(false)};cpu!!.acquire(70000)}
 private fun releaseCpu(){cpu?.let{if(it.isHeld)it.release()};cpu=null}
 override fun onDestroy(){scope.cancel();releaseCpu();wake.release();vm.backgroundReceiving(false);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
