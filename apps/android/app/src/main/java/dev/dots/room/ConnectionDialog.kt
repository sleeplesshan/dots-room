package dev.dots.room

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun ConnectionDialog(s:ViewState,vm:BamiViewModel,close:()->Unit){
 var mode by remember{mutableStateOf(s.connectionMode)}
 var address by remember{mutableStateOf(s.tailscaleAddress)}
 AlertDialog(onDismissRequest=close,title={Text("바미 무선 테스트 연결")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row{listOf("USB","Tailscale").forEach{option->Row{RadioButton(selected=mode==option,onClick={mode=option},enabled=!s.controls.busy);TextButton(onClick={mode=option},enabled=!s.controls.busy){Text(option)}}}}
  if(mode=="USB")Text("USB 케이블을 연결하고 Mac에서 선택 태블릿의 새 reverse ${s.port} 설정을 실행해 주세요.")
  else{OutlinedTextField(address,{address=it},singleLine=true,label={Text("Tailscale HTTPS 주소")},placeholder={Text("https://example-host.tailnet.ts.net:8788")},enabled=!s.controls.busy);Text("태블릿의 Tailscale VPN을 켜고 Mac도 온라인 상태인지 확인해 주세요. 앱 인증은 두 모드에서 유지돼요.")}
  Text("브리지: ${s.transport}\n대화 소스: ${sourceLabel(s.data.sourceStatus.state)}")
  if(s.error.isNotBlank())Text(s.error,color=MaterialTheme.colorScheme.error)
 }},confirmButton={TextButton(onClick={val unchanged=mode==s.connectionMode&&address.trim()==s.tailscaleAddress;if(vm.configureConnection(mode,address)){if(unchanged)vm.connectSaved();close()}},enabled=!s.controls.busy){Text("연결하기")}},dismissButton={TextButton(onClick=close){Text("닫기")}})
}
