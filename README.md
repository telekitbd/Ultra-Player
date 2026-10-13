# UltraPlayer

Android Gallery + Video Player (Kotlin, Jetpack Compose, libVLC).

## Features
- **Sob codec support**: libVLC engine (H.264/H.265/AV1/VP9, MKV/AVI/TS/FLV, AC3/DTS/FLAC ...), HW decode on, **4K/8K** ready
- Gallery: Videos/Photos, folder filter, search, sort, 4K/8K badge, photo viewer (pinch zoom, swipe, share)
- Player: swipe brightness/volume, double-tap seek, long-press 2x, lock, aspect ratio, 0.25x–4x speed,
  sensor auto-rotation, audio/subtitle track select, external subtitle, subtitle/audio delay, A-B loop, repeat,
  resume position, network stream (URL), "Open with" support for other apps
- **Subtitle Studio**: video/URL → audio extract (any codec) → Groq Whisper (free tier; usage limits apply), timestamped Gemini 3.8 Flash audio transcription with automatic Groq fallback,
  or on-device Whisper → free on-device translation → export SRT / VTT / ASS / TXT
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
- Gemini transcription-e WAV audio chunk upload kore timestamped subtitle segment generate hoy. Gemini transcription error ba empty response dile Groq Whisper automatically try korbe; Gemini select thaka obosthay optional Groq fallback API key dao. Groq free-tier usage limits apply.
- Generated subtitles are translated on-device with Google ML Kit; no translation API key or charge is needed. The source/target language model downloads on first use and then works offline. Select the spoken language manually if automatic language detection fails.
- On-device Whisper prothombar ekti ~78 MB model download kore; erpor transcription offline cholbe.
- Gemini audio uploads are temporarily stored by Google and expire according to Google's file-retention policy. On-device translation is machine translation and may not preserve all dialogue nuance.
