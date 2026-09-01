using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using AngraiPhoneTransfer.Core;
using AngraiPhoneTransfer.Devices;
using AngraiPhoneTransfer.Mvvm;
using AngraiPhoneTransfer.Transfer;

namespace AngraiPhoneTransfer.ViewModels;

public enum ItemFilter
{
    All,
    Photos,
    Videos,
    Files
}

/// One step in the location bar.
public sealed class Crumb
{
    public required string Title { get; init; }
    public required string Path { get; init; }
    public bool IsLast { get; set; }
}

/// Everything the single window binds to.
public sealed class MainViewModel : ObservableObject, IDisposable
{
    private const int PageSize = 300;

    private readonly DeviceService devices = new();
    private readonly TransferEngine engine;
    private readonly AppSettings settings = AppSettings.Load();
    private readonly List<NodeViewModel> location = new();

    private CancellationTokenSource? thumbnails;
    private CancellationTokenSource? transfer;
    private string? currentPath;
    private string? cameraRollPath;
    private string? rootPath;
    private bool virtualLocation;
    private Action? confirmAction;

    public MainViewModel()
    {
        engine = new TransferEngine(devices);

        RescanCommand = new AsyncRelayCommand(ScanAsync, () => !IsTransferring);
        ConnectCommand = new AsyncRelayCommand(p => ConnectAsync(p as PhoneDevice), _ => !IsTransferring);
        OpenCommand = new AsyncRelayCommand(p => OpenAsync(p as NodeViewModel), _ => !IsTransferring);
        UpCommand = new AsyncRelayCommand(GoUpAsync, () => CanGoUp);
        CameraRollCommand = new AsyncRelayCommand(GoCameraRollAsync, () => cameraRollPath is not null && !IsTransferring);
        DeviceRootCommand = new AsyncRelayCommand(GoRootAsync, () => rootPath is not null && !IsTransferring);
        AllMediaCommand = new AsyncRelayCommand(GoAllMediaAsync, () => cameraRollPath is not null && !IsTransferring);
        RefreshCommand = new AsyncRelayCommand(RefreshAsync, () => currentPath is not null && !IsTransferring);
        CrumbCommand = new AsyncRelayCommand(p => p is Crumb c ? NavigateAsync(c.Path, c.Title) : Task.CompletedTask,
            _ => !IsTransferring);

        SelectAllCommand = new RelayCommand(() => SetChecked(Filtered(), true));
        SelectNoneCommand = new RelayCommand(() => SetChecked(location, false));
        SelectPhotosCommand = new RelayCommand(() => SetChecked(Filtered().Where(n => n.Kind == MediaKind.Photo), true));
        SelectVideosCommand = new RelayCommand(() => SetChecked(Filtered().Where(n => n.Kind == MediaKind.Video), true));

        ChooseDestinationCommand = new RelayCommand(ChooseDestination);
        CopyCommand = new AsyncRelayCommand(() => StartAsync(TransferMode.Copy), () => HasSelection && !IsTransferring);
        MoveCommand = new RelayCommand(ConfirmMove, () => HasSelection && !IsTransferring);
        CancelTransferCommand = new RelayCommand(() => transfer?.Cancel(), () => IsTransferring);

        LoadMoreCommand = new RelayCommand(LoadMore, () => CanLoadMore);
        SetFilterCommand = new RelayCommand(p =>
        {
            if (p is string s && Enum.TryParse<ItemFilter>(s, out var f)) Filter = f;
        });
        SetLayoutCommand = new RelayCommand(p =>
        {
            if (p is string s && Enum.TryParse<FolderLayout>(s, out var l)) Layout = l;
        });
        SetDuplicatesCommand = new RelayCommand(p =>
        {
            if (p is string s && Enum.TryParse<DuplicatePolicy>(s, out var d)) Duplicates = d;
        });

        OpenDestinationCommand = new RelayCommand(() => OpenInExplorer(Destination));
        OpenReportCommand = new RelayCommand(() => OpenInExplorer(lastReportPath, select: true), () => !string.IsNullOrEmpty(lastReportPath));
        OpenLogsCommand = new RelayCommand(() => OpenInExplorer(AppPaths.Logs));
        DismissTipCommand = new RelayCommand(() =>
        {
            settings.KeepOriginalsTipDismissed = true;
            settings.Save();
            Raise(nameof(ShowKeepOriginalsTip));
        });
        DismissResultCommand = new RelayCommand(() => HasResult = false);
        ToggleSettingsCommand = new RelayCommand(() => ShowSettings = !ShowSettings);
        ToggleViewCommand = new RelayCommand(() => GridView = !GridView);
        ConfirmAcceptCommand = new RelayCommand(() =>
        {
            ShowConfirm = false;
            var action = confirmAction;
            confirmAction = null;
            action?.Invoke();
        });
        ConfirmCancelCommand = new RelayCommand(() =>
        {
            ShowConfirm = false;
            confirmAction = null;
        });
    }

