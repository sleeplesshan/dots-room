package dev.dots.room
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

fun weeklyQuota(quotas:List<Quota>):Quota? = quotas.firstOrNull{it.windowDurationMins==10080.0&&it.limitId.substringBefore(':')=="codex"}?:quotas.firstOrNull{it.windowDurationMins==10080.0}
fun remainingPercent(q:Quota?):Double?=q?.takeIf{it.knowledge=="known"}?.usedPercent?.let{(100-it).coerceIn(0.0,100.0)}
fun remainingPercentText(q:Quota?):String=remainingPercent(q)?.let{(if(it%1==0.0)it.toInt().toString()else String.format(Locale.KOREAN,"%.1f",it))+"% 남음"}?:"한도 정보 없음"
fun resetCountdown(q:Quota?,now:Instant=Instant.now()):String {
 val reset=q?.resetAt?.let{runCatching{Instant.parse(it)}.getOrNull()}?:return "초기화 시간 확인 불가"
 val seconds=Duration.between(now,reset).seconds
 if(seconds<=0)return "초기화 확인 중"
 if(seconds<60)return "1분 미만"
 val minutes=seconds/60;if(minutes<60)return "${minutes}분"
 if(minutes<1440)return "${minutes/60}시간"+(if(minutes%60>0)" ${minutes%60}분"else "")
 val hours=seconds/3600;return "${hours/24}일"+(if(hours%24>0)" ${hours%24}시간"else "")
}
fun quotaResetDate(q:Quota?,zone:String):String?=q?.resetAt?.let{runCatching{Instant.parse(it).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))}.getOrNull()}
fun quotaAnnotation(q:Quota?):String?=when{q?.freshness=="stale"->"오래된 값";q?.origin in listOf("mock","demo")->"데모";q?.freshness=="manual"||q?.origin in listOf("manual","user")->"수동 입력";else->null}

fun weeklyQuotaForView(s:ViewState,now:Instant=Instant.now()):Quota?{val q=weeklyQuota(s.data.quotas)?:return null;val expired=q.resetAt?.let{runCatching{!Instant.parse(it).isAfter(now)}.getOrDefault(false)}?:false;return if(q.freshness=="fresh"&&((!s.localDemo&&s.transport!="연결됨")||expired))q.copy(freshness="stale")else q}
fun resetText(q:Quota?,now:Instant=Instant.now(),short:Boolean=false):String{val count=resetCountdown(q,now);return if(count.startsWith("초기화"))count else if(short)"$count 후 초기화"else "초기화까지 $count 남았습니다"}
