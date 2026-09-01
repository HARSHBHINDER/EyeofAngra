namespace AngraiPhoneTransfer.Devices;

/// A connected portable device, flattened out of the COM object so it can be
/// handed to the UI thread safely.
public sealed class PhoneDevice
{
    public required string DeviceId { get; init; }
    public required string Name { get; init; }
    public string? Model { get; init; }
    public string? Manufacturer { get; init; }
    public string? SerialNumber { get; init; }
    public string? FirmwareVersion { get; init; }
    public uint? BatteryPercent { get; init; }
    public bool IsApple =>
        (Manufacturer?.Contains("Apple", StringComparison.OrdinalIgnoreCase) ?? false) ||
        Name.Contains("iPhone", StringComparison.OrdinalIgnoreCase) ||
        Name.Contains("iPad", StringComparison.OrdinalIgnoreCase) ||
        (Model?.Contains("iPhone", StringComparison.OrdinalIgnoreCase) ?? false);

    public override string ToString() => Name;
}

/// Storage headline for the connected device.
public sealed class StorageSummary
{
    public string Name { get; init; } = "Internal Storage";
    public long Capacity { get; init; }
    public long Free { get; init; }
    public long Used => Math.Max(0, Capacity - Free);
    public double UsedFraction => Capacity <= 0 ? 0 : Math.Clamp((double)Used / Capacity, 0, 1);
}