    // ---------------------------------------------------------------- devices

    public ObservableCollection<PhoneDevice> Devices { get; } = new();

    private PhoneDevice? device;
    public PhoneDevice? Device
    {
        get => device;
        private set
        {
            if (!Set(ref device, value)) return;
            Raise(nameof(HasDevice));
            Raise(nameof(DeviceName));
            Raise(nameof(DeviceSubtitle));
            Raise(nameof(BatteryText));
            Raise(nameof(HasBattery));
        }
    }

    public bool HasDevice => Device is not null;
    public string DeviceName => Device?.Name ?? "No phone connected";
    public string DeviceSubtitle => Device is null
        ? "Plug in over USB"
        : string.Join("  ·  ", new[] { Device.Model, Device.SerialNumber is { Length: > 6 } s ? "…" + s[^6..] : null }
            .Where(v => !string.IsNullOrWhiteSpace(v))!);
    public bool HasBattery => Device?.BatteryPercent is not null;
    public string BatteryText => Device?.BatteryPercent is { } b ? $"{b}%" : string.Empty;

    private StorageSummary? storage;
    public StorageSummary? Storage
    {
        get => storage;
        private set
        {
            if (!Set(ref storage, value)) return;
            Raise(nameof(StorageText));
            Raise(nameof(StorageFraction));
            Raise(nameof(HasStorage));
        }
    }

    public bool HasStorage => Storage is { Capacity: > 0 };
    public double StorageFraction => Storage?.UsedFraction ?? 0;
    public string StorageText => Storage is { Capacity: > 0 } s
        ? $"{Humanize.Bytes(s.Used)} used of {Humanize.Bytes(s.Capacity)}"
        : string.Empty;

    // -------------------------------------------------------------- browsing

    public ObservableCollection<NodeViewModel> Items { get; } = new();
    public ObservableCollection<Crumb> Breadcrumbs { get; } = new();

    private string locationTitle = "Not connected";
    public string LocationTitle
    {
        get => locationTitle;
        private set => Set(ref locationTitle, value);
    }

    private string status = "Looking for a phone…";
    public string Status
    {
        get => status;
        private set => Set(ref status, value);
    }

    private bool isWorking;
    public bool IsWorking
    {
        get => isWorking;
        private set
        {
            if (Set(ref isWorking, value)) RefreshCommandStates();
        }
    }

    private ItemFilter filter = ItemFilter.All;
    public ItemFilter Filter
    {
        get => filter;
        set
        {
            if (!Set(ref filter, value)) return;
            Raise(nameof(IsFilterAll));
            Raise(nameof(IsFilterPhotos));
            Raise(nameof(IsFilterVideos));
            Raise(nameof(IsFilterFiles));
            Repage();
        }
    }

    public bool IsFilterAll => Filter == ItemFilter.All;
    public bool IsFilterPhotos => Filter == ItemFilter.Photos;
    public bool IsFilterVideos => Filter == ItemFilter.Videos;
    public bool IsFilterFiles => Filter == ItemFilter.Files;

    private string search = string.Empty;
    public string Search
    {
        get => search;
        set
        {
            if (Set(ref search, value)) Repage();
        }
    }

