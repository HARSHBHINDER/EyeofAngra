using System.Globalization;

namespace AngraiPhoneTransfer.Core;

/// Human-readable sizes, rates and durations, used everywhere in the UI.
public static class Humanize
{
    private static readonly string[] Units = { "B", "KB", "MB", "GB", "TB" };

    public static string Bytes(long value)
    {
        if (value < 0) value = 0;
        double v = value;
        var unit = 0;
        while (v >= 1024 && unit < Units.Length - 1)
        {
            v /= 1024;
            unit++;
        }
        var digits = unit == 0 ? 0 : v >= 100 ? 0 : v >= 10 ? 1 : 2;
        return v.ToString("N" + digits, CultureInfo.CurrentCulture) + " " + Units[unit];
    }

    public static string Rate(double bytesPerSecond)
        => bytesPerSecond <= 0 ? "—" : Bytes((long)bytesPerSecond) + "/s";

    public static string Duration(TimeSpan span)
    {
        if (span < TimeSpan.Zero) span = TimeSpan.Zero;
        if (span.TotalHours >= 1) return $"{(int)span.TotalHours}h {span.Minutes:00}m";
        if (span.TotalMinutes >= 1) return $"{span.Minutes}m {span.Seconds:00}s";
        return $"{Math.Max(1, span.Seconds)}s";
    }

    public static string Count(int value, string singular, string plural)
        => $"{value:N0} {(value == 1 ? singular : plural)}";
}
