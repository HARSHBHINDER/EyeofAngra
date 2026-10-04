using System.Diagnostics;
using System.Text;
using System.Windows;
using System.Windows.Diagnostics;
using System.Windows.Threading;
using AngraiPhoneTransfer.Core;
using AngraiPhoneTransfer.ViewModels;
using AngraiPhoneTransfer.Views;

namespace AngraiPhoneTransfer;

/// `AngraiPhoneTransfer.exe --selftest` builds the real window against the real
/// view model, lets it lay out and settle, and reports every WPF data-binding
/// failure it saw. CI runs it on the published build so a broken binding or a
/// missing resource fails the build instead of the user's first launch.
internal static class SelfTest
{
    private static readonly List<string> Messages = new();
    private static readonly string ReportPath =
        Path.Combine(AppContext.BaseDirectory, "selftest.log");

    public static void Run(Application app)
    {
        PresentationTraceSources.Refresh();
        PresentationTraceSources.DataBindingSource.Listeners.Add(new Collector());
        PresentationTraceSources.DataBindingSource.Switch.Level = SourceLevels.Warning;

        MainViewModel? model = null;
        Window? window = null;
        var failures = new List<string>();

        try
        {
            model = new MainViewModel();
            window = new MainWindow { DataContext = model, ShowInTaskbar = false };
            window.Show();
            _ = model.StartupAsync();
        }
        catch (Exception ex)
        {
            failures.Add("The window could not be built: " + ex);
        }

        // Let layout, the device scan and the first render settle before judging.
        var timer = new DispatcherTimer(DispatcherPriority.ApplicationIdle)
        {
            Interval = TimeSpan.FromSeconds(6)
        };
        timer.Tick += (_, _) =>
        {
            timer.Stop();

            try
            {
                window?.UpdateLayout();
                if (window is { ActualWidth: 0 } && !failures.Any())
                    failures.Add("The window never laid out (ActualWidth stayed 0).");
            }
            catch (Exception ex)
            {
                failures.Add("Layout threw: " + ex);
            }

            failures.AddRange(Messages.Where(m => m.Contains("Error", StringComparison.Ordinal)));

            var report = new StringBuilder();
            report.AppendLine($"AngraiPhoneTransfer self-test — {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
            report.AppendLine($"Window laid out: {window?.ActualWidth:0} x {window?.ActualHeight:0}");
            report.AppendLine($"Binding trace lines: {Messages.Count}");
            foreach (var m in Messages) report.AppendLine("  trace: " + m.Trim());
            report.AppendLine(failures.Count == 0 ? "RESULT: pass" : $"RESULT: {failures.Count} failure(s)");
            foreach (var f in failures) report.AppendLine("  FAIL: " + f);

            try { File.WriteAllText(ReportPath, report.ToString()); }
            catch (Exception ex) { Log.Warn("Self-test report unwritable: " + ex.Message); }

            try { window?.Close(); } catch { /* nothing left to save */ }
            model?.Dispose();
            app.Shutdown(failures.Count == 0 ? 0 : 1);
        };
        timer.Start();
    }

    private sealed class Collector : TraceListener
    {
        public override void Write(string? message)
        {
            if (!string.IsNullOrWhiteSpace(message)) Messages.Add(message!);
        }

        public override void WriteLine(string? message) => Write(message);
    }
}
