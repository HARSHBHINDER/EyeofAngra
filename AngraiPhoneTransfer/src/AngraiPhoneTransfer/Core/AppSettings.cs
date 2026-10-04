using System.Text.Json;
using System.Text.Json.Serialization;
using AngraiPhoneTransfer.Transfer;

namespace AngraiPhoneTransfer.Core;

/// User preferences, kept as readable JSON next to the log. Losing this file
/// only costs the defaults.
public sealed class AppSettings
{
    private static readonly JsonSerializerOptions Json = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() }
    };

    public string Destination { get; set; } = AppPaths.DefaultDestination;
    public FolderLayout Layout { get; set; } = FolderLayout.ByYearMonth;
    public DuplicatePolicy Duplicates { get; set; } = DuplicatePolicy.Skip;

    /// A Live Photo is a still plus a movie sharing one name. Taking one without
    /// the other quietly loses half the picture.
    public bool KeepLivePhotoPairs { get; set; } = true;

    /// .AAE files describe edits made on the phone; most people do not want them.
    public bool IncludeEditSidecars { get; set; }

    public bool VerifySize { get; set; } = true;
    public bool ShowThumbnails { get; set; } = true;
    public bool GridView { get; set; } = true;
    public bool KeepOriginalsTipDismissed { get; set; }

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(AppPaths.SettingsFile))
            {
                var loaded = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(AppPaths.SettingsFile), Json);
                if (loaded is not null) return loaded;
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"Settings unreadable, falling back to defaults: {ex.Message}");
        }
        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            File.WriteAllText(AppPaths.SettingsFile, JsonSerializer.Serialize(this, Json));
        }
        catch (Exception ex)
        {
            Log.Warn($"Could not save settings: {ex.Message}");
        }
    }
}
