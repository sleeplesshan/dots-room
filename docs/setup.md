# 설치와 연결

공개본은 `dev.dots.room` / **Dots Room**이다. 다른 앱의 자격 증명, 캐시, ADB reverse를 재사용하지 않는다. 첫 공개는 소스 배포이며 APK와 Mac 런타임 묶음은 제공하지 않는다.

## 1. 빌드와 오프라인 데모

필요한 도구: Node **24**, JDK **17**, Android SDK platform **35** / build-tools **35.0.0**, Python **3.10 이상**, ADB. Android **8/API 26 이상**이 필요하다. macOS 실행 스크립트는 bash와 설치된 CLI를 사용한다. 공개 브라우저 어댑터의 지원 플랫폼은 macOS이며 Windows 실연동은 검증하지 않았다.

```bash
git clone https://github.com/sleeplesshan/dots-room.git
cd dots-room
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
npm ci --ignore-scripts
npm run build
npm test
npm run assets:prepare
npm run assets:check
# 설치한 JDK와 SDK에 맞게 JAVA_HOME / ANDROID_HOME을 설정한다.
apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

`assets/registry.json`은 원본 자산을 빌드용 `.cache/android-assets`로 연결한다. 자산 준비를 먼저 실행해야 한다. SDK·JDK를 저장소 안으로 다운로드하는 과정이나 특정 개인 경로는 요구하지 않는다.

```bash
adb devices -l
export ANDROID_SERIAL='YOUR_SELECTED_USB_SERIAL'
bash tools/macos/install.sh
```

홈/앱 목록의 **Dots Room**을 열고 오른쪽 위 `⋯` → **앱내 데모**를 선택한다. 데모는 페어링·로그인 없이 동작하고 실제 메시지를 보내지 않는다. `ShowcaseActivity`는 debug 빌드에만 포함된 별도 합성 화면이며 일반 앱의 연결 기능과 분리된다.

## 2. 로컬 설정

```bash
cp config.example.json config.local.json
```

`config.local.json`은 Git에서 제외된다. 다른 브리지가 기본 포트를 이미 사용한다면 비어 있는 별도 포트를 선택하고 앱 설정의 USB loopback 포트도 동일하게 맞춘다. 무선 게이트웨이의 기본 내부 포트는 8789이며 필요하면 `BAMI_GATEWAY_PORT`로 변경한다. `dotsUrl`에 **본인이 지정하는 Dots 대화 하나의 HTTPS 주소**를 넣는다. 빈 값은 데모에서만 허용한다. 실제 모드는 `/dots/<UUID>`의 정확한 URL만 허용하며 쿼리·해시·다른 도메인은 거부한다.

| 설정 | 의미 |
|---|---|
| `dotsUrl` | 수집·전송 대상 하나. 시작할 때 고정된다. |
| `egoBrowser` | 설치된 `ego-browser` CLI 이름 또는 실행 파일 경로 |
| `port` | loopback 브리지와 선택 USB reverse 포트. 기본 8788. 8765/8787은 보호한다. |
| `codexQuotas` | 기본 false. true이면 설치된 `codex app-server`의 **계정 한도**를 읽는다. |
| `notes` | 기본 null. 아래의 전용 Space 페이지 설정 |
| `weather` | 지역명·위도·경도·IANA 시간대. 기본은 서울 대표 지점이며 위치 권한이 필요 없다. |

## 3. 명시적 USB 페어링

새 앱과 선택한 USB 기기의 자격 증명 생성·저장은 사용자가 직접 승인한다. `ANDROID_SERIAL`을 지정하고 앱 설치를 확인한 후 실행한다.

```bash
bash tools/macos/pair-device.sh
# 화면의 확인 문구에 PAIR를 입력하면 이 새 앱에만 저장한다.
bash tools/macos/setup-usb.sh
```

Mac은 `~/.local/state/dots-room` (0700), 자격 증명 파일은 0600을 사용한다. debug 앱의 비공개 FIFO에 ADB **stdin**으로 가져오며 Android Keystore로 암호화한다. 토큰을 URL·로그·명령행 인자로 보내지 않는다. 공개 release APK는 이 debug USB 가져오기 기능이 없으므로 앱 설정의 비밀번호 입력 필드를 통한 수동 페어링이 필요하다. 이 저장소에는 어떤 자격 증명도 포함하지 않는다.

## 4. 실제 표시 대화 연결

**로그인된 Ego Browser와 그 CLI가 필요하다.** 일반 Chrome/Safari에 로그인한 것만으로 연결되지 않는다. Ego Browser는 Codex의 관리 브라우저 환경이며 설치·접근 가능 여부는 사용하는 환경에 달려 있다. 이 저장소는 로그인, 브라우저 프로필, 인증 쿠키, Codex 설정을 제공하거나 변경하지 않는다.

로그인이 유지되는 환경에서 다음을 실행한다.

```bash
bash tools/macos/start.sh browser-dots
# 종료: 이 패키지가 시작한 브리지·Serve와 선택 reverse만 정리
bash tools/macos/stop.sh
```

Ego의 전용 페이지에서 이미 표시된 최근 **50개**부터 읽는다. 이전 기록을 자동 스크롤해 불러오지 않는다. DOM 수정·이미지 로드를 관찰하고 변경만 전달한다. 주소 변경·로그인 만료·구조 변경·사용자 제어 전환은 수집/전송을 중단한다. 빠른 메시지와 자유 입력은 동일한 직렬 보호 장치를 사용하며 작성 중인 웹 초안을 덮어쓰지 않는다. 새 사용자 메시지가 실제 페이지에 나타나야 성공이다. 불확실한 전송은 자동 재시도하지 않는다.

모니터를 누르면 **동기화**, **무선 연결 설정**을 열 수 있다. 동기화는 현재 로드된 페이지를 다시 읽고 원자적 snapshot으로 맞춘다. 페이지 새로고침이나 초안 수정은 하지 않는다.

## 5. Tailscale로 태블릿만 들고 나가기

컴퓨터가 켜져 있고 브리지·로그인된 Ego 페이지가 살아 있어야 한다. Mac과 Android에서 Tailscale에 연결하고 tailnet의 HTTPS 인증서/Serve 사용 조건을 먼저 충족한다. 지원 문법은 TLS 종료 TCP와 PROXY v2가 있는 Tailscale CLI다. 포트가 이미 사용 중이거나 기존 Serve가 다르면 덮어쓰지 않는다. Funnel/공개 서버를 열지 않는다.

선택한 Android peer의 ID는 본인의 `tailscale status --json`에서 확인하고 **로컬에서만** 사용한다. 아래 값은 예시 자리표시자다.

```bash
export BAMI_TAILSCALE_CLI='tailscale'
bash tools/macos/wireless.sh prepare YOUR_ANDROID_PEER_ID
bash tools/macos/wireless.sh setup YOUR_ANDROID_PEER_ID
bash tools/macos/start.sh browser-dots
# 상태 확인 / 이 패키지 접속 지점만 해제
bash tools/macos/wireless.sh status
bash tools/macos/wireless.sh stop
```

준비 도구가 출력하는 본인 HTTPS 주소를 앱의 **모니터 → 무선 연결 설정 → Tailscale**에 넣는다. 게이트웨이는 선택한 peer IP와 앱 인증을 함께 검사한다. 기존 tailnet 정책도 유지된다. 외부 모바일 데이터 사용은 VPN·전원 절약·네트워크 상태에 영향을 받는다. 이 공개본으로 각자의 실제 tailnet·LTE에서 다시 검증해야 한다.

## 6. Space 업무 메모와 날씨

Space의 개인 메모 전체를 수집하지 않는다. **바미 업무 메모**라는 전용 페이지 하나를 만들고 다음 네 제목을 사용한다: **지금 하는 일**, **형아 확인이 필요한 일**, **오늘 끝낸 일**, **형아 자유 메모**. 추가로 **마지막 갱신 시각**을 적을 수 있다. 페이지를 수정하는 주체는 사용자이며 태블릿은 읽기 전용이다. 메모의 지시는 실행하지 않고 실제 작업 상태로 해석하지 않는다.

로그인된 Ego의 agent 소유 전용 공간에서 해당 페이지를 `p1`으로 열고, 환경에서 제공하는 공간 ID와 Page ID를 확인한 뒤 `config.local.json`의 `notes`를 `{ "pageId": "본인의 Page ID", "spaceId": 본인의_정수_공간_ID }`로 설정한다. 이 값은 로컬에만 둔다. 이 공개본은 페이지를 생성하거나 자유 메모를 자동 작성하지 않는다. 지원 DOM이 바뀌거나 접근할 수 없으면 마지막 정상 메모와 오류를 표시한다. 집은 **왼쪽 책장**, 회사/지하철은 메모 오브젝트를 눌러 상세를 연다.

메모 DOM 점검은 500ms, heartbeat는 5초, Android 확인은 5초 간격이다. 페이지 자체 수정 시각을 읽지 못하면 미확인으로 남기며 수집 시각과 구분한다. 날씨는 Mac에서 시작 시/15분마다 Open-Meteo를 조회하고 Android는 전면에서 1분마다 캐시를 확인한다. 45분 이상이면 오래됨, 3시간 이상이면 창밖은 중립 배경이다. 모든 장면의 창문을 눌러 상세와 출처를 확인한다. [Open-Meteo 문서](https://open-meteo.com/en/docs).

## 7. 개발 검사와 공개 감사

```bash
npm run public:check
python3 -m unittest discover -s tests -p 'test_*.py'
python3 tools/assets/character-pack.py apply assets/characters/bami --dry-run
# 별도 에뮬레이터에서만 합성 소개 화면 생성
apps/android/gradlew -p apps/android :app:assembleDebugAndroidTest
adb -s YOUR_EMULATOR install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_EMULATOR install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_EMULATOR shell am instrument -w -e class dev.dots.room.PublicShowcaseTest dev.dots.room.test/androidx.test.runner.AndroidJUnitRunner
# PNG는 앱의 비공개 files/showcase에서 읽는다. 실제 계정 화면은 사용하지 않는다.
mkdir -p docs/images
adb -s YOUR_EMULATOR exec-out run-as dev.dots.room cat files/showcase/home-landscape.png > docs/images/home-landscape.png
# 나머지 파일 이름은 PublicShowcaseTest.kt에 정의되어 있다.
python3 tools/capture-motion.py --serial YOUR_EMULATOR
```

공개 감사는 작업 파일, staged blob, 전체 게시 이력과 이미지 메타데이터를 검사하고 일치한 비밀 값은 출력하지 않는다. 자동 검사는 자산 권리·시각 품질의 사람 검수를 대신하지 않는다. [검증 기록](verification.md)에서 코드/합성/실제 계정/실기기 검사를 구분한다.
