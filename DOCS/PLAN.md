# EyeofAngra — Implementation Plan

Emergency evidence camera. Local-first, offline, no network permission.
Recording always requires deliberate user action and is always visibly indicated.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Working capture core: video+audio / audio-only foreground service, photo, file store | **Done** (pre-existing, retained) |
| 1 | Design tokens, theme, app shell, 5 destinations, real Vault + Settings, brand icons | **Done** |
| 2a | Launcher icon, onboarding, contextual permission rationale | **Done** |
| 2b | Capability repository, device compatibility screen, onboarding security step | Not started |
| 3 | Photo polish: focus, zoom, exposure, flash/torch split, timer, aspect ratio | Not started |
| 4 | Video polish: quality selection, pause/resume, notification actions, recovery | Not started |
| 5 | Audio polish: level meter, quality presets, markers, segmentation | Not started |
| 6 | Vault detail screen, notes/tags, Recently Deleted, explicit export via SAF | Not started |
| 7 | Biometric lock, PIN fallback, auto-lock, screenshot blocking, SHA-256 integrity | Not started |
| 8 | Accessibility sweep, adaptive layouts, tests, docs, release readiness | Not started |

Each phase must leave the repo compiling. CI (`.github/workflows/build-apk.yml`)
is the build verification: it produces an installable APK on every push to `main`.

## Architecture

Single Gradle module, package-separated. No multi-module split — this app has one
team, one shipping surface, and module boundaries here would cost build complexity
without buying isolation.

```
com.eyeofangra.app
  ui/theme        design tokens, Material 3 theme mapping
  ui/components   reusable composables (capture button, brand icons, rows)
  ui/Shell.kt     app shell, bottom navigation, destination host
  feature/video   video capture screen
  feature/audio   audio capture screen
  feature/photo   photo capture screen
  feature/vault   media list, filters, storage summary
  feature/settings settings screens, preference persistence
  RecorderService foreground service, authoritative recording state
  RecordingStore  file naming, listing, deletion, open-with
  Settings.kt     DataStore-backed preferences
```

Composables render state and emit events. Recording state is owned by
`RecorderService` and observed as a `StateFlow` — never mirrored in screen-local
state, so the UI cannot claim Idle while the service is still recording.

### Deliberate deviations from the brief

These are documented, not accidental. Each names the trigger to revisit.

- **No Hilt.** Four collaborators, all constructible without a graph. Hilt would add
  annotation processing and build time for no wiring benefit at this size.
  *Add when:* the object graph exceeds roughly a dozen injected types.
- **No Room yet.** Files on disk are currently the index — name carries the timestamp,
  the filesystem carries size and duration. A database would introduce DB/disk drift
  as a new failure mode with nothing to store that disk does not already hold.
  *Add in phase 6*, when notes, tags, favourites, integrity digests, and Recently
  Deleted state need somewhere to live that is not the filename.
- **No Navigation Compose yet.** Five sibling destinations with no back stack between
  them; a saved index and a `when` is the whole requirement.
  *Add in phase 6*, with the media-detail screen — the first real push destination.
- **No `material-icons-extended`.** That artifact costs roughly 10 MB for five icons.
  Brand icons are drawn with Canvas primitives in `ui/components/BrandIcons.kt`,
  which also gives the design system its own iconography rather than stock Material.

## Screen map

```
Shell (bottom navigation, 5 destinations)
├── Video     preview · capture control · elapsed · recording banner
├── Audio     elapsed · level state · start/stop · recent list
├── Photo     preview · shutter · tap-anywhere · volume-key capture
├── Vault     all/video/audio/photo filters · storage summary · open · delete
└── Settings  capture · recording behaviour · appearance · about & legal
```

Phase 6 adds `Vault → MediaDetail` as the first pushed destination.

## Recording state machine

`RecorderService` is authoritative. Permitted transitions:

```
Idle ──start──▶ Starting ──▶ Recording ──stop──▶ Finalizing ──▶ Idle
                    │                                 │
                    └────────── Failed ◀──────────────┘
```

Phases 4–5 extend this with `Pausing / Paused / Resuming / Recovering`. Duplicate
actions are rejected at the service boundary: a second start while `activeMode`
is non-null is ignored rather than queued. The UI disables the competing mode
rather than allowing a request that would fail.

Currently a single recording runs at a time — video and audio-only both need the
microphone, and CameraX plus `MediaRecorder` contending for it is a device-specific
failure. The UI states this rather than letting the second start fail silently.

## Permission matrix

