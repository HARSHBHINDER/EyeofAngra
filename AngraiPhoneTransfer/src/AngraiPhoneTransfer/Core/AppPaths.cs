namespace AngraiPhoneTransfer.Core;

/// Everything the app writes lives under one folder in LocalAppData: settings,
/// a rolling log, and one CSV report per transfer. Nothing else is touched.
public static class AppPaths
{
    public static string Root { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "AngraiPhoneTransfer");

    public static string Logs => Ensure(Path.Combine(Root, "logs"));
    public static string Reports => Ensure(Path.Combine(Root, "reports"));
    public static string SettingsFile => Path.Combine(Ensure(Root), "settings.json");

    public static string DefaultDestination
    {
        get
        {
            var pictures = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
            if (string.IsNullOrWhiteSpace(pictures)) pictures = Path.GetTempPath();
            return Path.Combine(pictures, "AngraiPhoneTransfer");
        }
    }

    private static string Ensure(string path)
    {
        try { Directory.CreateDirectory(path); } catch { /* logged by the caller that needs it */ }
        return path;
    }
}
