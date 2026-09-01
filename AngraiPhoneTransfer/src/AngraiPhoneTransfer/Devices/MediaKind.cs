namespace AngraiPhoneTransfer.Devices;

public enum MediaKind
{
    Folder,
    Photo,
    Video,
    Audio,
    Document,
    Sidecar,
    Other
}

/// Classifies an item by extension. The iPhone's camera roll is HEIC/JPEG plus
/// MOV/MP4, with .AAE sidecars describing non-destructive edits.
public static class MediaClassifier
{
    private static readonly HashSet<string> Photos = new(StringComparer.OrdinalIgnoreCase)
    { ".heic", ".heif", ".avci", ".jpg", ".jpeg", ".jpe", ".png", ".gif", ".bmp", ".tif", ".tiff", ".dng", ".webp", ".raw", ".cr2", ".nef" };

    private static readonly HashSet<string> Videos = new(StringComparer.OrdinalIgnoreCase)
    { ".mov", ".mp4", ".m4v", ".hevc", ".avi", ".3gp", ".3g2", ".mkv", ".webm" };

    private static readonly HashSet<string> Audios = new(StringComparer.OrdinalIgnoreCase)
    { ".m4a", ".mp3", ".wav", ".aac", ".caf", ".aiff", ".flac", ".amr" };

    private static readonly HashSet<string> Documents = new(StringComparer.OrdinalIgnoreCase)
    { ".pdf", ".txt", ".rtf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".json", ".xml", ".zip", ".vcf", ".ics", ".epub", ".pages", ".numbers", ".key" };

    public static MediaKind FromName(string name)
    {
        var ext = Path.GetExtension(name);
        if (string.IsNullOrEmpty(ext)) return MediaKind.Other;
        if (Photos.Contains(ext)) return MediaKind.Photo;
        if (Videos.Contains(ext)) return MediaKind.Video;
        if (Audios.Contains(ext)) return MediaKind.Audio;
        if (Documents.Contains(ext)) return MediaKind.Document;
        if (string.Equals(ext, ".aae", StringComparison.OrdinalIgnoreCase)) return MediaKind.Sidecar;
        return MediaKind.Other;
    }

    public static bool IsMedia(MediaKind kind) => kind is MediaKind.Photo or MediaKind.Video;
}
