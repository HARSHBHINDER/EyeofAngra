# EyeofAngra

**Emergency evidence capture for Android — private by design.** EyeofAngra records
video, audio, and photos the instant your safety, liberty, or wellbeing is at
risk, and keeps every file on your own device. No account, no cloud, no
analytics, and no network permission at all.

Built for the moments that matter: one-tap capture, recording that survives a
locked screen, an optional stealth auto-lock, and the freedom to store evidence
wherever you choose — on the phone or in a folder you pick.

iOS sibling: [EyeofAngra for iPhone](https://github.com/HARSHBHINDER/EyeofAngraiOS).

## Downloads

Every release is listed below with its changes and a direct download — from both
this repository's [`APKs/`](APKs) folder and the matching GitHub Release. The
table regenerates automatically on each push from
[`versions.json`](versions.json).

<!-- VERSIONS:TABLE:START -->
<table>
<thead><tr><th>#</th><th>Version</th><th>Changes &amp; features</th><th>Download</th></tr></thead>
<tbody>
<tr><td align="center">1</td><td align="center"><strong>v2.7</strong><br><sub>2026-10-10</sub></td><td><ul><li>Drag to select: hold a tile and slide across the grid, everything you pass is selected; the grid scrolls on its own at the top or bottom edge.</li><li>Pinch the Vault to resize it, from 1 to 7 tiles per row.</li><li>Photos-style grid: square, edge-to-edge tiles with thin gaps, length bottom-right, capture time on larger tiles, sharper thumbnails when zoomed in.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.7.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.7.apk from repo"></a><br><a href="https://github.com/HARSHBHINDER/EyeofAngra/releases/download/latest/app-debug.apk"><img src="https://img.shields.io/badge/APK%20release-24292E?style=flat-square&logo=github&logoColor=white" alt="Download from GitHub release"></a></td></tr>
<tr><td align="center">2</td><td align="center"><strong>v2.6</strong><br><sub>2026-10-10</sub></td><td><ul><li>Auto-rotation removed: the app stays in portrait, so locking the phone mid-recording can no longer leave the lock screen in a broken landscape layout.</li><li>Recordings and photos are portrait too, however the phone is held: the screen, the record button and the captures never change orientation.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.6.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.6.apk from repo"></a></td></tr>
<tr><td align="center">3</td><td align="center"><strong>v2.5</strong><br><sub>2026-10-10</sub></td><td><ul><li>Select mode in the Vault: tap Select or hold any tile, tick as many videos, photos and audio clips as you like, or use All.</li><li>Batch actions for every file type: Copy to folder, Move to folder (pick the folder once for the whole batch), and Delete with one confirmation.</li><li>Lighter exports: the copy is handed to the system kernel (FileChannel.transferTo) instead of passing every byte through the app, still synced every 16 MB and run at background priority.</li><li>Progress shows which item of the batch is copying and the overall percentage.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.5.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.5.apk from repo"></a></td></tr>
<tr><td align="center">4</td><td align="center"><strong>v2.4</strong><br><sub>2026-10-10</sub></td><td><ul><li>Export is smooth: data is written to storage every 16 MB instead of buffering gigabytes and stalling, so progress moves steadily and the phone stays responsive.</li><li>Export runs at background priority, so the screen and other apps always come first.</li><li>Switching tabs mid-export no longer hides the progress bar; come back to the Vault and it is still there.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.4.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.4.apk from repo"></a></td></tr>
<tr><td align="center">5</td><td align="center"><strong>v2.3</strong><br><sub>2026-10-10</sub></td><td><ul><li>Fixed: exported videos would not play in MX Player or any other player. Export copied on the main thread, so Android killed the app partway through large videos, leaving files without the index MP4 keeps at the end. Export now runs in the background with a progress bar, checks every byte arrived, and deletes a failed partial copy.</li><li>Move only removes the original after a complete, verified copy.</li><li>Video tiles show a real frame from the clip, its length, and when it was recorded; audio tiles show their length.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.3.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.3.apk from repo"></a></td></tr>
<tr><td align="center">6</td><td align="center"><strong>v2.2</strong><br><sub>2026-10-10</sub></td><td><ul><li>Vault redesigned as a tile grid: photo thumbnails, gold marks for video and audio, size on every tile, gold filter pills, hold a tile to export or delete.</li><li>Video quality setting: 4K, 1080p or 720p, applied each time recording starts, with automatic fallback on phones that cannot reach it.</li><li>Settings rebuilt in the iOS order: Capture, Recording, Storage, Appearance, About, closing on the brushed-gold wordmark.</li><li>About shows the real app version; Safety & Legal folds open in place.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.2.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.2.apk from repo"></a></td></tr>
<tr><td align="center">7</td><td align="center"><strong>v2.1</strong><br><sub>2026-09-27</sub></td><td><ul><li>Vault: Export a capture to any folder, as a Copy (keeps the original) or a Move (removes it from the vault once the export succeeds).</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.1.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.1.apk from repo"></a></td></tr>
<tr><td align="center">8</td><td align="center"><strong>v2.0</strong><br><sub>2026-08-05</sub></td><td><ul><li>Auto-lock: the screen turns off the instant recording starts, and capture continues in the background (opt-in, device-admin).</li><li>Choose where captures are saved: a toggle plus a folder picker (Storage Access Framework), with automatic fallback to on-device storage if the folder is unavailable.</li><li>Premium visual pass: gold hairline cards, letter-spaced section headers, and a focal record button.</li><li>Video, audio, and photo all honour the chosen save location.</li><li>The Vault lists captures from both locations, tagged 'on board' or 'chosen folder', with combined storage totals; open and delete work for either.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v2.0.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v2.0.apk from repo"></a></td></tr>
<tr><td align="center">9</td><td align="center"><strong>v1.0</strong><br><sub>2026-07-19</sub></td><td><ul><li>First release: Video, Audio, Photo, Vault, and Settings.</li><li>Lock-screen recording via a foreground service with an ongoing notification.</li><li>Everything stored on-device; no network permission, accounts, or analytics.</li></ul></td><td align="center"><a href="https://github.com/HARSHBHINDER/EyeofAngra/raw/main/APKs/EyeofAngra-v1.0.apk"><img src="https://img.shields.io/badge/APK%20in--repo-2EA44F?style=flat-square&logo=android&logoColor=white" alt="Download EyeofAngra-v1.0.apk from repo"></a></td></tr>
</tbody>
</table>
<!-- VERSIONS:TABLE:END -->

> **Installing:** on an Android phone, tap a **Download** button above, open the
> `.apk`, and allow "install from unknown sources" when prompted. Updating from
> an older EyeofAngra signed with a different key? Uninstall it first. First
> launch asks for camera, microphone, and notification access. Free, no expiry.

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
- **Vault** — everything captured, from **both** storage locations, filterable by
  type and tagged "on board" or "chosen folder", with a combined storage summary.
  Tap to play or view; delete asks for confirmation first.
- **Settings** — capture, recording behaviour, appearance, storage,
  permissions, and the full Safety & Legal text.

Two capabilities added in 2.0:

- **Auto-lock on record** *(opt-in)* — turn it on and the screen locks the
  instant a recording begins, so the phone looks idle while capture continues in
  the background. Uses Android's device-admin force-lock; toggle it off any time.
- **Choose your save location** — a toggle plus a folder picker. Off, captures
  stay in the app's private storage ("on board"); on, they go to any folder you
  pick via the Storage Access Framework. If that folder is ever unavailable,
  capture falls back on board so a recording is never lost.

Only one recording runs at a time: video and audio both need the microphone,
and the app says so rather than letting the second attempt fail silently.

No cloud, no analytics, no accounts, no network permission at all.

Architecture, phased plan, permission matrix, storage model, and the recording
state machine are documented in [DOCS/PLAN.md](DOCS/PLAN.md).

## Releases & automation

Grab any build from the [Downloads](#downloads) table above. Behind it:

- Every push to `main` rebuilds the debug APK and publishes it to the rolling
  [`latest` release](../../releases/tag/latest)
  ([`build-apk.yml`](.github/workflows/build-apk.yml)).
- To add a version to the table, append an entry to
  [`versions.json`](versions.json) and drop its APK in [`APKs/`](APKs). On push,
  [`readme-versions.yml`](.github/workflows/readme-versions.yml) regenerates the
  table automatically — serial number, version, changelog, and download buttons
  for both the in-repo file and the GitHub Release.

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
this app lawfully. Recordings stay in the app's private folder until you delete
them, unless you turn on **Save to a chosen folder** — captures then go to the
folder you pick, where Android may index them into your gallery and other apps
can read them. Provided as-is, without warranty — see [LICENSE](LICENSE).