    private bool gridView = true;
    public bool GridView
    {
        get => gridView;
        set
        {
            if (!Set(ref gridView, value)) return;
            settings.GridView = value;
            settings.Save();
            Raise(nameof(ListView));
        }
    }

    public bool ListView => !GridView;

    public bool CanGoUp => currentPath is not null && !virtualLocation && Breadcrumbs.Count > 1;

    private int shown;
    public bool CanLoadMore => shown < FilteredCount;
    public string CountText
    {
        get
        {
            var total = FilteredCount;
            if (total == 0) return "Nothing here";
            return shown < total
                ? $"Showing {shown:N0} of {total:N0}"
                : Humanize.Count(total, "item", "items");
        }
    }

    private int FilteredCount => Filtered().Count();

    // ------------------------------------------------------------- selection

    public int SelectedCount => location.Count(n => n.IsChecked);
    public long SelectedBytes => location.Where(n => n.IsChecked).Sum(n => n.Node.Size);
    public bool HasSelection => SelectedCount > 0;
    public string SelectionText => SelectedCount == 0
        ? "Nothing selected"
        : $"{Humanize.Count(SelectedCount, "item", "items")}  ·  {Humanize.Bytes(SelectedBytes)}";

    // --------------------------------------------------------------- options

    public string Destination
    {
        get => settings.Destination;
        private set
        {
            if (settings.Destination == value) return;
            settings.Destination = value;
            settings.Save();
            Raise();
        }
    }

    public FolderLayout Layout
    {
        get => settings.Layout;
        set
        {
            if (settings.Layout == value) return;
            settings.Layout = value;
            settings.Save();
            Raise();
        }
    }

    public DuplicatePolicy Duplicates
    {
        get => settings.Duplicates;
        set
        {
            if (settings.Duplicates == value) return;
            settings.Duplicates = value;
            settings.Save();
            Raise();
        }
    }

    public bool KeepLivePhotoPairs
    {
        get => settings.KeepLivePhotoPairs;
        set { settings.KeepLivePhotoPairs = value; settings.Save(); Raise(); }
    }

    public bool IncludeEditSidecars
    {
        get => settings.IncludeEditSidecars;
        set { settings.IncludeEditSidecars = value; settings.Save(); Raise(); }
    }

    public bool VerifySize
    {
        get => settings.VerifySize;
        set { settings.VerifySize = value; settings.Save(); Raise(); }
    }

    public bool ShowThumbnails
    {
        get => settings.ShowThumbnails;
        set
        {
            settings.ShowThumbnails = value;
            settings.Save();
            Raise();
            if (value) StartThumbnailPump();
        }
    }

    public bool ShowKeepOriginalsTip => !settings.KeepOriginalsTipDismissed && HasDevice;

    public string VersionText =>
        "Version " + (System.Reflection.Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "1.0.0");

    private bool showSettings;
    public bool ShowSettings
    {
        get => showSettings;
        set => Set(ref showSettings, value);
    }

    // -------------------------------------------------------------- transfer

    private bool isTransferring;
    public bool IsTransferring
    {
        get => isTransferring;
        private set
        {
            if (Set(ref isTransferring, value)) RefreshCommandStates();
        }
    }

    private double progressFraction;
    public double ProgressFraction
    {
        get => progressFraction;
        private set
        {
            if (Set(ref progressFraction, value)) Raise(nameof(ProgressPercent));
        }
    }

    public string ProgressPercent => $"{ProgressFraction * 100:0}%";

    private string progressHeadline = string.Empty;
    public string ProgressHeadline
    {
        get => progressHeadline;
        private set => Set(ref progressHeadline, value);
    }

    private string progressFile = string.Empty;
    public string ProgressFile
    {
        get => progressFile;
        private set => Set(ref progressFile, value);
    }

    private string progressDetail = string.Empty;
    public string ProgressDetail
    {
        get => progressDetail;
        private set => Set(ref progressDetail, value);
    }

