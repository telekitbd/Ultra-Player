# UltraPlayer

Android Gallery + Video Player (Kotlin, Jetpack Compose, libVLC).

## Features
- **Sob codec support**: libVLC engine (H.264/H.265/AV1/VP9, MKV/AVI/TS/FLV, AC3/DTS/FLAC ...), HW decode on, **4K/8K** ready
- Gallery: Videos/Photos, folder filter, search, sort, 4K/8K badge, photo viewer (pinch zoom, swipe, share)
- Player: swipe brightness/volume, double-tap seek, long-press 2x, lock, aspect ratio, 0.25x–4x speed,
  audio/subtitle track select, external subtitle, subtitle/audio delay, equalizer, A-B loop, repeat, sleep timer,
  resume position, network stream (URL), "Open with" support for other apps
- **Subtitle Studio**: video/URL → audio extract (any codec) → Whisper (Groq/OpenAI) → on-device translate
  (ML Kit, 50+ language, Bengali included) → export SRT / VTT / ASS / TXT → play with subtitle

## GitHub diye build
1. Notun repo banao, ei folder er sob file push koro (`main` branch)
2. Repo → **Actions** → *Build APK* (push korlei auto chole, na hole *Run workflow*)
3. Run shesh hole **Artifacts → UltraPlayer-APK** download koro (debug + release APK)

## Local build
```
gradle assembleRelease   # Android Studio / JDK 17 + Android SDK 34
```

## Subtitle Studio setup
- Groq free API key: console.groq.com → Settings te dao (Whisper large v3 turbo)
- Translation model prothombar internet diye download hoy, tarpor offline cholbe
