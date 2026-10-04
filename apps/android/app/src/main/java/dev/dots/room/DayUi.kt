package dev.dots.room

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable fun KeyboardButton(open:()->Unit,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val icon=remember{context.assets.open("bami/day/keyboard-v2.png").use{BitmapFactory.decodeStream(it)}.asImageBitmap()}
 IconButton(onClick=open,modifier=modifier.size(64.dp)){Image(icon,"바미에게 메시지 입력",Modifier.size(56.dp),filterQuality=androidx.compose.ui.graphics.FilterQuality.None)}
}
@Composable fun MessageComposer(s:ViewState,vm:BamiViewModel,close:()->Unit){
 val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.widthIn(max=680.dp).fillMaxWidth().padding(16.dp).imePadding(),shape=MaterialTheme.shapes.large,color=cream){Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("바미에게 메시지",fontFamily=Paperlogy,fontSize=18.sp)
   OutlinedTextField(value=s.draft,onValueChange=vm::draft,label={Text("메시지 입력")},modifier=Modifier.fillMaxWidth().focusRequester(focus),minLines=3,maxLines=7,enabled=!s.controls.sending,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Default))
   Text("${s.draft.codePointCount(0,s.draft.length)} / ${s.controls.maxTextLength}",fontSize=12.sp)
   if(!s.controls.canText)Text("브리지 연결 또는 업데이트가 필요해요. 입력한 내용은 유지해요.",fontSize=13.sp)
   if(s.controls.message.isNotBlank())Text(s.controls.message,fontSize=13.sp)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=close){Text("닫기")};Button(onClick=vm::sendText,enabled=s.controls.canText&&s.transport=="연결됨"&&!s.controls.busy&&s.draft.isNotBlank()){Text(if(s.controls.sending)"보내는 중…"else "보내기")}}
  }}
  LaunchedEffect(Unit){delay(150);focus.requestFocus();keyboard?.show()}
 }
}