    private bool hasResult;
    public bool HasResult
    {
        get => hasResult;
        private set => Set(ref hasResult, value);
    }

    private string resultTitle = string.Empty;
    public string ResultTitle
    {
        get => resultTitle;
        private set => Set(ref resultTitle, value);
    }

    private string resultDetail = string.Empty;
    public string ResultDetail
    {
        get => resultDetail;
        private set => Set(ref resultDetail, value);
    }

    private bool resultIsWarning;
    public bool ResultIsWarning
    {
        get => resultIsWarning;
        private set => Set(ref resultIsWarning, value);
    }

    private string lastReportPath = string.Empty;

    // --------------------------------------------------------- confirmation

    private bool showConfirm;
    public bool ShowConfirm
    {
        get => showConfirm;
        private set => Set(ref showConfirm, value);
    }

    private string confirmTitle = string.Empty;
    public string ConfirmTitle
    {
        get => confirmTitle;
        private set => Set(ref confirmTitle, value);
    }

    private string confirmMessage = string.Empty;
    public string ConfirmMessage
    {
        get => confirmMessage;
        private set => Set(ref confirmMessage, value);
    }

    private string confirmAccept = "Continue";
    public string ConfirmAccept
    {
        get => confirmAccept;
        private set => Set(ref confirmAccept, value);
    }

    // --------------------------------------------------------------- commands

    public AsyncRelayCommand RescanCommand { get; }
    public AsyncRelayCommand ConnectCommand { get; }
    public AsyncRelayCommand OpenCommand { get; }
    public AsyncRelayCommand UpCommand { get; }
    public AsyncRelayCommand CameraRollCommand { get; }
    public AsyncRelayCommand DeviceRootCommand { get; }
    public AsyncRelayCommand AllMediaCommand { get; }
    public AsyncRelayCommand RefreshCommand { get; }
    public AsyncRelayCommand CrumbCommand { get; }
    public RelayCommand SelectAllCommand { get; }
    public RelayCommand SelectNoneCommand { get; }
    public RelayCommand SelectPhotosCommand { get; }
    public RelayCommand SelectVideosCommand { get; }
    public RelayCommand ChooseDestinationCommand { get; }
    public AsyncRelayCommand CopyCommand { get; }
    public RelayCommand MoveCommand { get; }
    public RelayCommand CancelTransferCommand { get; }
    public RelayCommand LoadMoreCommand { get; }
    public RelayCommand SetFilterCommand { get; }
    public RelayCommand SetLayoutCommand { get; }
    public RelayCommand SetDuplicatesCommand { get; }
    public RelayCommand OpenDestinationCommand { get; }
    public RelayCommand OpenReportCommand { get; }
    public RelayCommand OpenLogsCommand { get; }
    public RelayCommand DismissTipCommand { get; }
    public RelayCommand DismissResultCommand { get; }
    public RelayCommand ToggleSettingsCommand { get; }
    public RelayCommand ToggleViewCommand { get; }
    public RelayCommand ConfirmAcceptCommand { get; }
    public RelayCommand ConfirmCancelCommand { get; }

    // ----------------------------------------------------------------- flow

    public async Task StartupAsync()
    {
        GridView = settings.GridView;
        await ScanAsync();
    }

    public async Task ScanAsync()
    {
        IsWorking = true;
        Status = "Looking for a phone…";
        try
        {
            var found = await devices.DiscoverAsync();
            Devices.Clear();
            foreach (var d in found) Devices.Add(d);
            Raise(nameof(HasDevices));
            Raise(nameof(DeviceCountText));

            if (found.Count == 0)
            {
                Device = null;
                Status = "No phone found. Connect it with a cable, unlock it, and tap Trust.";
                return;
            }

            var pick = found.FirstOrDefault(d => d.IsApple) ?? found[0];
            await ConnectAsync(pick);
        }
        catch (Exception ex)
        {
            Log.Error("Scan failed", ex);
            Status = "Could not look for devices: " + ex.Message;
        }
        finally
        {
            IsWorking = false;
        }
    }

