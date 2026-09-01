using System.Windows;
using System.Windows.Threading;
using AngraiPhoneTransfer.Core;
using AngraiPhoneTransfer.ViewModels;
using AngraiPhoneTransfer.Views;

namespace AngraiPhoneTransfer;

public partial class App : Application
{
    private MainViewModel? model;

    /// Marshals a callback onto the UI thread — the device worker reports
    /// progress from its own thread.
    public static void OnUi(Action action)
    {
        var dispatcher = Current?.Dispatcher;
        if (dispatcher is null) return;
        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(action);
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        DispatcherUnhandledException += OnDispatcherException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            Log.Error("Unhandled exception", args.ExceptionObject as Exception);

        Log.Info("AngraiPhoneTransfer starting.");

        // CI runs the published build with --selftest to prove the window and
        // every binding in it still come up.
        if (e.Args.Any(a => string.Equals(a, "--selftest", StringComparison.OrdinalIgnoreCase)))
        {
            SelfTest.Run(this);
            return;
        }

        model = new MainViewModel();
        var window = new MainWindow { DataContext = model };
        MainWindow = window;
        window.Show();

        _ = model.StartupAsync();
    }

    private void OnDispatcherException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        Log.Error("Unhandled UI exception", e.Exception);
        MessageBox.Show(
            "Something went wrong:\n\n" + e.Exception.Message +
            "\n\nThe details are in:\n" + Log.CurrentFile,
            "AngraiPhoneTransfer",
            MessageBoxButton.OK,
            MessageBoxImage.Warning);
        e.Handled = true;
    }

    protected override void OnExit(ExitEventArgs e)
    {
        model?.Dispose();
        Log.Info("AngraiPhoneTransfer closing.");
        base.OnExit(e);
    }
}
