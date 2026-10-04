using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using AngraiPhoneTransfer.ViewModels;

namespace AngraiPhoneTransfer.Views;

public partial class MainWindow : Window
{
    public MainWindow() => InitializeComponent();

    private MainViewModel? Model => DataContext as MainViewModel;

    private void OnMinimise(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void OnMaximise(object sender, RoutedEventArgs e)
        => WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    /// Double-clicking a folder opens it; double-clicking a file leaves the
    /// single-click selection it just made alone.
    private void OnItemDoubleClick(object sender, MouseButtonEventArgs e)
    {
        var node = FindNode(e.OriginalSource as DependencyObject);
        if (node is null || !node.IsFolder || Model is null) return;
        if (Model.OpenCommand.CanExecute(node)) Model.OpenCommand.Execute(node);
        e.Handled = true;
    }

    private static NodeViewModel? FindNode(DependencyObject? source)
    {
        while (source is not null)
        {
            if (source is FrameworkElement { DataContext: NodeViewModel node }) return node;
            source = VisualTreeHelper.GetParent(source);
        }
        return null;
    }

    protected override void OnPreviewKeyDown(KeyEventArgs e)
    {
        base.OnPreviewKeyDown(e);
        if (e.Key != Key.Escape || Model is null) return;

        if (Model.ShowConfirm) Model.ConfirmCancelCommand.Execute(null);
        else if (Model.ShowSettings) Model.ToggleSettingsCommand.Execute(null);
        else if (Model.HasResult) Model.DismissResultCommand.Execute(null);
        else return;

        e.Handled = true;
    }
}
