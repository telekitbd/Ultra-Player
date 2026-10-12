# UltraPlayer

Android Gallery + Video Player (Kotlin, Jetpack Compose, libVLC).

## Features
- **Sob codec support**: libVLC engine (H.264/H.265/AV1/VP9, MKV/AVI/TS/FLV, AC3/DTS/FLAC ...), HW decode on, **4K/8K** ready
- Gallery: Videos/Photos, folder filter, search, sort, 4K/8K badge, photo viewer (pinch zoom, swipe, share)
- Player: swipe brightness/volume, double-tap seek, long-press 2x, lock, aspect ratio, 0.25x–4x speed,
  sensor auto-rotation, audio/subtitle track select, external subtitle, subtitle/audio delay, A-B loop, repeat,
  resume position, network stream (URL), "Open with" support for other apps
- **Subtitle Studio**: video/URL → audio extract (any codec) → Groq, Gemini 3.8 Live streaming transcription,
  or on-device Whisper → context-aware Gemini translation into natural Bengali → export SRT / VTT / ASS / TXT
  → play with subtitle

## GitHub diye build
1. Notun repo banao, ei folder er sob file push koro (`main` branch)
2. Repo → **Actions** → *Build APK* (push korlei auto chole, na hole *Run workflow*)
3. Run shesh hole **Artifacts → UltraPlayer-APK** download koro (release APK)

## Local build
```
gradle assembleRelease   # Android Studio / JDK 17 + Android SDK 34
```

## Subtitle Studio setup
- Groq ba Gemini API key provider select korar por dao.
- Groq ba on-device transcription-er shathe context-aware translation chalate Translation settings-e Gemini API key dao.
- Gemini Live transcription streams audio in real time; its subtitle segment timing is estimated.
- On-device Whisper prothombar ekti ~78 MB model download kore; erpor transcription offline cholbe.
- Gemini translation-e neighboring subtitle dialogue context hisebe use hoy, Bangla-ke aro natural o bujhte shohoj rakhar jonno.
