package dev.dots.room
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun noteTime(value:String?,zone:String)=value?.let{runCatching{Instant.parse(it).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm:ss"))}.getOrNull()}?:"확인 불가"
@Composable fun ExecutionPanel(s:ViewState){val e=s.desk.execution
 HorizontalDivider();Text(runtimeLabel(runtimeState(e,s.transport=="연결됨")),fontSize=17.sp)
 Text("출처: "+when(e.source){"fixture"->"테스트 데이터";"codex-app-server"->"Codex app-server";"browser-ui"->"브라우저 표시";else->"지원되는 원천 없음"},fontSize=14.sp)
 Text("마지막 확인: "+noteTime(e.observedAt,s.zone),fontSize=14.sp)
 if(e.knowledge!="known")Text("지정한 바미 Dots의 실제 실행 상태를 읽지 못했어요. 대화나 메모로 추측하지 않아요.",fontSize=14.sp)
 e.tasks.forEach{t->val current=runtimeState(e,s.transport=="연결됨")!="unknown"&&recentObservation(t.observedAt,Instant.now());Text((if(current)""else "마지막 관측: ")+runtimeLabel(t.state)+" · "+t.taskId+" · "+noteTime(t.observedAt,s.zone),fontSize=14.sp)}
}
@Composable fun WorkNotesPanel(s:ViewState){val n=s.desk.notes;val context=LocalContext.current
 Text("사용자가 수정하는 Space 메모 · 읽기 전용",fontSize=14.sp)
 if(n.status=="unconfigured")Text("Mac에서 전용 업무 메모 페이지 연결이 필요해요",fontSize=s.font.sp)
 if(n.status=="error"||n.freshness!="fresh"||s.transport!="연결됨")Text("갱신 지연 · 마지막 정상 메모를 유지하고 있어요"+(if(n.error.isNotBlank())" (${n.error})"else ""),fontSize=14.sp)
 if(n.status=="empty")Text("아직 적힌 업무 메모가 없어요",fontSize=s.font.sp)
 n.sections.forEach{section->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(section.title,fontSize=s.font.sp,color=navy);if(section.items.isEmpty())Text("—",fontSize=s.font.sp)else section.items.forEach{Text(it.text,fontSize=s.font.sp,lineHeight=(s.font*1.5).sp,fontFamily=Paperlogy)}}}
 Text("페이지 실제 수정 시각: "+noteTime(n.sourceUpdatedAt,s.zone),fontSize=14.sp)
 if(n.declaredUpdateText.isNotBlank())Text("페이지에 적힌 갱신 시각: "+n.declaredUpdateText,fontSize=14.sp)
 Text("Mac 수집기가 마지막 읽은 시각: "+noteTime(n.lastSuccessAt,s.zone),fontSize=14.sp)
 Text("실제 실행 상태와 별개인 메모예요",fontSize=14.sp)
 n.url?.let{url->OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}){Text("원본 Space 페이지 열기")}}
}
