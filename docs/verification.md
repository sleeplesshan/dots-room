# 공개본 검증 기록

검증일: 2026-10-05. 이 기록은 **Dots Room 공개 폴더의 코드**를 검사한 결과다. 기존 바미 앱의 검수 결과를 재사용하지 않았다. 공개본은 `dev.dots.room`, 별도 상태 경로와 새 Git 이력을 사용한다.

## 코드와 합성 데이터

| 검사 | 결과 | 범위 |
|---|---|---|
| 독립 npm 설치·TypeScript build | 통과 | Node 24.21.0, npm ci --ignore-scripts; 개인 설정 없이 실행 |
| 브리지 회귀 검사 | 통과 | 10 files / 78 tests: 계약 v2, replay/snapshot/chunk, revision, 지연 이미지, 중복 메시지, 전송 보호, sync, PROXY v2·peer 제한, 메모/날씨 캐시, 설정 대상·시간대 |
| Kotlin 단위 검사 | 통과 | 94 tests: reducer, 여러 작업, 상태/생활 분리, 장면 일정·배치·alpha 가림, 이동·착석·터치·집어 이동, 날씨·연결·화면 깨우기 논리 |
| Android debug / release APK build, lintDebug | 통과 | JDK 17 / SDK 35. lint 오류 0; 기존 스타일·의존성 갱신 등 경고는 남아 있음. APK는 게시하지 않음. |
| 캐릭터 팩 | 통과 | 39 animations / 211 frames / 11 RGBA atlases; 136 runtime assets 준비. 기본 팩 check / apply --dry-run, 실제 apply 회귀 2 tests 및 contact sheet·행동 GIF 생성 |
| 개인정보·비밀 검사 | 통과 | 작업 파일·staged·게시 이력과 이미지 metadata를 공개 감사 도구로 검사. 별도 공개 스킬 감사의 경고도 검토. |
| 소개 화면·실제 앱 UI 합성 검사 | 통과 | 별도 API 35 에뮬레이터에서 최종 native capture 1 test / 11 PNG. 60 native frames를 고정 200ms fixture 시각으로 촬영한 12초 GIF에서 한 번의 반짝임·말하기·생활 복귀를 육안 확인. 실제 FPS 측정은 아님. |

## 실제 연결과 지원 제한

| 기능 | 이번 공개본 판정 |
|---|---|
| 실제 Ego Dots 표시 대화 수집·전송·이미지·동기화 | 코드와 합성 검사는 통과. 새 공개 앱의 실제 계정/태블릿 연결은 **미검증**. 로그인된 Ego Browser 및 지원 DOM 필요. |
| 공식 Dots API | **미지원 / NOT_IMPLEMENTED** |
| 실제 Dots 실행 상태 | **미확인**. 자동 Codex 작업 연결을 활성화하지 않음. 대화/메모로 작업 완료를 추측하지 않음. |
| 전용 Space 수정·삭제·읽기 실패·빈 메모 | 합성 DOM/store 검사는 통과. 새 공개 앱의 실제 개인 페이지 연결은 **미검증**. 페이지 작성은 사용자, 기본 읽기 전용. |
| Open-Meteo 실제 API | **통과**. 공개 WeatherStore로 서울 대표 지점의 익명 실제 API 응답을 읽어 ready와 모델 시각을 확인. 합성 값·0·누락·오래됨과 사용자 설정 시간대 변환도 통과. |
| 새 공개 앱 실기기 USB / Tailscale / LTE / 화면 깨우기 | **미검증**. 기존 개인 설치 앱을 업데이트하거나 지속 페어링을 만들지 않음. |
| 30fps / p95 상태 반영 300ms / 8시간 soak | **미검증**. 빌드·정지 캡처·짧은 GIF를 성능/장시간 검사로 대신하지 않음. |
| GitHub Actions | **통과**. [공개 Linux CI 실행](https://github.com/sleeplesshan/dots-room/actions/runs/37215774669), 소스 커밋 `fd75654`: bridge-assets와 android 모두 성공. 브리지·자산·문서·공개 감사 및 Kotlin 단위 검사·debug/release 빌드·lint를 실행. 실제 로그인/태블릿 검사는 CI와 분리됨. |

## 재현과 읽기 경로

- [설치 명령](setup.md), [캐릭터 팩](characters.md), [제작 템플릿](sprite-prompts.md).
- 첫 CI의 Android SDK 준비는 폐기된 `tools` 패키지 요청으로 실패했다. `platform-tools`만 설치하도록 수정한 뒤 위 실행에서 통과했다. 이후 문서·라이선스 고지만 정리하는 커밋에는 `[skip ci]`를 사용하며 앱·브리지·자산 코드는 검사한 소스와 동일하다.
- 브리지 검사는 파일 단위로 순차 실행한다. DOM 200ms 묶음은 고정 시각으로 검사하고, 복구 fixture는 동일 snapshot을 재전달한다. 촬영·빌드와 함께 실행했을 때 발생한 시간 제한 실패 후 자원 부하를 줄여 최종 78 tests를 통과했다. 이 논리 검사를 실제 브라우저 반영 지연 측정으로 해석하지 않는다.
- 모든 소개 화면은 고정 합성 대화·메모·한도·날씨·시각을 실제 Compose/Canvas UI에 공급한다. 실제 대화 캡처에 텍스트를 덮어쓰지 않는다. 초기 전체화면 안내와 촬영 환경의 System UI 오류에 가린 캡처는 폐기하고 다시 촬영했다. 활성 창과 화면 크기·픽셀 검사를 통과한 앱 화면만 사용한다.
- 브라우저 메시지는 DOM 변경을 최대 200ms 묶음으로 관찰한다. 프로세스 전달은 비공개 FIFO이고 기본 로그에 본문·이미지·토큰을 남기지 않는다. 최근 로드된 50개를 읽으며 전체 기록 자동 탐색은 하지 않는다.
- WS heartbeat 5초 / stale 15초, bounded replay와 atomic snapshot을 사용한다. 수동 sync는 화면 새로고침이나 웹 초안을 바꾸지 않는다. uncertain send는 자동 재전송하지 않는다.
- 메모 DOM 확인 500ms / heartbeat 5초 / Android 확인 5초. 실제 사용자 수정 → 태블릿의 반영 지연은 이 공개본에서 측정하지 않아 수치로 주장하지 않는다.
- 날씨 Mac 15분 / Android 전면 1분, stale 45분 / 중립 풍경 3시간. 모델 기준 시각과 수집 시각을 분리한다. HTTP 조회가 매번 upstream을 조회하지 않는다.
- 기존 USB 앱·서비스·PET·개인 폴더·Git 이력·다른 ADB 매핑은 공개 작업에서 수정하지 않았다.
