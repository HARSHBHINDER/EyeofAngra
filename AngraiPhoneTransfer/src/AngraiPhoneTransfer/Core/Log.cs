using System.Text;

namespace AngraiPhoneTransfer.Core;

/// Append-only log so a failed transfer can be explained after the fact.
/// Deliberately tiny: no dependency, no background thread, no network.
public static class Log
{
    private static readonly object Gate = new();
    private static readonly string File = Path.Combine(
        AppPaths.Logs, $"angra-{DateTime.Now:yyyyMMdd}.log");

    public static string CurrentFile => File;

    public static void Info(string message) => Write("INFO ", message);

    public static void Warn(string message) => Write("WARN ", message);

    public static void Error(string message, Exception? ex = null)
        => Write("ERROR", ex is null ? message : $"{message}: {ex.GetType().Name}: {ex.Message}\n{ex.StackTrace}");

    private static void Write(string level, string message)
    {
        try
        {
            lock (Gate)
            {
                System.IO.File.AppendAllText(
                    File,
                    $"{DateTime.Now:HH:mm:ss.fff} {level} {message}{Environment.NewLine}",
                    Encoding.UTF8);
            }
        }
        catch
        {
            // Logging must never take the app down.
        }
    }
}
