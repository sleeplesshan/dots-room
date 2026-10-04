package dev.dots.room

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun MessageImage(image:MediaAttachment,loadMedia:suspend (String)->ByteArray,enabled:Boolean,refreshKey:String?=null){var expanded by remember{mutableStateOf(false)};var bitmap by remember(image.assetId){mutableStateOf<Bitmap?>(null)};var status by remember(image){mutableStateOf(image.status)}
 LaunchedEffect(image,enabled,refreshKey){if(!enabled||image.status!="available"||image.assetId==null)return@LaunchedEffect;status="loading";try{val bytes=loadMedia(image.assetId);bitmap=withContext(Dispatchers.Default){BitmapFactory.decodeByteArray(bytes,0,bytes.size)};check(bitmap!=null);status="available"}catch(_:MediaExpired){status="expired"}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){status="error"}}
 val b=bitmap;if(b!=null){Image(b.asImageBitmap(),image.alt.ifBlank{"바미 이미지 · 누르면 확대"},Modifier.fillMaxWidth().heightIn(min=120.dp,max=280.dp).clickable{expanded=true},contentScale=ContentScale.Fit)}else Text(when(status){"pending","loading"->"이미지 불러오는 중";"expired"->"이미지 만료됨";else->"이미지를 읽을 수 없어요 (${image.error?:"전송 오류"})"})
 if(expanded&&b!=null)AlertDialog(onDismissRequest={expanded=false},text={Image(b.asImageBitmap(),image.alt.ifBlank{"확대 이미지"},Modifier.fillMaxWidth().aspectRatio(b.width.toFloat()/b.height).heightIn(max=600.dp),contentScale=ContentScale.Fit)},confirmButton={TextButton(onClick={expanded=false}){Text("이미지 닫기")}})
}
