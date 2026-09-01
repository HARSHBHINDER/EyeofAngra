using AngraiPhoneTransfer.Core;
using MediaDevices;

namespace AngraiPhoneTransfer.Devices;

/// The one place that talks to the phone. Every call is marshalled onto the
/// device worker thread; the rest of the app only ever sees plain snapshots.
///
/// The transport is USB/MTP through the Windows Portable Devices stack — the
/// same path Explorer uses — so nothing here needs iTunes, a driver of its own,
/// or a network connection of any kind.
public sealed class DeviceService : IDisposable
{
    private readonly DeviceWorker worker = new();
    private MediaDevice? device;

    public PhoneDevice? Current { get; private set; }

    public bool IsConnected => Current is not null;

    /// True while the device thread is working, so thumbnail loading can yield
    /// to a running transfer instead of queueing behind it.
    public bool Busy => worker.Busy;

    /// Runs arbitrary work against the connected device on the device thread.
    /// Used by the transfer engine, which needs the device for a long stretch.
    public Task<T> InvokeAsync<T>(Func<MediaDevice, T> work) => worker.RunAsync(() =>
    {
        var d = device ?? throw new InvalidOperationException("No phone is connected.");
        return work(d);
    });

    public Task<IReadOnlyList<PhoneDevice>> DiscoverAsync() => worker.RunAsync<IReadOnlyList<PhoneDevice>>(() =>
    {
        var found = new List<PhoneDevice>();
        try
        {
            foreach (var d in Enumerate())
            {
                try
                {
                    found.Add(new PhoneDevice
                    {
                        DeviceId = d.DeviceId,
                        Name = FirstNonEmpty(d.FriendlyName, d.Description, d.Model, "Portable device"),
                        Model = d.Model,
                        Manufacturer = d.Manufacturer,
                        SerialNumber = d.SerialNumber
                    });
                }
                catch (Exception ex)
                {
                    Log.Warn($"Skipped a device during discovery: {ex.Message}");
                }
            }
        }
        catch (Exception ex)
        {
            Log.Error("Device discovery failed", ex);
        }

        // Apple hardware first — this app is built for it, but any MTP device works.
        return found.OrderByDescending(d => d.IsApple).ThenBy(d => d.Name).ToList();
    });

    public Task<PhoneDevice?> ConnectAsync(string deviceId) => worker.RunAsync<PhoneDevice?>(() =>
    {
        DisconnectCore();

        var target = Enumerate()
            .FirstOrDefault(d => string.Equals(d.DeviceId, deviceId, StringComparison.OrdinalIgnoreCase));
        if (target is null)
        {
            Log.Warn($"Device {deviceId} vanished before connect.");
            return null;
        }

        target.Connect();
        device = target;

        uint? battery = null;
        string? firmware = null;
        try { battery = target.PowerLevel; } catch { /* not reported by every device */ }
        try { firmware = target.FirmwareVersion; } catch { /* optional */ }

        Current = new PhoneDevice
        {
            DeviceId = target.DeviceId,
            Name = FirstNonEmpty(target.FriendlyName, target.Description, target.Model, "iPhone"),
            Model = target.Model,
            Manufacturer = target.Manufacturer,
            SerialNumber = target.SerialNumber,
            FirmwareVersion = firmware,
            BatteryPercent = battery
        };
        Log.Info($"Connected to {Current.Name} ({Current.Model ?? "unknown model"}).");
        return Current;
    });

    public Task DisconnectAsync() => worker.RunAsync(DisconnectCore);

    private void DisconnectCore()
    {
        if (device is null) return;
        try
        {
            if (device.IsConnected) device.Disconnect();
        }
        catch (Exception ex)
        {
            Log.Warn($"Disconnect failed: {ex.Message}");
        }
        device = null;
        Current = null;
    }

    /// Storage headline for the sidebar (capacity and free space).
    public Task<StorageSummary?> StorageAsync() => InvokeAsync<StorageSummary?>(d =>
    {
        try
        {
            var drive = d.GetDrives().FirstOrDefault();
            if (drive is null) return null;
            return new StorageSummary
            {
                Name = FirstNonEmpty(drive.VolumeLabel, drive.Name, "Internal Storage"),
                Capacity = drive.TotalSize,
                Free = drive.TotalFreeSpace
            };
        }
        catch (Exception ex)
        {
            Log.Warn($"Storage query failed: {ex.Message}");
            return null;
        }
    });

    /// Root of the device tree. On an iPhone this holds "Internal Storage".
    public Task<MediaNode?> RootAsync() => InvokeAsync<MediaNode?>(d =>
    {
        try
        {
            var root = d.GetRootDirectory();
            return Snapshot(root);
        }
        catch (Exception ex)
        {
            Log.Error("Could not read the device root", ex);
            return null;
        }
    });

