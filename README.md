# EyeofAngra

A minimal, open-source Android app for emergency evidence capture — for people
whose safety, life, or freedom may be at risk. Everything stays on your device.

Android sibling of [LifeLine Recorder](https://github.com/HARSHBHINDER/LifeLineRecorder) (iOS).

## Features

- **Video** — one-button evidence video (video + audio) saved as
  `VID_YYYYMMDD_HHMMSS.mp4`. **Keeps recording with the screen locked** (runs
  as a foreground service with a permanent notification). Stops only when you
  unlock and press Stop.
- **Audio** — same, audio-only, saved as `AUD_YYYYMMDD_HHMMSS.m4a`.
- **Photo** — black screen; **tap anywhere or press either volume button** to
  take an evidence photo (`IMG_YYYYMMDD_HHMMSS.jpg`) — built for shaking hands
  in tense situations. Brief "Photo captured" confirmation.
- **Safety & Legal** — what is recorded, local-only storage, your legal
  responsibility.

Tap any recording in a list to play/view it; trash icon deletes it. No cloud,
no analytics, no accounts, no third-party services.

## Install (free, no expiry)

1. On an Android phone, open this repo's
   [latest release](../../releases/tag/latest).
2. Download `app-debug.apk`.
3. Open it; allow "install from unknown sources" when Android asks.
4. First launch asks for camera, microphone, and notification permission.

Every push to `main` rebuilds the APK automatically
([`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)).

## Building locally

Android Studio (or plain Gradle 8.9 + JDK 17): open the project, run
`gradle assembleDebug`. No wrapper is checked in; CI installs Gradle itself.

## Reliability notes

- Some phone brands (Xiaomi, Huawei, some Samsung modes) aggressively kill
  background apps. For dependable lock-screen recording, exempt EyeofAngra
  from battery optimization: Settings → Apps → EyeofAngra → Battery →
  Unrestricted.
- Recording holds a wake lock; long sessions use battery accordingly.

## Transparency

Recording only ever starts from your explicit action. While recording, Android
shows a permanent notification plus its own camera/microphone indicators —
this app does not and will not hide them. It is not a covert surveillance
tool.

## Legal disclaimer

Laws on recording conversations and filming people vary by country and state
(one-party vs. all-party consent, etc.). You are solely responsible for using
this app lawfully. Recordings stay in the app's private folder until you
delete them. Provided as-is, without warranty — see [LICENSE](LICENSE).
