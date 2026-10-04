using System.Collections.Concurrent;

namespace AngraiPhoneTransfer.Core;

/// The Windows Portable Devices COM API is apartment-bound and the MTP wrapper
/// on top of it is not thread-safe, so every single call into the phone is
/// funnelled through this one long-lived STA thread. Callers await instead of
/// blocking, which keeps the UI responsive during long enumerations.
public sealed class DeviceWorker : IDisposable
{
    private readonly BlockingCollection<Action> queue = new();
    private readonly Thread thread;

    public DeviceWorker()
    {
        thread = new Thread(Pump)
        {
            Name = "Angra device worker",
            IsBackground = true
        };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
    }

    /// True while the worker is executing a job — used to hold back thumbnail
    /// requests while a transfer has the device.
    public bool Busy { get; private set; }

    public int Pending => queue.Count;

    private void Pump()
    {
        foreach (var job in queue.GetConsumingEnumerable())
        {
            Busy = true;
            try { job(); }
            catch (Exception ex) { Log.Error("Device job threw", ex); }
            finally { Busy = false; }
        }
    }

    public Task<T> RunAsync<T>(Func<T> work)
    {
        var tcs = new TaskCompletionSource<T>(TaskCreationOptions.RunContinuationsAsynchronously);
        try
        {
            queue.Add(() =>
            {
                try { tcs.TrySetResult(work()); }
                catch (Exception ex) { tcs.TrySetException(ex); }
            });
        }
        catch (InvalidOperationException)
        {
            tcs.TrySetCanceled(); // queue completed — the app is shutting down
        }
        return tcs.Task;
    }

    public Task RunAsync(Action work) => RunAsync<object?>(() => { work(); return null; });

    public void Dispose()
    {
        queue.CompleteAdding();
        thread.Join(TimeSpan.FromSeconds(3));
        queue.Dispose();
    }
}
