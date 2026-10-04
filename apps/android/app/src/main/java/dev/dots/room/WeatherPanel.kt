package dev.dots.room

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun WeatherPanel(s:ViewState,now:ZonedDateTime){
 val w=s.weather;val r=w.reading;val context=LocalContext.current
 fun number(v:Double?,unit:String)=if(v==null)"미확인"else String.format(Locale.KOREAN,"%.1f%s",v,unit)
 fun date(v:String?)=v?.let{Instant.parse(it).atZone(ZoneId.of(w.region.timezone)).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))}?:"없음"
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text(w.region.name,fontSize=24.sp)
  if(r!=null){Text(WeatherVisual.label(r.code)+" · "+number(r.temperatureC,"°C"),fontSize=28.sp,color=navy);Text("체감 "+number(r.apparentC,"°C"));Text("최저 "+number(r.minC,"°C")+" / 최고 "+number(r.maxC,"°C"));Text("습도 "+number(r.humidityPercent,"%")+" · 강수 "+number(r.precipitationMm,"mm"));Text("바람 "+number(r.windMs,"m/s"))}else Text("날씨를 아직 읽지 못했어요")
  if(s.transport!="연결됨")Text("연결 끊김 · 마지막으로 읽은 날씨",color=MaterialTheme.colorScheme.error)
  else if(s.weatherReadError)Text("날씨 갱신 지연 · 마지막 정상값 유지",color=MaterialTheme.colorScheme.error)
  else if(WeatherVisual.stale(w,now.toInstant()))Text(if(r==null)"날씨 연결 대기"else "오래된 날씨 · 갱신 지연",color=MaterialTheme.colorScheme.error)
  if(r!=null&&!WeatherVisual.usable(w,now.toInstant()))Text("창밖은 시간대에 맞는 기본 풍경으로 표시해요",fontSize=14.sp)
  Text("날씨 기준: "+date(r?.validAt)+"\nMac 수집: "+date(w.fetchedAt)+"\n최근 수집 시도: "+date(w.attemptedAt),fontSize=14.sp)
  if(w.error !in listOf("","NOT_FETCHED"))Text(when(w.error){"TIMEOUT"->"날씨 제공처 응답 시간이 초과됐어요";"INVALID_RESPONSE"->"날씨 응답을 확인할 수 없어요";else->"날씨 제공처에 연결할 수 없어요"},fontSize=14.sp)
  Text("선택한 지역의 모델 기반 날씨예요. 관측소 실측값과 구분해 주세요.",fontSize=14.sp)
  Text("Open-Meteo · CC BY 4.0\n15분 갱신 · 단위 °C / mm / m/s",fontSize=12.sp)
  TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))}}){Text("날씨 출처 열기")}
 }
}
