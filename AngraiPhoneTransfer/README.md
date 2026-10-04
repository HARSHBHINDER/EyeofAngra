# AngraiPhoneTransfer

**Wired iPhone transfer for Windows.** Plug the phone in, pick what you want,
and the photos, videos and files land on the laptop as plain files — full
resolution, original names, original dates. No iTunes, no backup blob, no
account, no internet connection.

It does the one thing iTunes will not: a **manual copy or cut** of the real
files, at full scale, straight into a folder you choose.

- **Copy to PC** — leaves the phone exactly as it was.
- **Move off phone** — copies, verifies every byte count against what the phone
  reports, and only then deletes the original. Anything that fails to copy stays
  on the phone.
- Browse the camera roll, flatten it into one **All photos & videos** list, or
  walk the whole device tree.
- Filter to photos, videos or other files; search by name; grid or list.
- Sort arrivals into `Year / month` folders (or don't), skip duplicates, keep
  both, or overwrite.
- Live Photos stay whole: the still and its movie travel together.
- Every run writes a CSV report — what moved, what was skipped, what failed.

Nothing is uploaded anywhere. The app has no network code in it at all.

## Install

1. Download `AngraiPhoneTransfer-Setup-1.0.0-x64.exe` from the
   [latest Windows build](../../releases/tag/windows-latest) — or from the
   **Build Windows app** run in the [Actions tab](../../actions/workflows/build-windows-app.yml)
   if you are building from a branch.
2. Run it. It installs for the current user, so Windows never asks for an
   administrator password.
3. Connect the iPhone with a cable, unlock it, and tap **Trust** when it asks.

Windows 10 (1809) or Windows 11, 64-bit. Everything the app needs is inside the
installer — .NET is bundled, so there is nothing else to install.

## Getting full-resolution originals

Two settings on the phone decide whether you get the true original file:

| On the phone | Set it to | Why |
| --- | --- | --- |
| Settings › Photos › **Transfer to Mac or PC** | **Keep Originals** | On *Automatic*, iOS transcodes HEIC to JPEG and HEVC to H.264 while copying. Keep Originals hands over the untouched file. |
| Settings › Photos › **Optimize iPhone Storage** | Download originals first | Anything stored only in iCloud is not physically on the phone, so no cable can fetch it. |

The app shows this as a one-time note when a phone connects.

## How it connects

Over the USB cable, through **MTP / Windows Portable Devices** — the same stack
Explorer uses when it shows `This PC › Apple iPhone`. That means:

- no iTunes, no Apple Mobile Device Service, no third-party driver,
- no pairing certificate, no Wi-Fi, no network of any kind,
- files arrive one-for-one, not inside a backup container.

The details, the alternatives that were considered, and what each one can and
cannot reach are in [DOCS/RESEARCH.md](DOCS/RESEARCH.md).

## Building it yourself

The app is WPF on .NET 8, so it builds on Windows only:

```powershell
dotnet publish src\AngraiPhoneTransfer\AngraiPhoneTransfer.csproj -c Release -r win-x64 --self-contained true -o publish
iscc installer\AngraiPhoneTransfer.iss    # needs Inno Setup 6
```

The installer lands in `dist\`. CI does exactly this on every push —
[`build-windows-app.yml`](../.github/workflows/build-windows-app.yml).

## Layout

```
src/AngraiPhoneTransfer/
  Core/        settings, logging, the single-threaded device worker
  Devices/     MTP discovery, browsing, thumbnails — the only code that touches the phone
  Transfer/    the copy/verify/delete engine and its report
  ViewModels/  window state and commands
  Views/       one window
  Themes/      the Angra design tokens, shared with EyeofAngra for Android
installer/     Inno Setup script that produces the .exe
assets/        icon generator
```

Settings, logs and transfer reports live in
`%LocalAppData%\AngraiPhoneTransfer`. Nothing else on the PC is touched.

## Known limits

- **Deleting is the phone's decision.** iOS allows deleting camera-roll items
  over MTP, but it can refuse — for an item that is syncing, or one iOS does not
  consider deletable. When it refuses, the copy is already safe on the PC and the
  original simply stays; the report says so per file.
- MTP exposes the camera roll (`DCIM`) and whatever else iOS chooses to publish.
  App documents are not reachable this way — see the research notes for the AFC
  route that would reach further.
- Thumbnails come from the phone. Some items offer none; those show a typed icon.
