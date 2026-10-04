using System.Windows.Media;
using System.Windows.Media.Imaging;
using AngraiPhoneTransfer.Core;
using AngraiPhoneTransfer.Devices;
using AngraiPhoneTransfer.Mvvm;

namespace AngraiPhoneTransfer.ViewModels;

/// One tile or row in the browser.
public sealed class NodeViewModel : ObservableObject
{
    private bool isChecked;
    private ImageSource? thumbnail;

    public NodeViewModel(MediaNode node) => Node = node;

    public MediaNode Node { get; }

    public string Name => Node.Name;
    public bool IsFolder => Node.IsFolder;
    public MediaKind Kind => Node.Kind;
    public bool CanDelete => Node.CanDelete;

    public string SizeText => Node.IsFolder ? "Folder" : Humanize.Bytes(Node.Size);

    public string DateText => Node.IsFolder
        ? string.Empty
        : Node.Taken.ToString("d MMM yyyy");

    public string DetailText => Node.IsFolder
        ? "Folder"
        : $"{Humanize.Bytes(Node.Size)}  ·  {Node.Taken:d MMM yyyy}";

    public bool IsChecked
    {
        get => isChecked;
        set => Set(ref isChecked, value);
    }

    public ImageSource? Thumbnail
    {
        get => thumbnail;
        private set => Set(ref thumbnail, value);
    }

    public bool ThumbnailWanted => !Node.IsFolder && Node.Kind is MediaKind.Photo or MediaKind.Video;

    public bool ThumbnailLoaded { get; private set; }

    /// Decodes a device thumbnail on the UI thread and freezes it so it can be
    /// handed around without a per-image lock.
    public void ApplyThumbnail(byte[]? bytes)
    {
        ThumbnailLoaded = true;
        if (bytes is null || bytes.Length == 0) return;
        try
        {
            var image = new BitmapImage();
            image.BeginInit();
            image.StreamSource = new MemoryStream(bytes);
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.DecodePixelWidth = 240;
            image.EndInit();
            image.Freeze();
            Thumbnail = image;
        }
        catch (Exception ex)
        {
            Log.Warn($"Thumbnail decode failed for {Name}: {ex.Message}");
        }
    }
}
