namespace AngraiPhoneTransfer.Transfer;

/// Snapshot pushed to the UI while a transfer runs.
public sealed class TransferProgress
{
    public int FileIndex { get; init; }
    public int FileCount { get; init; }
    public string CurrentFile { get; init; } = string.Empty;
    public long BytesDone { get; init; }
    public long BytesTotal { get; init; }
    public double BytesPerSecond { get; init; }
    public TimeSpan Remaining { get; init; }
    public int Copied { get; init; }
    public int Skipped { get; init; }
    public int Failed { get; init; }
    public int RemovedFromPhone { get; init; }

    public double Fraction => BytesTotal <= 0 ? 0 : Math.Clamp((double)BytesDone / BytesTotal, 0, 1);
}

public enum ItemStatus
{
    Copied,
    Moved,
    Skipped,
    Failed,
    Cancelled
}

public sealed class ItemOutcome
{
    public required string Name { get; init; }
    public required string Source { get; init; }
    public string Destination { get; init; } = string.Empty;
    public long Bytes { get; init; }
    public ItemStatus Status { get; init; }
    public string Note { get; init; } = string.Empty;
}

public sealed class TransferResult
{
    public required IReadOnlyList<ItemOutcome> Items { get; init; }
    public TimeSpan Elapsed { get; init; }
    public long BytesTransferred { get; init; }
    public bool Cancelled { get; init; }
    public string ReportPath { get; init; } = string.Empty;
    public string Destination { get; init; } = string.Empty;

    public int Copied => Items.Count(i => i.Status is ItemStatus.Copied or ItemStatus.Moved);
    public int Moved => Items.Count(i => i.Status == ItemStatus.Moved);
    public int Skipped => Items.Count(i => i.Status == ItemStatus.Skipped);
    public int Failed => Items.Count(i => i.Status == ItemStatus.Failed);
}
