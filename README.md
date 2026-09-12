# RespawnSuite

An Android scorekeeper for airsoft and milsim games. It turns a spare phone into
a field terminal: bolt it to a box, plug in a USB-C numeric keypad, pair a
Bluetooth speaker, and it runs a round on its own — objective timers, capture
codes, music, countdown cues and a results board, with no touchscreen needed.

The whole UI is green-on-black monospace and is driven entirely from the keypad,
because the phone is usually behind a plastic window with gloves on the other
side of it.

```
┌──────────────────────────────────────────────┐
│  RESPAWN_SUITE                        [WIDE] │
│                                              │
│  > MODULE_01  KING_OF_THE_HILL   HOLD/TIMER  │
│    MODULE_02  COUNTDOWN          HOLD/FINAL  │
│    MODULE_03  SEARCH_AND_DESTROY BOMB/45s    │
└──────────────────────────────────────────────┘
```

## Game modes

| Module | Mode | How it is won |
| --- | --- | --- |
| 01 | `KING_OF_THE_HILL` | Most banked hill time when the round clock runs out. |
| 02 | `COUNTDOWN` | Whoever is holding the hill at zero. |
| 03 | `SEARCH_AND_DESTROY` | Attack detonates, or defence defuses. |

**King of the Hill / Countdown.** Each team gets a code. Typing another team's
code takes the hill from them and starts banking time for you. A team's code is
hidden while they hold the hill and is only reissued once it has actually been
used against them, so the other codes stay valid all round. Two to six teams
(`ALPHA` … `FOXTROT`), a configurable pre-start countdown so everyone can reach
their positions, and a round timer of 1–99 minutes.

**Search and Destroy.** Attack carries the phone to a site and types the arm
code. That starts the fuse and reveals the disarm code for defence to find and
type. A part-typed arm code has ten seconds to be finished — otherwise it is
scrambled and reissued, so a bomb abandoned mid-plant is no use to whoever picks
the phone up next. The fuse (30–45s) is exactly as long as the cue that plays
over it, so what players hear and what the screen says can never disagree. An
armed bomb flashes an amber border.

## Controls

Everything is reachable from a bare numeric keypad; the number row of a full
keyboard works too.

| Key | What it does |
| --- | --- |
| `8` `2` `4` `6` | Up / down / left / right — the ring around `5` stands in for an arrow cluster |
| `+` `-` | Change the highlighted setting |
| `0`–`9` | Digits, on any screen that is taking a code |
| `.` / `Backspace` | Clear the typed digits |
| **hold** `Enter` | Confirm — launch a round from setup, end a round, leave the results |
| **hold** `0` | Run the same round back from the results |

Holds are deliberate rather than tapped: a thin progress bar fills under a short
caption, and the action only fires when the bar completes. Real USB keypads
report a hold as one keystroke, a pause, then a burst of repeats, so the input
layer debounces releases instead of trusting auto-repeat.

## Audio

Sound is the point — players are looking at cover, not at the phone.

- **Tones.** A small synthesiser (`ToneEngine`) drives the interface beeps and
  the idle heartbeat, running a continuous PCM pump so the cues land on time
  instead of at the mercy of AudioFlinger's buffer scheduling.
- **Music mode.** In hill modes, each team can be given a track; whoever holds
  the hill is who you hear. The music fades out over the last thirty seconds so
  the countdown cue has the field to itself.
- **Cues.** A 45-second countdown, a detonation and a defusal. A shorter fuse
  starts the countdown cue partway in, so its ending still lands on the bang.
- **Silence.** Nothing sounds while the screen is off, the app is backgrounded,
  or something else has the foreground (a notification shade, a system dialog).
  The round keeps its own wall-clock time regardless — only the speaker waits.

### Adding music

Tracks are read from `app/src/main/assets/music/` at runtime, so dropping an
`.mp3` in is the whole procedure — no code change, no rebuild of any list. The
filename is what shows up on the setup screen and in `NOW_PLAYING`, so name them
in caps: `DOOM_OST.mp3` reads as `DOOM_OST`.

No music ships in this repository. Bring your own tracks and mind the licensing
of anything you play in public. With the folder empty the app simply offers no
music mode; everything else runs as normal.

## Kiosk behaviour

From the moment a mode is chosen through to the results, the app pins itself:
system bars hidden, Home, Recents and Back blocked, screen kept awake. The main
menu is deliberately left unpinned so the phone can still be put away.

On an ordinary install the system asks for confirmation the first time each
launch. On a phone dedicated to scorekeeping, provision it once over adb with no
accounts signed in and the pin becomes silent:

```bash
adb shell dpm set-device-owner com.example.respawnsuite/com.example.respawnsuite.kiosk.KioskAdminReceiver
```

The app launches in landscape by default — that is how a mounted phone sits —
and `AUTO` / `TALL` / `WIDE` is one keypress away on the menu. Every screen has a
layout for both orientations, and a round in progress never re-orients itself.

## Building

Android Studio, or from the command line:

```bash
./gradlew assembleDebug
```

Kotlin + Jetpack Compose (Material 3), `minSdk` 31, single activity, no
dependency injection and no persistence — a round lives in snapshot state and
dies with the app, which is all a match needs.

```
app/src/main/java/com/example/respawnsuite/
├── audio/      tone synthesis, music and cue playback, the silence rule
├── game/       round config and the two engines (koth/, sad/)
├── input/      keypad vocabulary, navigation mapping, hold debouncing
├── kiosk/      device-owner receiver for silent pinning
└── ui/         screens/, components/, theme/, orientation, screen lock
```

The engines are plain classes over `mutableStateOf`, ticked at 200 ms against
`SystemClock.elapsedRealtime()` deltas rather than counted frames, so a long
round cannot drift.