    public bool HasDevices => Devices.Count > 0;
    public string DeviceCountText => Devices.Count switch
    {
        0 => "No devices",
        1 => "1 device",
        _ => $"{Devices.Count} devices"
    };

    private async Task ConnectAsync(PhoneDevice? target)
    {
        if (target is null) return;
        IsWorking = true;
        Status = $"Opening {target.Name}…";
        try
        {
            var connected = await devices.ConnectAsync(target.DeviceId);
            Device = connected;
            Raise(nameof(ShowKeepOriginalsTip));

            if (connected is null)
            {
                Status = "That device went away. Reconnect the cable and scan again.";
                return;
            }

            Storage = await devices.StorageAsync();
            rootPath = (await devices.RootAsync())?.FullName;
            cameraRollPath = await devices.FindCameraRollAsync();
            RefreshCommandStates();

            if (cameraRollPath is not null)
            {
                await NavigateAsync(cameraRollPath, "Camera roll");
            }
            else if (rootPath is not null)
            {
                await NavigateAsync(rootPath, connected.Name);
                Status = "No DCIM folder — unlock the phone and tap Trust, then scan again.";
            }
        }
        catch (Exception ex)
        {
            Log.Error("Connect failed", ex);
            Status = "Could not open the phone: " + ex.Message;
        }
        finally
        {
            IsWorking = false;
        }
    }

    private Task GoCameraRollAsync() => cameraRollPath is null ? Task.CompletedTask : NavigateAsync(cameraRollPath, "Camera roll");

    private Task GoRootAsync() => rootPath is null ? Task.CompletedTask : NavigateAsync(rootPath, Device?.Name ?? "Phone");

    private Task RefreshAsync() => currentPath is null
        ? Task.CompletedTask
        : virtualLocation ? GoAllMediaAsync() : NavigateAsync(currentPath, LocationTitle);

    private async Task OpenAsync(NodeViewModel? node)
    {
        if (node is null || !node.IsFolder) return;
        await NavigateAsync(node.Node.FullName, node.Name);
    }

    private async Task GoUpAsync()
    {
        if (Breadcrumbs.Count < 2) return;
        var parent = Breadcrumbs[^2];
        await NavigateAsync(parent.Path, parent.Title);
    }

    private async Task NavigateAsync(string path, string title)
    {
        StopThumbnails();
        IsWorking = true;
        Status = "Reading " + title + "…";
        try
        {
            var nodes = await devices.ListAsync(path);
            currentPath = path;
            virtualLocation = false;
            LocationTitle = title;
            ReplaceLocation(nodes);
            BuildBreadcrumbs(path);
            Status = $"{LocationTitle} — {Humanize.Count(location.Count, "item", "items")}";
        }
        catch (Exception ex)
        {
            Log.Error($"Navigate to {path} failed", ex);
            Status = "Could not open that folder: " + ex.Message;
        }
        finally
        {
            IsWorking = false;
        }
    }

    /// Flattens every photo and video under DCIM into one list — the view most
    /// people actually want when emptying a camera roll.
    private async Task GoAllMediaAsync()
    {
        if (cameraRollPath is null) return;
        StopThumbnails();
        IsWorking = true;
        Status = "Scanning the whole camera roll…";
        try
        {
            using var scan = new CancellationTokenSource();
            var counted = 0;
            var files = await devices.EnumerateFilesAsync(
                cameraRollPath,
                n =>
                {
                    counted = n;
                    App.OnUi(() => Status = $"Scanning the camera roll — {counted:N0} files so far…");
                },
                scan.Token);

            currentPath = cameraRollPath;
            virtualLocation = true;
            LocationTitle = "All photos & videos";
            ReplaceLocation(files);

            Breadcrumbs.Clear();
            Breadcrumbs.Add(new Crumb { Title = "All photos & videos", Path = cameraRollPath, IsLast = true });
            Raise(nameof(CanGoUp));

            Status = $"All photos & videos — {Humanize.Count(location.Count, "file", "files")}";
        }
        catch (Exception ex)
        {
            Log.Error("Full camera roll scan failed", ex);
            Status = "Could not scan the camera roll: " + ex.Message;
        }
        finally
        {
            IsWorking = false;
        }
    }

