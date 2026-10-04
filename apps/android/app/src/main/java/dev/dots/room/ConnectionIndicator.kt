package dev.dots.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Transport and source are separate: a live USB bridge does not imply a live browser. */
@Composable fun ConnectionIndicator(s:ViewState,onConnect:()->Unit,onMode:()->Unit={},modifier:Modifier=Modifier) {
 val connecting=s.transport in listOf("연결 중","동기화 중","재동기화 중")||s.controls.syncing
 val bridge=s.paired&&!s.localDemo&&s.transport=="연결됨"
 val source=bridge&&s.data.sourceStatus.state in listOf("live","manual","demo")
 val label=if(source)"연결됨"else "연결 안 됨"
 Text(label,modifier
  .sizeIn(minWidth=48.dp,minHeight=48.dp)
  .clickable(enabled=!s.controls.busy,role=Role.Button,onClick=onMode)
  .wrapContentHeight(Alignment.Top)
  .semantics{
   contentDescription="연결 상태: $label. ${s.connectionMode}. 연결 설정 열기. 브리지: ${s.transport}. 대화 소스: ${sourceLabel(s.data.sourceStatus.state)}"
   customActions=listOf(CustomAccessibilityAction("다시 연결"){
    if(connecting||s.controls.busy)false else{onConnect();true}
   })
  },fontFamily=Paperlogy,fontSize=13.sp,color=if(source)Color(0xFF135B37)else Color(0xFF733A2B))
}
