# Dots Room

**Give your Dots a small pixel home on an otherwise unused Android tablet.**

[한국어](README.md) · [Setup guide (Korean)](docs/setup.md) · [Character packs](docs/characters.md) · [Verification](docs/verification.md)

![Home and conversation side by side](docs/images/home-landscape.png)

*Every screenshot uses fixed synthetic conversation, notes, quota, weather and time data. These are captures of the actual Android UI in a separate offline demo environment.*

## From a message display to a place to live

This started as a way to stop missing Dots messages while working. An unused tablet could become a small companion display: wake when a message arrives, show the reply, and get out of the way.

The unexpected part was how nice it felt when a dark screen suddenly lit up and Dots started talking. A room where the character could read, rest, play and spend the day seemed more fun than messages alone. It grew into a little Tamagotchi-like space with a home, an office and morning/evening subway rides.

The default character is **Bami**, a small blue companion. A new reply brings it forward for a single yellow shimmer and a short talking animation. During quiet periods it can read a white book or wander slowly. These are **conversation reactions and decorative everyday behavior**. They do not claim to reveal actual Dots execution state.

## A day together

| Office | Morning commute | Evening commute |
|---|---|---|
| ![Office](docs/images/office.png) | ![Morning](docs/images/subway-morning.png) | ![Evening](docs/images/subway-evening.png) |

In the configured timezone, weekdays follow **07:00–08:00 morning subway → 08:00–17:00 office → 17:00–18:00 evening subway → home**. Weekends stay at home. Coworkers work, have coffee and greet one another. Tap characters to interact, or hold Bami to pick it up and move it.

![Reply shimmer and talking](docs/images/reply-motion.gif)

*A synthetic new reply played through the real renderer's shimmer, speech and everyday transition path. Native captures use fixed 200ms fixture-clock steps; this is not an FPS measurement.*

## A quick tap or a typed message

| Quick conversation menu | Direct typing |
|---|---|
| ![Quick chat](docs/images/quick-chat.png) | ![Typing](docs/images/typing.png) |

Tap Bami once for a curious expression and `?`, then again for five presets: ask for a briefing, say thanks, say stop, check back later, or continue. The circular keyboard button beneath the conversation opens a multiline composer.

Messages go through the input field of the one configured Dots web page. Existing web drafts are preserved. A send is confirmed only after a new user message actually appears on that page, and the tablet displays the collected result rather than an optimistic duplicate. Image replies are transferred as PNG, displayed in bubbles and opened larger on tap. Reading older messages does not force auto-scroll.

## Useful objects in the room

| Weekly quota, sync and connection settings | Dedicated Space notes | Weather outside |
|---|---|---|
| ![Quota](docs/images/weekly-limit.png) | ![Notes](docs/images/work-notes.png) | ![Weather](docs/images/weather.png) |

- **Monitor:** remaining weekly percentage and reset time from an available Codex **account** quota, manual synchronization, and USB/Tailscale settings.
- **Bookshelf / note object:** read-only display of a user-edited dedicated Space work-notes page.
- **Clock and calendar:** visible in the scene and available as larger widgets.
- **Windows:** model-based Open-Meteo weather for a configured region, with source and freshness details. The default is a representative point in Seoul.

Unknown, zero, manual and stale values are distinct. Writing “working” in a note does not create an execution-state event.

| Portrait split | Portrait scene only |
|---|---|
| ![Portrait split](docs/images/portrait-split.png) | ![Full scene](docs/images/portrait-scene.png) |

Landscape keeps the scene and conversation **50:50**. Portrait supports **scene only / conversation only / vertical split**. Scene-only mode has no overlapping RPG dialogue box.

## Take the room outside

With the computer on, the bridge running and **Ego Browser** logged in, Tailscale can connect the tablet away from home. Carry the tablet and talk from this little Dots space instead of opening the usual GPT app. The private gateway checks both the selected tablet peer and application authentication. See the [setup guide](docs/setup.md).

Collection currently reads **messages displayed in a logged-in Ego Browser**. Signing into an ordinary browser is not enough. The official Dots API adapter is **NOT_IMPLEMENTED**, and support for actual Dots execution state has not been verified. Collection begins with the latest 50 already loaded messages; it does not automatically retrieve the entire history. Screen wake is implemented but depends on Android battery, VPN and background restrictions. The weekly indicator is a **Codex account limit**, not a Dots-specific quota.

## Bring your own character

Generate sheets from your own character references, then supply the atlas and manifest. A replacement needs **96×96 RGBA cells, directional walking, foot anchors, frame timing and furniture layers**; one PNG is not enough for every pose. [Character pack instructions](docs/characters.md) and [sprite prompts](docs/sprite-prompts.md) cover breathing, blinking, looking around, talking, reading, rear-facing computer work, sleep, pickup reactions and shimmer. The tools do not modify an installed Codex PET.

## Build and layout

Requires Node 24, JDK 17, Android SDK 35 and Python/Pillow.

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
npm ci --ignore-scripts
npm run build
npm test
npm run assets:prepare
npm run assets:check
apps/android/gradlew -p apps/android :app:assembleDebug
```

The in-app demo needs no account. Real collection uses local configuration and explicit pairing as described in [setup](docs/setup.md). Local configuration, browser profiles and credentials are excluded from the repository.

```text
apps/android/      Kotlin / Compose tablet application
bridge/            TypeScript authenticated WS and browser adapters
protocol/          Shared v2 contract and synthetic fixtures
assets/            Character, scene and UI artwork / manifests
tests/             Bridge regression tests
tools/             Launch, pairing, assets and public audit
docs/              Setup, prompts, images and verification
```

Code and documentation: [MIT](LICENSE). Provided artwork: [CC BY-NC 4.0](assets/LICENSE). Paperlogy fonts: [SIL OFL 1.1](licenses/Paperlogy-OFL.txt). See [third-party notices](THIRD_PARTY_NOTICES.md). This is independent of OpenAI, ChatGPT, Codex and Tailscale. This first publication contains source, documentation and assets; no personal APK, Mac distribution ZIP or GitHub Pages site is published.
