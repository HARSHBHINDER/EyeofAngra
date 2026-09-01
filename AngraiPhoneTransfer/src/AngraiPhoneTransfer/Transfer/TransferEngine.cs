using System.Diagnostics;
using System.Text;
using AngraiPhoneTransfer.Core;
using AngraiPhoneTransfer.Devices;
using MediaDevices;

namespace AngraiPhoneTransfer.Transfer;

/// Streams files off the phone and onto the PC as ordinary files — one photo in,
/// one photo out, nothing wrapped in a backup container.
///
/// Order of operations per file, and the reason for it:
///   1. write to "<name>.angrapart" so an interrupted run never leaves a file
///      that looks complete,
///   2. compare the written length against the size the phone reported,
///   3. only then swap the part file into place and stamp its timestamps,
///   4. only then, and only for a Move, delete the original from the phone.
/// A file that fails any step stays on the phone.
public sealed class TransferEngine
{
    private const int BufferSize = 1024 * 1024;
    private static readonly TimeSpan ReportEvery = TimeSpan.FromMilliseconds(120);

    private readonly DeviceService devices;

    public TransferEngine(DeviceService devices) => this.devices = devices;

    public Task<TransferResult> RunAsync(
        IReadOnlyList<MediaNode> items,
        TransferOptions options,
        IProgress<TransferProgress>? progress,
        CancellationToken token)
        => devices.InvokeAsync(device => Execute(device, items, options, progress, token));

