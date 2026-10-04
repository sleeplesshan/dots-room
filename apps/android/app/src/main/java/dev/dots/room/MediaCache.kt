package dev.dots.room

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.*
import java.security.MessageDigest
import kotlinx.coroutines.*

class MediaExpired:Exception()
class MediaCache(private val context:Context,private val secure:SecureStore,private val prefix:String="",private val limit:Long=32L*1024*1024) {
 private fun files()=context.filesDir.listFiles()?.filter{it.name.startsWith(prefix+"media-")&&it.name.endsWith(".enc")}?:emptyList()
 private fun verify(id:String,b:ByteArray):ByteArray{require(b.size<=2*1024*1024);require(MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}==id);val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(b,0,b.size,bounds);require(bounds.outWidth in 1..1024&&bounds.outHeight in 1..1024);return b}
 @Synchronized fun read(id:String):ByteArray?{require(Regex("[a-f0-9]{64}").matches(id));val text=secure.load("media-$id.enc")?:return null;return try{context.getFileStreamPath(prefix+"media-$id.enc").setLastModified(System.currentTimeMillis());verify(id,Base64.decode(text,Base64.NO_WRAP))}catch(_:Exception){secure.clear("media-$id.enc");null}}
 @Synchronized fun put(id:String,b:ByteArray){verify(id,b);val text=Base64.encodeToString(b,Base64.NO_WRAP);val required=text.toByteArray().size+29L;require(required<=limit);val current=files().filter{it.name!=prefix+"media-$id.enc"}.sortedBy{it.lastModified()}.toMutableList();var size=current.sumOf{it.length()};while(size+required>limit&&current.isNotEmpty()){val f=current.removeAt(0);size-=f.length();f.delete()};secure.save("media-$id.enc",text)}
 @Synchronized fun clear(){files().forEach{it.delete()}}
 suspend fun load(id:String,token:String,port:Int,http:OkHttpClient):ByteArray=load(id,token,ConnectionProfile(usbPort=port),http)
 suspend fun load(id:String,token:String,profile:ConnectionProfile,http:OkHttpClient):ByteArray=withContext(Dispatchers.IO){read(id)?.let{return@withContext it};val call=http.newCall(Request.Builder().url(profile.http("/v1/media/$id")).header("Authorization","Bearer $token").build());val handle=currentCoroutineContext()[Job]?.invokeOnCompletion{call.cancel()};try{call.execute().use{r->if(r.code==404)throw MediaExpired();check(r.isSuccessful);require(r.header("Content-Type")=="image/png");val body=requireNotNull(r.body);require(body.contentLength() in -1..2L*1024*1024);val out=java.io.ByteArrayOutputStream();body.byteStream().use{stream->val buf=ByteArray(8192);while(true){currentCoroutineContext().ensureActive();val n=stream.read(buf);if(n<0)break;require(out.size()+n<=2*1024*1024);out.write(buf,0,n)}};out.toByteArray().also{put(id,it)}}}finally{handle?.dispose()}}
}
