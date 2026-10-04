# How a Windows app talks to an iPhone

Research behind AngraiPhoneTransfer: what routes exist between a Windows PC and
an iPhone over a cable, what each one can reach, and why this app takes the one
it takes.

## The three routes

### 1. MTP / PTP through Windows Portable Devices — *the route this app uses*

When an iPhone is plugged in and unlocked, iOS publishes itself as a **Media
Transfer Protocol** device. Windows exposes that through the
**Windows Portable Devices (WPD)** COM API — `IPortableDeviceManager`,
`IPortableDevice`, `IPortableDeviceContent` — which is exactly what Explorer
uses to show `This PC › Apple iPhone › Internal Storage › DCIM`.

What it reaches: the camera roll (`DCIM/100APPLE`, `101APPLE`, …) with original
files, original names, capture dates, and, where iOS offers them, thumbnails.
Deleting is supported, which is what makes a true *move* possible.

Why it wins here:

- **Nothing to install.** No iTunes, no Apple Mobile Device Service, no driver,
  no pairing record. If Explorer can see the phone, so can this app.
- **No network, ever.** USB only, by construction.
- **Plain files.** One photo in, one photo out — never a backup container.
- It is the same path a user could take by hand, so nothing surprising happens
  to their phone.

The COM API is verbose, so the app builds on
**[MediaDevices](https://www.nuget.org/packages/MediaDevices/)** (MIT, .NET 8+),
a maintained .NET wrapper over WPD by
[Bassman2](https://github.com/Bassman2/MediaDevices). It gives typed access to
device discovery, the object tree, `OpenRead`, `DownloadThumbnail` and
`DeleteFile`. It is not thread-safe — the COM objects underneath are apartment
bound — so every call in this app is funnelled through one long-lived STA
thread (`Core/DeviceWorker.cs`).

Limits worth knowing:

- **Transcoding.** With *Settings › Photos › Transfer to Mac or PC* on
  **Automatic**, iOS converts HEIC to JPEG and HEVC to H.264 as it hands the
  file over. **Keep Originals** turns that off. This is an iOS behaviour, not
  something an app can override, so the app tells the user about it instead.
- **iCloud.** With *Optimize iPhone Storage*, the full-size original may not be
  on the device at all. No cable protocol can produce a file the phone does not
  hold.
- **Scope.** MTP shows what iOS chooses to publish — the camera roll, and not
  much else. App documents are out of reach.
- **Deletion can be refused** per item; the app treats that as "copy succeeded,
  original stayed", never as a silent failure.

### 2. AFC through libimobiledevice

[libimobiledevice](https://libimobiledevice.org/) is a clean-room implementation
of Apple's own protocols (usbmuxd, lockdownd, **AFC** — Apple File Conduit). It
reaches `/DCIM` and, with the right service, more of the media domain than MTP
exposes; `ifuse` mounts it as a filesystem, and `afcclient` copies from the
command line. Windows binaries exist
([jrjr/libimobiledevice-windows](https://github.com/jrjr/libimobiledevice-windows))
and .NET bindings exist (imobiledevice-net).

Why it is not the first backend:

- It needs a **pairing record** — the phone must be unlocked and trusted, and
  lockdownd must complete a handshake. That is a second failure mode on top of
  the cable.
- It means shipping native DLLs (LGPL-2.1) and a usbmuxd service alongside a
  .NET app, which is a much larger install than "run the .exe".
- For photos and videos specifically it reaches the same `DCIM` content MTP
  already gives.

It stays the natural **second backend**: the app's device layer is one
interface (`Devices/DeviceService.cs`) over plain snapshots
(`MediaNode`), so an AFC implementation can slot in beside the MTP one without
touching the transfer engine or the UI.

### 3. iTunes / Apple Devices backup — *explicitly rejected*

A backup writes an opaque, phone-shaped archive: hashed filenames, a manifest
database, all-or-nothing restores. It is the exact thing this app exists to
avoid. Nothing in the codebase reads or writes that format.

## Prior art that informed the design

| Project | What it does | What was taken from it |
| --- | --- | --- |
| [thomas694/iPhoneMediaTransfer](https://github.com/thomas694/iPhoneMediaTransfer) | Windows command-line copy of photos/videos off an iPhone over MTP | Confirms MTP is enough for the whole camera roll, including full-size originals |
| [Bassman2/MediaDevices](https://github.com/Bassman2/MediaDevices) | MIT .NET wrapper over WPD | Used directly as the transport |
| [libimobiledevice](https://libimobiledevice.org/) | Apple protocol stack, cross-platform | The roadmap for a deeper second backend |
| [adam-nemeth/iPhoneImport](https://github.com/adam-nemeth/iPhoneImport) | Python script, copies new photos via MTP | The "only what is new" idea, met here by the duplicate policy |
| CopyTrans Photo, iMazing (commercial) | The polished versions of this job | The interaction model: browse, select, drag out — and the fact that people pay for it because Explorer's cut/paste is unreliable |

Explorer itself can copy from `DCIM`, and its *cut* often fails to remove the
original — the common complaint this app answers with copy → verify → delete,
reported per file.

## Design decisions that follow from the research

1. **Verify before delete.** The phone reports a size per object; a moved file
   is only unlinked once the written byte count matches. A mismatch leaves the
   original alone and marks the file failed.
2. **Write to `<name>.angrapart`, then rename.** An interrupted run never leaves
   a truncated file that looks complete.
3. **One device thread.** WPD is apartment bound and MTP has no concurrency to
   offer; serialising is both correct and faster than contending.
4. **Snapshots, not COM objects, in the UI.** The view model holds plain records,
   so a disconnected phone can never crash a redraw.
5. **Live Photos travel in pairs.** A `.HEIC` and its `.MOV` share a stem; taking
   one without the other silently halves the picture.
6. **Timestamps are restored** from the phone's created/modified dates, so the
   PC's own gallery sorts the arrivals correctly.
7. **Paged listing.** iOS caps a `DCIM` subfolder near 999 items, but a flattened
   camera roll can be tens of thousands, so the browser pages and loads
   thumbnails only for what has been paged in.

## If you want to go further

- **AFC backend** for the media domain beyond `DCIM`, via libimobiledevice.
- **HEIC preview** on the PC without a codec pack, by decoding the embedded
  thumbnail rather than the full image.
- **Incremental sync** — remember what a given phone serial has already handed
  over, and offer "only what is new since last time".
