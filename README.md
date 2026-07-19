# EyeofAngra

A minimal, open-source Android app for emergency evidence capture — for people
whose safety, life, or freedom may be at risk. Everything stays on your device.

Android sibling of [LifeLine Recorder](https://github.com/HARSHBHINDER/LifeLineRecorder) (iOS).

## Features

Five destinations: Video, Audio, Photo, Vault, Settings.

- **Video** — live viewfinder, one capture control, elapsed timer, storage
  readout. Saved as `VID_YYYYMMDD_HHMMSS.mp4`. **Keeps recording with the
  screen locked** via a foreground service with an ongoing notification. Stops
  only when you return and press Stop.
- **Audio** — dedicated screen with a large timer and a level meter driven by
  real microphone amplitude. Saved as `AUD_YYYYMMDD_HHMMSS.m4a`. Also survives
  screen lock.
- **Photo** — full-bleed preview; **tap anywhere or press either volume key**
  to capture (`IMG_YYYYMMDD_HHMMSS.jpg`), built for unsteady hands. Status
  reports Saving, Saved, or Failed — never Saved before the file is finalised.
- **Vault** — everything captured, filterable by type, with a storage summary.
  Tap to play or view; delete asks for confirmation first.
- **Settings** — capture, recording behaviour, appearance, storage,
  permissions, and the full Safety & Legal text.

Only one recording runs at a time: video and audio both need the microphone,
and the app says so rather than letting the second attempt fail silently.

No cloud, no analytics, no accounts, no network permission at all.

Architecture, phased plan, permission matrix, storage model, and the recording
state machine are documented in [DOCS/PLAN.md](DOCS/PLAN.md).

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
