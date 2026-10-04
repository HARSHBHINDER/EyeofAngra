namespace AngraiPhoneTransfer.Transfer;

public enum TransferMode
{
    /// Copy to the PC and leave the phone untouched.
    Copy,
    /// Copy, verify, then delete the original from the phone.
    Move
}

public enum FolderLayout
{
    Flat,
    ByYear,
    ByYearMonth,
    ByYearMonthDay
}

public enum DuplicatePolicy
{
    /// Same name and same size already on disk — leave it alone.
    Skip,
    /// Write alongside as "IMG_0042 (2).HEIC".
    KeepBoth,
    Overwrite
}

public sealed class TransferOptions
{
    public required string Destination { get; init; }
    public TransferMode Mode { get; init; } = TransferMode.Copy;
    public FolderLayout Layout { get; init; } = FolderLayout.ByYearMonth;
    public DuplicatePolicy Duplicates { get; init; } = DuplicatePolicy.Skip;

    /// Compare the written byte count against what the phone reported before the
    /// file counts as transferred. A move never deletes an unverified original.
    public bool VerifySize { get; init; } = true;

    public string FolderFor(DateTime taken) => Layout switch
    {
        FolderLayout.ByYear => taken.ToString("yyyy"),
        FolderLayout.ByYearMonth => Path.Combine(taken.ToString("yyyy"), taken.ToString("yyyy-MM")),
        FolderLayout.ByYearMonthDay => Path.Combine(taken.ToString("yyyy"), taken.ToString("yyyy-MM"), taken.ToString("yyyy-MM-dd")),
        _ => string.Empty
    };
}