    private static TransferResult Execute(
        MediaDevice device,
        IReadOnlyList<MediaNode> items,
        TransferOptions options,
        IProgress<TransferProgress>? progress,
        CancellationToken token)
    {
        var outcomes = new List<ItemOutcome>(items.Count);
        var clock = Stopwatch.StartNew();
        var lastReport = TimeSpan.Zero;
        long bytesTotal = items.Sum(i => Math.Max(0, i.Size));
        long bytesDone = 0;
        int copied = 0, skipped = 0, failed = 0, removed = 0;
        var cancelled = false;

        Log.Info($"Transfer started: {items.Count} item(s), {Humanize.Bytes(bytesTotal)}, " +
                 $"mode={options.Mode}, layout={options.Layout}, duplicates={options.Duplicates}, " +
                 $"destination={options.Destination}");

        Directory.CreateDirectory(options.Destination);

        for (var index = 0; index < items.Count; index++)
        {
            var item = items[index];
            if (token.IsCancellationRequested)
            {
                cancelled = true;
                outcomes.Add(new ItemOutcome
                {
                    Name = item.Name, Source = item.FullName, Bytes = item.Size,
                    Status = ItemStatus.Cancelled, Note = "Stopped before this file"
                });
                continue;
            }

            Report(force: true, item.Name, index);

            string? partFile = null;
            try
            {
                var folder = Path.Combine(options.Destination, options.FolderFor(item.Taken));
                Directory.CreateDirectory(folder);

                var target = Path.Combine(folder, SafeFileName(item.Name));

                if (File.Exists(target))
                {
                    switch (options.Duplicates)
                    {
                        case DuplicatePolicy.Skip:
                            skipped++;
                            bytesDone += item.Size;
                            outcomes.Add(new ItemOutcome
                            {
                                Name = item.Name, Source = item.FullName, Destination = target,
                                Bytes = item.Size, Status = ItemStatus.Skipped,
                                Note = "Already in the destination folder"
                            });
                            Report(force: true, item.Name, index + 1);
                            continue;

                        case DuplicatePolicy.KeepBoth:
                            target = NextFreeName(target);
                            break;

                        case DuplicatePolicy.Overwrite:
                        default:
                            break;
                    }
                }

                partFile = target + ".angrapart";
                if (File.Exists(partFile)) File.Delete(partFile);

                long written = 0;
                using (var source = device.GetFileInfo(item.FullName).OpenRead())
                using (var sink = new FileStream(partFile, FileMode.Create, FileAccess.Write, FileShare.None, BufferSize))
                {
                    var buffer = new byte[BufferSize];
                    int read;
                    while ((read = source.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        token.ThrowIfCancellationRequested();
                        sink.Write(buffer, 0, read);
                        written += read;
                        bytesDone += read;
                        Report(force: false, item.Name, index);
                    }
                    sink.Flush(true);
                }

                // The phone's reported size is the contract for a completed file.
                if (options.VerifySize && item.Size > 0 && written != item.Size)
                {
                    throw new IOException(
                        $"Size mismatch — the phone reported {item.Size:N0} bytes but {written:N0} arrived.");
                }

                File.Move(partFile, target, overwrite: true);
                partFile = null;
                StampTimestamps(target, item);

                var status = ItemStatus.Copied;
                var note = string.Empty;

                if (options.Mode == TransferMode.Move)
                {
                    try
                    {
                        device.DeleteFile(item.FullName);
                        removed++;
                        status = ItemStatus.Moved;
                    }
                    catch (Exception ex)
                    {
                        // The copy is safe on disk; the original simply stayed put.
                        note = "Copied, but the phone refused to delete the original: " + ex.Message;
                        Log.Warn($"Delete refused for {item.FullName}: {ex.Message}");
                    }
                }

                copied++;
                outcomes.Add(new ItemOutcome
                {
                    Name = item.Name, Source = item.FullName, Destination = target,
                    Bytes = written, Status = status, Note = note
                });
            }
            catch (OperationCanceledException)
            {
                cancelled = true;
                TryDeletePart(partFile);
                outcomes.Add(new ItemOutcome
                {
                    Name = item.Name, Source = item.FullName, Bytes = item.Size,
                    Status = ItemStatus.Cancelled, Note = "Stopped part-way; nothing was removed from the phone"
                });
            }
            catch (Exception ex)
            {
                failed++;
                TryDeletePart(partFile);
                Log.Error($"Transfer failed for {item.FullName}", ex);
                outcomes.Add(new ItemOutcome
                {
                    Name = item.Name, Source = item.FullName, Bytes = item.Size,
                    Status = ItemStatus.Failed, Note = ex.Message
                });
            }

            Report(force: true, item.Name, index + 1);
        }

        clock.Stop();
        var report = WriteReport(outcomes, options, clock.Elapsed);
        Log.Info($"Transfer finished in {clock.Elapsed:hh\\:mm\\:ss} — " +
                 $"{copied} transferred, {skipped} skipped, {failed} failed, {removed} removed from phone.");

        return new TransferResult
        {
            Items = outcomes,
            Elapsed = clock.Elapsed,
            BytesTransferred = bytesDone,
            Cancelled = cancelled,
            ReportPath = report,
            Destination = options.Destination
        };

        void Report(bool force, string name, int index)
        {
            if (progress is null) return;
            if (!force && clock.Elapsed - lastReport < ReportEvery) return;
            lastReport = clock.Elapsed;

            var rate = clock.Elapsed.TotalSeconds > 0.5 ? bytesDone / clock.Elapsed.TotalSeconds : 0;
            var remaining = rate > 0 ? TimeSpan.FromSeconds(Math.Max(0, bytesTotal - bytesDone) / rate) : TimeSpan.Zero;

            progress.Report(new TransferProgress
            {
                FileIndex = Math.Min(index + 1, items.Count),
                FileCount = items.Count,
                CurrentFile = name,
                BytesDone = bytesDone,
                BytesTotal = bytesTotal,
                BytesPerSecond = rate,
                Remaining = remaining,
                Copied = copied,
                Skipped = skipped,
                Failed = failed,
                RemovedFromPhone = removed
            });
        }
    }

    private static void StampTimestamps(string path, MediaNode item)
    {
        try
        {
            if (item.Created is { } created) File.SetCreationTime(path, created);
            if (item.Modified is { } modified) File.SetLastWriteTime(path, modified);
            else if (item.Created is { } fallback) File.SetLastWriteTime(path, fallback);
        }
        catch (Exception ex)
        {
            Log.Warn($"Could not stamp timestamps on {path}: {ex.Message}");
        }
    }

    private static void TryDeletePart(string? partFile)
    {
        if (partFile is null) return;
        try { if (File.Exists(partFile)) File.Delete(partFile); } catch { /* best effort */ }
    }

    private static string SafeFileName(string name)
    {
        var invalid = Path.GetInvalidFileNameChars();
        var builder = new StringBuilder(name.Length);
        foreach (var c in name) builder.Append(invalid.Contains(c) ? '_' : c);
        var cleaned = builder.ToString().Trim();
        return string.IsNullOrEmpty(cleaned) ? "unnamed" : cleaned;
    }

    private static string NextFreeName(string target)
    {
        var folder = Path.GetDirectoryName(target) ?? string.Empty;
        var stem = Path.GetFileNameWithoutExtension(target);
        var ext = Path.GetExtension(target);
        for (var n = 2; n < 10000; n++)
        {
            var candidate = Path.Combine(folder, $"{stem} ({n}){ext}");
            if (!File.Exists(candidate)) return candidate;
        }
        return Path.Combine(folder, $"{stem} ({Guid.NewGuid():N}){ext}");
    }

    private static string WriteReport(IReadOnlyList<ItemOutcome> outcomes, TransferOptions options, TimeSpan elapsed)
    {
        try
        {
            var path = Path.Combine(AppPaths.Reports, $"transfer-{DateTime.Now:yyyyMMdd-HHmmss}.csv");
            var csv = new StringBuilder();
            csv.AppendLine($"# AngraiPhoneTransfer report,{DateTime.Now:yyyy-MM-dd HH:mm:ss}");
            csv.AppendLine($"# Mode,{options.Mode},Destination,{Escape(options.Destination)},Elapsed,{elapsed:hh\\:mm\\:ss}");
            csv.AppendLine("Status,Name,Bytes,On phone,Saved to,Note");
            foreach (var o in outcomes)
            {
                csv.AppendLine(string.Join(',',
                    o.Status, Escape(o.Name), o.Bytes, Escape(o.Source), Escape(o.Destination), Escape(o.Note)));
            }
            File.WriteAllText(path, csv.ToString(), Encoding.UTF8);
            return path;
        }
        catch (Exception ex)
        {
            Log.Warn($"Could not write the transfer report: {ex.Message}");
            return string.Empty;
        }

        static string Escape(string value)
            => value.Contains(',') || value.Contains('"')
                ? "\"" + value.Replace("\"", "\"\"") + "\""
                : value;
    }
}
