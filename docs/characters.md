# 내 캐릭터 적용하기

바미 팩은 예제다. 본인 캐릭터의 참조 이미지와 사용 권리를 준비한 후 새로운 팩을 제작하면 된다. PNG 한 장만 바꾸면 보행·후면 작업·착석·수면까지 완성되는 구조는 아니다. 기본 팩은 **39 animation IDs / 211 frames / 11 RGBA atlases**로 나뉜다.

1. 캐릭터의 정면·후면·실루엣·색·팔다리 기준을 정한다.
2. [생성 프롬프트](sprite-prompts.md)로 동작별 시트를 제작한다.
3. 프레임을 **96×96 RGBA** 셀로 추출한다. 배경을 투명하게 하고 모든 셀의 몸 크기·중심·발 접점을 맞춘다.
4. `assets/characters/bami/manifest.json`을 복사해 새 팩의 상대 atlas 경로와 프레임 좌표를 입력한다. 기본 팩의 animation ID를 모두 유지한다.
5. contact sheet와 동작 GIF를 확인하고 자동 검사를 실행한다.
6. 공개 프로젝트에 적용·자산 준비·빌드 후 모든 장소와 착석을 확인한다. Codex PET 원본은 도구가 읽거나 변경하지 않는다.

```bash
python3 tools/assets/character-pack.py check path/to/my-pack
python3 tools/assets/preview.py path/to/my-pack
python3 tools/assets/character-pack.py apply path/to/my-pack --dry-run
python3 tools/assets/character-pack.py apply path/to/my-pack
npm run assets:prepare
apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleDebug
```

새 팩은 `{ "version": 1, "name": "my-character", "cellSize": 96, "animations": [...] }` 형태를 사용한다. 이름은 ASCII 영숫자·`-`·`_`만 사용한다. 적용 도구는 새 atlas를 `assets/characters/<name>`에 복사하고 장면 animation 참조와 registry를 갱신한다. dry-run은 파일을 바꾸지 않는다. Git diff를 확인하면 되돌릴 범위를 알 수 있다. 기존 설치 앱이나 PET 페어링에는 영향을 주지 않는다.

| animation 필드 | 의미 |
|---|---|
| `assetId`, `action`, `direction` | renderer가 사용하는 고정 ID, 동작, `N/NE/E/SE/S/SW/W/NW` 방향. 기본 팩의 대문자 표기를 그대로 사용한다. |
| `atlas` | 팩 폴더 안의 PNG 상대 경로. 외부 경로는 거부한다. |
| `frameRects` | `[x,y,96,96]` 목록. 셀 테두리와 atlas 범위를 지켜야 한다. |
| `frameDurationsMs` | 프레임별 양의 밀리초, frameRects와 길이가 같다. |
| `footAnchor` | 셀 내부의 발 접지점. 몸 중심이 아닌 발을 장면 위치에 맞춘다. |
| `interactionAnchor` | 잡기·상호작용 기준점. 손가락이 얼굴을 가리지 않게 정한다. |
| `collisionBounds`, `drawLayer` | 셀 내부 충돌 사각형 `[x,y,w,h]`와 캐릭터 레이어. 장면의 실제 가구 collider와 함께 검수한다. |
| `allowedFallback`, `sourceReference`, `generationProvenance` | 방향/동작 fallback과 원본·제작 기록. 기본 팩의 구조를 유지하고 새 팩의 권리와 제작 출처를 기록한다. |
| `loop` | 반복 여부. 깜박임·반짝임·전환 동작은 비반복으로 제작한다. |

장면 manifest는 `logicalSize`, 가구의 `rect` / `collider` / `anchor` / `frontAsset` / `backAsset`, waypoint와 전면 가림 레이어를 함께 정의한다. 앱은 동일 좌표 변환으로 그리기·터치·이동을 처리한다. nearest sampling을 유지하고 픽셀을 보간해 흐리지 않는다. 96px 셀이 고정되어도 얼굴/몸이 프레임마다 이동하면 공중에 뜨거나 발이 미끄러져 보인다.

자동 도구는 RGBA, 범위, 시간, anchor와 필수 ID를 검사한다. 눈·입·팔다리 수, 후면 가운데 불필요한 발, 가림·착석의 자연스러움은 실제 contact sheet와 앱에서 별도로 봐야 한다. generated image는 완벽한 픽셀 격자나 일관된 해부 구조를 보장하지 않는다. 새 팩의 생성 기록·출처·라이선스도 함께 남긴다. 기본 그림의 비상업 라이선스를 새 팩의 권리와 혼동하지 않는다.
