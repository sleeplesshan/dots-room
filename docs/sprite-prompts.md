# 스프라이트 생성 프롬프트

이 문서는 imagegen 등 참조 이미지 생성 도구에 사용할 **재사용 가능한 제작 템플릿**이다. 배포 팩의 모든 프레임을 이 문장 하나로 다시 얻는다고 보장하지 않는다. 원본 캐릭터와 기존 방은 변경하지 않고 별도 결과를 만든다.

## 공통 기준

아래 공통 문장을 동작 프롬프트 앞에 붙이고 `[CHARACTER]`를 본인 캐릭터 기준으로 바꾼다.

> Use the attached reference as the identity source for [CHARACTER]. Preserve its exact colors, silhouette, proportions and clean 2D pixel-art style. Create isolated RGBA sprites with a transparent background, no scenery, no captions, no hearts, no watermark. Each final frame occupies a 96 × 96 cell with transparent margins. Keep body size, center, baseline and the same foot anchor stable across frames. Exactly the reference limb count: for the Bami example, two arms and two legs. No extra central foot in rear views. Stable face and closed neutral mouth when not talking. Blink both eyes together; no repeated winking. White book for the Bami example. Use crisp pixel edges suitable for nearest-neighbor sampling. Leave clear separation between cells.

생성 결과의 실제 크기와 그리드를 확인한 뒤 셀을 추출한다. 도구가 정렬을 완전히 맞추지 못하면 atlas에 바로 등록하지 말고 프레임과 anchor를 교정한다.

## 방향·보행

> Make eight neutral poses facing N, NE, E, SE, S, SW, W, NW, one pose for each direction. Preserve the same camera, scale and baseline. Rear views show a natural back with only the two correctly placed feet.

> For each of the eight directions, make an eight-frame walking cycle. Alternate two feet with subtle body movement and stable stride length. No skating, growth, rotation drift or added limbs. First and last frames should connect cleanly without a visible jump. Make one direction per sheet so consistency can be reviewed.

## 차분한 생활과 대화

> Make 12 breathing frames for a calm 4.8-second loop. Barely move the chest/body; keep the face, mouth and feet stable. This is a quiet resting pose, not a bouncing dance.

> Make six non-looping blink frames. Both eyes close and open together. Neutral mouth, stable cheeks and body. The app plays this once at intervals, rather than looping it continuously.

> Make 12 look-around frames with small glances left and right and a return to neutral. Do not spin the entire body or slide the feet.

> Make 12 calm talking frames. Use small, clean mouth changes without stretching the face. Keep two arms and two legs stable. A short reply will use this for about five seconds and then transition back to everyday behavior.

## 가구·작업·휴식

> Make a quiet reading loop holding a pure white book. Two arms naturally support the book; no extra hands. Keep the book color and shape identical in every frame.

> Make rear-facing computer work frames: walk into position, align to the desk, sit, type calmly, stop, stand and walk away. Keep the chair/desk out of character sprites because the app renders them separately. No central third foot. Provide transition frames and stable seat and foot anchors.

> Make seated resting frames and sit/stand transitions. For bed use, make lying-down, sleep-breathing and rising frames aligned to a pillow. Do not paint a bed or blanket into the character: the scene provides a separate foreground blanket layer. Two legs and two arms remain consistent; no furniture intersection.

## 터치 반응

> Make a curious pose with a calm questioning expression; keep the question-mark effect separate from the face. Make picked-up struggling frames with small leg kicks and two arms, aligned to a fixed grip anchor above the body. Make a dizzy expression and separate small star effects without violent spinning or changing proportions. Keep transparent cell margins.

> Make six frames of a warm yellow shimmer around the same character for a single 600ms reply-introduction effect. Begin with no highlight, brighten gently once, then return to the unchanged blue base color. No hearts or looping flashing. The app plays this only once per new assistant message ID.

## 방·동료·검수

배경은 집·회사·아침/저녁 지하철의 기존 원근과 palette를 기준으로 제작한다. 창틀/유리 마스크, 가구 앞/뒤, 이불과 그림자 레이어를 분리한다. 날씨·시간·날짜·한도·업무 문구는 이미지에 굽지 않고 동적 텍스트로 그린다. 회사 동료도 각각의 색·실루엣을 유지하고 정면 휴식·독서·후면 업무·커피·인사 프레임을 고정 발 접점으로 만든다.

최종 폴더에는 atlas PNG, manifest, 참조 이미지의 출처/권리, 사용한 프롬프트, contact sheet, 동작 GIF, 검사 기록을 남긴다. contact sheet에서 얼굴과 팔다리, GIF에서 loop 경계, 앱에서 책상 가림·의자 앉기·침대 이불·보행 충돌을 확인한다. 단순히 프레임 수가 많아졌다는 이유로 자연스러움을 통과 처리하지 않는다.
