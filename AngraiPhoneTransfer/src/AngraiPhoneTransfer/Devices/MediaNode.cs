namespace AngraiPhoneTransfer.Devices;

/// One file or folder on the phone. A plain snapshot — no COM object is held,
/// so the UI can keep thousands of these around without pinning the device.
public sealed class MediaNode
{
    public required string FullName { get; init; }
    public required string Name { get; init; }
    public bool IsFolder { get; init; }
    public long Size { get; init; }
    public DateTime? Created { get; init; }
    public DateTime? Modified { get; init; }
    public bool CanDelete { get; init; }
    public MediaKind Kind { get; init; }

    /// Timestamp used for date-based foldering and for stamping the copy.
    public DateTime Taken => Created ?? Modified ?? DateTime.Now;

    /// "IMG_0042" for IMG_0042.HEIC — Live Photos pair a still and a movie
    /// under the same stem.
    public string Stem => Path.GetFileNameWithoutExtension(Name);
}
