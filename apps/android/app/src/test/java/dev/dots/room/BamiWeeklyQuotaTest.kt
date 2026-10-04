package dev.dots.room
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class BamiWeeklyQuotaTest {
 private val now=Instant.parse("2026-09-30T00:00:00Z")
 private fun quota(used:Double=95.0,minutes:Double=10080.0)=Quota("known",used,"official","account",now.toString(),"fresh",now.plusSeconds(6*86400+3600+100).toString(),"percent used","codex:primary","weekly",minutes,used)
 @Test fun showsWeeklyRemainingAndResetInsteadOfTokenUsage(){assertEquals("5% 남음",remainingPercentText(weeklyQuota(listOf(quota(10.0,300.0),quota()))));assertEquals("6일 1시간",resetCountdown(quota(),now));assertEquals("10월 6일 10:01",quotaResetDate(quota(),"Asia/Seoul"))}
 @Test fun knownZeroRemainingIsDistinctFromUnknown(){assertEquals("0% 남음",remainingPercentText(quota(100.0)));assertEquals("100% 남음",remainingPercentText(quota(0.0)));assertEquals("한도 정보 없음",remainingPercentText(null));assertNull(weeklyQuota(listOf(quota(minutes=300.0))));assertEquals("한도 정보 없음",remainingPercentText(quota().copy(knowledge="unknown",usedPercent=null)))}
 @Test fun resetCountdownNeverAssumesRestoredAllowanceAtExpiry(){assertEquals("초기화 확인 중",resetCountdown(quota().copy(resetAt=now.toString()),now));assertEquals("초기화 시간 확인 불가",resetCountdown(quota().copy(resetAt=null),now));assertEquals("오래된 값",quotaAnnotation(quota().copy(freshness="stale")));assertEquals("수동 입력",quotaAnnotation(quota().copy(origin="manual",freshness="manual")))}
 @Test fun expiryOrDisconnectedTransportMarksLastQuotaStale(){val q=quota();val s=ViewState(data=AppState(quotas=listOf(q)),transport="연결됨");assertEquals("fresh",weeklyQuotaForView(s,now)?.freshness);assertEquals("stale",weeklyQuotaForView(s.copy(transport="재연결 중"),now)?.freshness);assertEquals("stale",weeklyQuotaForView(s,now.plusSeconds(7*86400))?.freshness);assertEquals("초기화 확인 중",resetText(q.copy(resetAt=now.toString()),now));assertEquals("초기화 시간 확인 불가",resetText(q.copy(resetAt=null),now))}
}