    /// Children of a folder, folders first then files, both alphabetical.
    public Task<IReadOnlyList<MediaNode>> ListAsync(string path) => InvokeAsync<IReadOnlyList<MediaNode>>(d =>
    {
        var items = new List<MediaNode>();
        try
        {
            foreach (var entry in d.GetDirectoryInfo(path).EnumerateFileSystemInfos())
            {
                try { items.Add(Snapshot(entry)); }
                catch (Exception ex) { Log.Warn($"Skipped an item in {path}: {ex.Message}"); }
            }
        }
        catch (Exception ex)
        {
            Log.Error($"Listing {path} failed", ex);
        }

        return items
            .OrderByDescending(i => i.IsFolder)
            .ThenBy(i => i.Name, StringComparer.OrdinalIgnoreCase)
            .ToList();
    });

    /// Finds DCIM — the camera roll — without assuming a fixed path.
    public Task<string?> FindCameraRollAsync() => InvokeAsync<string?>(d =>
    {
        try
        {
            foreach (var storage in d.GetRootDirectory().EnumerateDirectories())
            {
                if (IsDcim(storage.Name)) return storage.FullName;
                foreach (var child in storage.EnumerateDirectories())
                {
                    if (IsDcim(child.Name)) return child.FullName;
                }
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"Camera roll lookup failed: {ex.Message}");
        }
        return null;

        static bool IsDcim(string name) => string.Equals(name, "DCIM", StringComparison.OrdinalIgnoreCase);
    });

    /// Every file under a folder, recursively. Reports as it goes so the UI can
    /// count up instead of freezing on a 20,000-photo library.
    public Task<IReadOnlyList<MediaNode>> EnumerateFilesAsync(
        string path, Action<int>? progress, CancellationToken token) =>
        InvokeAsync<IReadOnlyList<MediaNode>>(d =>
        {
            var files = new List<MediaNode>();
            var folders = new Stack<string>();
            folders.Push(path);

            while (folders.Count > 0 && !token.IsCancellationRequested)
            {
                var current = folders.Pop();
                try
                {
                    foreach (var entry in d.GetDirectoryInfo(current).EnumerateFileSystemInfos())
                    {
                        if (token.IsCancellationRequested) break;
                        if (entry is MediaDirectoryInfo dir)
                        {
                            folders.Push(dir.FullName);
                        }
                        else
                        {
                            files.Add(Snapshot(entry));
                            if (files.Count % 250 == 0) progress?.Invoke(files.Count);
                        }
                    }
                }
                catch (Exception ex)
                {
                    Log.Warn($"Could not walk {current}: {ex.Message}");
                }
            }

            progress?.Invoke(files.Count);
            return files
                .OrderBy(f => f.FullName, StringComparer.OrdinalIgnoreCase)
                .ToList();
        });

    /// Device-rendered thumbnail, or null when the phone offers none for that item.
    public Task<byte[]?> ThumbnailAsync(string path) => InvokeAsync<byte[]?>(d =>
    {
        try
        {
            using var buffer = new MemoryStream();
            d.DownloadThumbnail(path, buffer);
            return buffer.Length > 0 ? buffer.ToArray() : null;
        }
        catch
        {
            return null; // thumbnails are a nicety, never an error worth surfacing
        }
    });

    /// The manager is a shared singleton; it hands back live COM-backed objects,
    /// so they are never disposed here.
    private static IEnumerable<MediaDevice> Enumerate()
        => MediaDeviceManager.Instance?.GetDevices() ?? Enumerable.Empty<MediaDevice>();

    internal static MediaNode Snapshot(MediaFileSystemInfo info)
    {
        var isFolder = info is MediaDirectoryInfo;
        long size = 0;
        try { size = (long)Math.Min(info.Length, (ulong)long.MaxValue); } catch { /* folders report nothing */ }

        var attributes = MediaFileAttributes.Normal;
        try { attributes = info.Attributes; } catch { /* optional */ }

        return new MediaNode
        {
            FullName = info.FullName,
            Name = info.Name,
            IsFolder = isFolder,
            Size = isFolder ? 0 : size,
            Created = Safe(() => info.CreationTime),
            Modified = Safe(() => info.LastWriteTime),
            CanDelete = attributes.HasFlag(MediaFileAttributes.CanDelete),
            Kind = isFolder ? MediaKind.Folder : MediaClassifier.FromName(info.Name)
        };
    }

    private static DateTime? Safe(Func<DateTime?> read)
    {
        try { return read(); } catch { return null; }
    }

    private static string FirstNonEmpty(params string?[] values)
        => values.FirstOrDefault(v => !string.IsNullOrWhiteSpace(v))?.Trim() ?? string.Empty;

    public void Dispose()
    {
        try { worker.RunAsync(DisconnectCore).Wait(TimeSpan.FromSeconds(3)); } catch { /* shutting down */ }
        worker.Dispose();
    }
}