| Permission | Applies | Needed for | Requested | If denied | If permanently denied |
|---|---|---|---|---|---|
| `CAMERA` | all | video, photo | entering Video/Photo | screen explains, capture disabled | link to app settings *(phase 2)* |
| `RECORD_AUDIO` | all | video sound, audio | before first record | screen explains, capture disabled | link to app settings *(phase 2)* |
| `POST_NOTIFICATIONS` | 33+ | ongoing recording notification | before first record | recording still runs; notification absent | link to app settings *(phase 2)* |
| `FOREGROUND_SERVICE` | all | screen-locked recording | install time | n/a | n/a |
| `FOREGROUND_SERVICE_CAMERA` | 34+ | video while locked | install time | n/a | n/a |
| `FOREGROUND_SERVICE_MICROPHONE` | 34+ | audio while locked | install time | n/a | n/a |
| `WAKE_LOCK` | all | keeping capture alive while locked | install time | n/a | n/a |

Not requested, and will not be: `INTERNET`, location, contacts, SMS, call log,
accessibility service, overlay, device admin, `MANAGE_EXTERNAL_STORAGE`.
Phase 2 adds location strictly behind an explicit geotagging opt-in, default off.

Permissions are requested by `PermissionGate` at the destination that needs them,
with an in-app rationale shown before the system dialog. Once a permission is
permanently denied the OS stops prompting, so the gate offers a route to app
settings rather than a button that would do nothing.

## Storage model

- Private media: `getExternalFilesDir(null)` — app-scoped, no broad storage permission,
  removed on uninstall.
- Names: `VID_yyyyMMdd_HHmmss.mp4`, `AUD_….m4a`, `IMG_….jpg`. The timestamp makes
  reverse-lexicographic sort equal newest-first. Same-second collisions get a `_2`
  suffix rather than overwriting — evidence is never silently replaced.
- Export (phase 6): SAF copy. The private original is retained and is not deleted
  until an exported copy is verified.
- Recently Deleted (phase 6): 30-day default retention, configurable.

## Foreground service lifecycle

Started from a foreground user action only. Types `camera|microphone` for video,
`microphone` for audio. Notification is ongoing and non-dismissible, states the
active mode, and returns to the recording screen when tapped. Stop actions in the
notification land in phase 4. `onDestroy` finalizes the recording, releases the
recorder, releases the wake lock, and clears state — a partial file is preserved
rather than discarded.

## Security approach

Phase 7. Vault lock via `BiometricPrompt` with PIN fallback; PIN verified through a
platform KDF, never stored in plaintext; keys in Android Keystore; no custom
cryptography. Screenshot blocking and recents-preview protection are configurable,
because forcing them breaks accessibility and support workflows for some users.

Integrity (phase 7): SHA-256 over the finalized file, stored with size, timestamp,
and app version. States: Verified / Modified / Pending / Failed / Unavailable. A
digest shows a file is unchanged since capture; it does not establish legal
authenticity or admissibility, and the UI will not claim that it does.

## Testing strategy

Unit: filename generation and collision handling, retention arithmetic, storage
thresholds, state-machine transitions, integrity verification. ViewModel tests with
injected dispatchers and fakes. Compose UI tests for navigation, recording control
states, destructive confirmations, and accessibility labels. CameraX instrumentation
tests gated behind capability assumptions, since CI has no camera.

CI currently builds only. Test execution lands in phase 8 alongside the first
tests worth running.

## Technical risks

| Risk | Mitigation |
|---|---|
| Manufacturer battery policy kills locked recording | Documented; phase 2 surfaces battery-optimization state in-app |
| Camera and microphone contention between modes | One recording at a time; competing mode disabled with a stated reason |
| Encoder or finalization failure leaves a partial file | Partial preserved and labelled; never reported as a clean save |
| Device lacks a requested quality, torch, or lens | Phase 2 capability repository; controls built from real capability data |
| No hardware available to the author for verification | Phases marked verified only after real-device testing, not before |

## Known incomplete work

- **Launcher icon** is a vector interpretation of the brand — ember flame around a
  single eye — drawn in `res/drawable/ic_launcher_foreground.xml`. It ships working
  and needs no binary. To use the full Oni artwork instead, add it as
  `res/mipmap-*/ic_launcher_foreground.png` and repoint the `<foreground>` in
  `res/mipmap-anydpi-v26/ic_launcher.xml`; the swap is documented in the vector file.
- Pause/resume, quality selection, and notification actions are absent (phases 4–5).
- No tests yet (phase 8).
- **Nothing is hardware-verified.** CI proves the project compiles; no phase should
  be called verified until it has run on a real device.

Nothing above is stubbed in the UI: controls that do not yet exist are absent
rather than present and inert.