    private void ReplaceLocation(IReadOnlyList<MediaNode> nodes)
    {
        foreach (var old in location) old.PropertyChanged -= OnNodeChanged;
        location.Clear();

        foreach (var node in nodes)
        {
            var vm = new NodeViewModel(node);
            vm.PropertyChanged += OnNodeChanged;
            location.Add(vm);
        }

        Repage();
        RaiseSelection();
    }

    private void OnNodeChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(NodeViewModel.IsChecked)) RaiseSelection();
    }

    private IEnumerable<NodeViewModel> Filtered()
    {
        IEnumerable<NodeViewModel> query = location;

        query = Filter switch
        {
            ItemFilter.Photos => query.Where(n => n.IsFolder || n.Kind == MediaKind.Photo),
            ItemFilter.Videos => query.Where(n => n.IsFolder || n.Kind == MediaKind.Video),
            ItemFilter.Files => query.Where(n => n.IsFolder || n.Kind is not (MediaKind.Photo or MediaKind.Video)),
            _ => query
        };

        if (!string.IsNullOrWhiteSpace(Search))
        {
            var term = Search.Trim();
            query = query.Where(n => n.Name.Contains(term, StringComparison.OrdinalIgnoreCase));
        }

        return query;
    }

    private void Repage()
    {
        shown = 0;
        Items.Clear();
        LoadMore();
    }

    private void LoadMore()
    {
        var page = Filtered().Skip(shown).Take(PageSize).ToList();
        foreach (var item in page) Items.Add(item);
        shown += page.Count;

        Raise(nameof(CountText));
        Raise(nameof(CanLoadMore));
        LoadMoreCommand.RaiseCanExecuteChanged();
        StartThumbnailPump();
    }

    private void SetChecked(IEnumerable<NodeViewModel> nodes, bool value)
    {
        foreach (var n in nodes) n.IsChecked = value;
        RaiseSelection();
    }

    private void RaiseSelection()
    {
        Raise(nameof(SelectedCount));
        Raise(nameof(SelectedBytes));
        Raise(nameof(HasSelection));
        Raise(nameof(SelectionText));
        CopyCommand.RaiseCanExecuteChanged();
        MoveCommand.RaiseCanExecuteChanged();
    }

    // ------------------------------------------------------------ thumbnails

    private void StopThumbnails()
    {
        thumbnails?.Cancel();
        thumbnails = null;
    }

    private void StartThumbnailPump()
    {
        if (!ShowThumbnails || IsTransferring) return;
        StopThumbnails();
        var cts = new CancellationTokenSource();
        thumbnails = cts;
        _ = PumpThumbnailsAsync(cts.Token);
    }

    private async Task PumpThumbnailsAsync(CancellationToken token)
    {
        try
        {
            foreach (var item in Items.ToList())
            {
                if (token.IsCancellationRequested) return;
                if (!item.ThumbnailWanted || item.ThumbnailLoaded) continue;

                var bytes = await devices.ThumbnailAsync(item.Node.FullName);
                if (token.IsCancellationRequested) return;
                item.ApplyThumbnail(bytes);
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"Thumbnail pump stopped: {ex.Message}");
        }
    }

    // -------------------------------------------------------------- transfer

    private void ChooseDestination()
    {
        var dialog = new Microsoft.Win32.OpenFolderDialog
        {
            Title = "Where should the files land?",
            Multiselect = false
        };

        try
        {
            if (Directory.Exists(Destination)) dialog.InitialDirectory = Destination;
        }
        catch { /* a stale path is not worth a crash */ }

        if (dialog.ShowDialog() == true && !string.IsNullOrWhiteSpace(dialog.FolderName))
        {
            Destination = dialog.FolderName;
            Status = "Saving to " + Destination;
        }
    }

    private void ConfirmMove()
    {
        var undeletable = SelectedNodes().Count(n => !n.CanDelete);
        var detail = undeletable > 0
            ? $"\n\n{Humanize.Count(undeletable, "item", "items")} cannot be deleted by the phone — those will be copied and left in place."
            : string.Empty;

        ConfirmTitle = "Move off the phone?";
        ConfirmMessage =
            $"{SelectionText} will be copied to\n{Destination}\n\n" +
            "Each file is verified against the size the phone reports before its original is deleted. " +
            "Anything that fails to copy stays on the phone." + detail;
        ConfirmAccept = "Copy, verify, then delete";
        confirmAction = () => _ = StartAsync(TransferMode.Move);
        ShowConfirm = true;
    }

    private List<MediaNode> SelectedNodes() => location.Where(n => n.IsChecked).Select(n => n.Node).ToList();

    private async Task StartAsync(TransferMode mode)
    {
        if (IsTransferring) return;

        var chosen = SelectedNodes();
        if (chosen.Count == 0) return;

        StopThumbnails();
        HasResult = false;
        IsTransferring = true;
        ProgressHeadline = mode == TransferMode.Move ? "Moving off the phone" : "Copying to this PC";
        ProgressFile = "Preparing…";
        ProgressDetail = string.Empty;
        ProgressFraction = 0;

        transfer = new CancellationTokenSource();
        var token = transfer.Token;

        try
        {
            var files = await ExpandAsync(chosen, token);
            if (files.Count == 0)
            {
                Status = "Nothing to transfer — the selection held no files.";
                return;
            }

            var options = new TransferOptions
            {
                Destination = Destination,
                Mode = mode,
                Layout = Layout,
                Duplicates = Duplicates,
                VerifySize = VerifySize
            };

            var progress = new Progress<TransferProgress>(p =>
            {
                ProgressFraction = p.Fraction;
                ProgressFile = p.CurrentFile;
                ProgressHeadline = $"{(mode == TransferMode.Move ? "Moving" : "Copying")} {p.FileIndex:N0} of {p.FileCount:N0}";
                ProgressDetail =
                    $"{Humanize.Bytes(p.BytesDone)} of {Humanize.Bytes(p.BytesTotal)}  ·  " +
                    $"{Humanize.Rate(p.BytesPerSecond)}  ·  {Humanize.Duration(p.Remaining)} left";
            });

            var result = await engine.RunAsync(files, options, progress, token);
            ShowResult(result, mode);

            if (result.Moved > 0) await RefreshAsync();
        }
        catch (Exception ex)
        {
            Log.Error("Transfer failed", ex);
            ResultIsWarning = true;
            ResultTitle = "The transfer stopped";
            ResultDetail = ex.Message;
            HasResult = true;
        }
        finally
        {
            IsTransferring = false;
            transfer?.Dispose();
            transfer = null;
            StartThumbnailPump();
        }
    }

    /// Turns the selection into a flat file list: folders are walked, and Live
    /// Photo partners and edit sidecars are pulled in when asked for.
    private async Task<IReadOnlyList<MediaNode>> ExpandAsync(List<MediaNode> chosen, CancellationToken token)
    {
        var files = new Dictionary<string, MediaNode>(StringComparer.OrdinalIgnoreCase);

        foreach (var node in chosen)
        {
            if (token.IsCancellationRequested) break;

            if (node.IsFolder)
            {
                ProgressFile = "Reading " + node.Name + "…";
                foreach (var file in await devices.EnumerateFilesAsync(node.FullName, null, token))
                    files[file.FullName] = file;
            }
            else
            {
                files[node.FullName] = node;
            }
        }

        if (KeepLivePhotoPairs || IncludeEditSidecars)
        {
            var byStem = location
                .Where(n => !n.IsFolder)
                .GroupBy(n => Key(n.Node), StringComparer.OrdinalIgnoreCase)
                .ToDictionary(g => g.Key, g => g.Select(n => n.Node).ToList(), StringComparer.OrdinalIgnoreCase);

            foreach (var node in files.Values.ToList())
            {
                if (!byStem.TryGetValue(Key(node), out var siblings)) continue;
                foreach (var sibling in siblings)
                {
                    if (files.ContainsKey(sibling.FullName)) continue;
                    var take = sibling.Kind switch
                    {
                        MediaKind.Video => KeepLivePhotoPairs && node.Kind == MediaKind.Photo,
                        MediaKind.Sidecar => IncludeEditSidecars,
                        _ => false
                    };
                    if (take) files[sibling.FullName] = sibling;
                }
            }
        }

        if (!IncludeEditSidecars)
        {
            foreach (var sidecar in files.Values.Where(f => f.Kind == MediaKind.Sidecar).ToList())
                files.Remove(sidecar.FullName);
        }

        return files.Values.OrderBy(f => f.FullName, StringComparer.OrdinalIgnoreCase).ToList();

        static string Key(MediaNode node)
            => (Path.GetDirectoryName(node.FullName) ?? string.Empty) + "|" + node.Stem;
    }

    private void ShowResult(TransferResult result, TransferMode mode)
    {
        lastReportPath = result.ReportPath;
        OpenReportCommand.RaiseCanExecuteChanged();

        var verb = mode == TransferMode.Move ? "moved" : "copied";
        ResultIsWarning = result.Failed > 0 || result.Cancelled;
        ResultTitle = result.Cancelled
            ? "Transfer stopped"
            : result.Failed > 0
                ? $"Finished with {Humanize.Count(result.Failed, "problem", "problems")}"
                : $"{Humanize.Count(result.Copied, "file", "files")} {verb}";

        var parts = new List<string>
        {
            $"{Humanize.Bytes(result.BytesTransferred)} in {Humanize.Duration(result.Elapsed)}"
        };
        if (result.Skipped > 0) parts.Add($"{result.Skipped:N0} already there");
        if (result.Moved > 0) parts.Add($"{result.Moved:N0} removed from the phone");
        if (result.Failed > 0) parts.Add($"{result.Failed:N0} failed — see the report");

        ResultDetail = string.Join("  ·  ", parts);
        HasResult = true;
        Status = ResultTitle + " — " + ResultDetail;
    }

    private void RefreshCommandStates()
    {
        RescanCommand.RaiseCanExecuteChanged();
        ConnectCommand.RaiseCanExecuteChanged();
        OpenCommand.RaiseCanExecuteChanged();
        UpCommand.RaiseCanExecuteChanged();
        CameraRollCommand.RaiseCanExecuteChanged();
        DeviceRootCommand.RaiseCanExecuteChanged();
        AllMediaCommand.RaiseCanExecuteChanged();
        RefreshCommand.RaiseCanExecuteChanged();
        CrumbCommand.RaiseCanExecuteChanged();
        CopyCommand.RaiseCanExecuteChanged();
        MoveCommand.RaiseCanExecuteChanged();
        CancelTransferCommand.RaiseCanExecuteChanged();
        Raise(nameof(CanGoUp));
    }

    private void BuildBreadcrumbs(string path)
    {
        Breadcrumbs.Clear();
        var segments = path.Split(new[] { '\\', '/' }, StringSplitOptions.RemoveEmptyEntries);
        var built = string.Empty;
        foreach (var segment in segments)
        {
            built = string.IsNullOrEmpty(built) ? "\\" + segment : built + "\\" + segment;
            Breadcrumbs.Add(new Crumb { Title = segment, Path = built });
        }
        if (Breadcrumbs.Count > 0) Breadcrumbs[^1].IsLast = true;
        Raise(nameof(CanGoUp));
        UpCommand.RaiseCanExecuteChanged();
    }

    private static void OpenInExplorer(string path, bool select = false)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(path)) return;
            if (!select) Directory.CreateDirectory(path);
            Process.Start(new ProcessStartInfo
            {
                FileName = "explorer.exe",
                Arguments = select ? $"/select,\"{path}\"" : $"\"{path}\"",
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            Log.Warn($"Could not open {path}: {ex.Message}");
        }
    }

    public void Dispose()
    {
        StopThumbnails();
        transfer?.Cancel();
        settings.Save();
        devices.Dispose();
    }
}
