# ai.yuzu.common.concurrent

> v0.0.1 🍊 Safe background execution.

- `AsyncRunner` — runs tasks on virtual threads; any `Throwable` is forwarded to every `ErrorSink`
  (so it can reach the frontend) and propagated to returned futures.
- `ErrorSink` — receiver of asynchronous failures. `LoggingErrorSink` is always registered; the
  realtime reporter (added later) pushes them to the UI.
- `CancelToken` — cooperative cancellation; binds working threads and interrupts them on cancel
  (which also aborts blocking `HttpClient.send`).

Rule: use `ReentrantLock`, never `synchronized`, in code that runs on virtual threads (JDK 21 pinning).
