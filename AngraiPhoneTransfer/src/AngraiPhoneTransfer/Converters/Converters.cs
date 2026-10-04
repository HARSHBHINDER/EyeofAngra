using System.Globalization;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using AngraiPhoneTransfer.Devices;

namespace AngraiPhoneTransfer.Converters;

/// true -> Collapsed. The inverse of the framework's own converter.
public sealed class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is true ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// Anything non-null (and non-empty for strings) becomes Visible.
public sealed class PresenceToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var present = value switch
        {
            null => false,
            string s => !string.IsNullOrWhiteSpace(s),
            int i => i > 0,
            _ => true
        };
        if (parameter is string p && p.Equals("invert", StringComparison.OrdinalIgnoreCase)) present = !present;
        return present ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// Binds an enum property to a toggle: IsChecked is true when the bound value
/// equals the converter parameter.
public sealed class EnumEqualsConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is not null && parameter is string name &&
           string.Equals(value.ToString(), name, StringComparison.OrdinalIgnoreCase);

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => Binding.DoNothing;
}

/// Picks the line icon for an item: folder, photo, video, audio, document, file.
public sealed class KindToIconConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var kind = value as MediaKind?;
        var key = kind switch
        {
            MediaKind.Folder => "IconFolder",
            MediaKind.Photo => "IconPhoto",
            MediaKind.Video => "IconVideo",
            MediaKind.Audio => "IconAudio",
            MediaKind.Document => "IconDoc",
            _ => "IconFile"
        };
        return Application.Current?.TryFindResource(key) as Geometry;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
