# What the engine gives an app

<p class="awake-lede">A checklist of the pieces every app needs that Awake already has, so you look here before writing your own loading screen state, crash recorder, playback clock or id set.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

An audit of one shipping app found it re-implementing engine pieces it could have used: its own
layered content source, a loading state, launch-option parsing, a crash recorder and a snapshot
playback clock. Each row below names what to use instead and where it lives. The test for whether
something belongs here: an app can't run and ship without it. Finished services a studio would buy
(a crash-report backend, a self-updating launcher, full client replication) are not the engine's.

## Content and loading

| You need | Use | Module |
|---|---|---|
| A public set before sign-in and protected content after it | `LayeredAssetSource`, `attachProtected` / `detachProtected` | `awake:project`; see [Protected content](../guides/protected-content.md) |
| A read token that refreshes and retries | `TokenAuthorizedFetch`, `KtorContentTransport` | `awake:project`, `awake:project:transport-ktor` |
| Files fetched ahead of play, with byte progress | `PrefetchableAssetSource.prefetchWithProgress` | `awake:project` |
| Named loading stages declared as data | `ContentStages`, `ContentStage` | `awake:project` |
| One state for a loading screen's bytes | `ContentStages.state`: `ContentLoad` (idle, loading, waiting for content, ready, too new, failed) | `awake:project` |
| A loading screen's bar, label and retry button | `ContentLoad.loadingView()`: `LoadingView` (phase, fraction, error, can retry) | `awake:project` |
| A default loading bar drawn from that model | `ShadcnLoadingBar(label, fraction, error, retryLabel, onRetry)` | `awake:ui:shadcn` |
| "This content is newer than the app" without matching exception text | `isTooNew(throwable)`, `ContentTooNew` | `awake:core:io` |
| A scene switch's progress, a wait for sign-in, a failure | `SceneSwitchStatus.state`: `SceneSwitchState` (`Loading`, `WaitingForContent`, `Ready`, `Failed`) | `awake:project:runtime` |
| A first scene that only loads after sign-in | `loadProject(files, EntryGate(placeholderScene))` | `awake:project:runtime` |
| Knowing when a streaming scene's reads have gone quiet | `ReadActivitySource.awaitQuiet(quietFor)` | `awake:project` |
| Content cached between runs | `platformContentCache(namespace)`, `FileSystemContentCache`, `IndexedDbContentCache` | `awake:project` |

`ContentLoad` describes staging bytes and `SceneSwitchState` describes swapping scenes; a loading
screen that shows both reads both. Neither draws anything: the app owns its loading screen.

## Launch, host and platform

| You need | Use | Module |
|---|---|---|
| Launch options from the URL, the command line, an `Intent` or a launch URL | `LaunchParameters` | `awake:engine:window`; see [Launch parameters](../guides/launch-parameters.md) |
| Open a link in the browser | `HostActions.openUrl(url)`, which refuses anything but `http`, `https` and `mailto` | `awake:engine:window` |
| Fullscreen, a page reload, a frameless window | `HostActions.fullscreen`, `pageReload`, `windowFrame` (null where the platform has none) | `awake:engine:window` |

## Crashes and diagnostics

| You need | Use | Module |
|---|---|---|
| A record of what happened before a tab or process died | `CrashRecorder`, `CrashSession`, `CrashBreadcrumb` | `awake:core:diagnostics` |
| The app's own counters in that record | `CrashBreadcrumb.metrics` | `awake:core:diagnostics` |
| The record kept in a browser | `LocalStorageCrashStorage`, `installPageCrashHooks(recorder, sampler)` (wasmJs) | `awake:core:diagnostics` |
| Sending the record to your service | Implement `CrashReportSender`; call `recorder.sendPending(sender)` | `awake:core:diagnostics` |
| No flood of the same error | `ErrorSampler.admit(key)` | `awake:core:diagnostics` |
| Debug readings and overlays | `DebugRegistry`, `DebugSource` | `awake:core:diagnostics`; see [Debug tools](../guides/debug-tools.md) |

Core ships no crash-report service and no HTTP client for one: `CrashReportSender` is where your own
client plugs in.

## Networking

| You need | Use | Module |
|---|---|---|
| A playback clock for remote entities behind server snapshots | `SnapshotTimeline`, or `AdaptiveSnapshotTimeline`, which learns the snapshot interval and jitter | `awake:net:prediction`; see [Client prediction](../guides/client-prediction.md) |
| A frame loop that survives one bad delta | `AdaptiveSnapshotTimeline.inputMode = TimelineInputMode.Clamp` | `awake:net:prediction` |
| Prediction and reconciliation | `PredictionHistory`, `reconcile(input, config, rules)`, `PredictedEntity` | `awake:net:prediction` |

## Testing and CI

| You need | Use | Module |
|---|---|---|
| A game played with no window, for CI | `VulkanEngine.playHeadless(width, height, frames)`, or `-Dawake.capture=frame.png` on a desktop run | `awake:backend:vulkan`; see [Vulkan backend](../guides/vulkan.md) |
| A screenshot of a project or scene as a PNG | `VulkanEngine.snapshotPng(file, width, height)`, or `awake render <scene>` from the project CLI | `awake:backend:vulkan`, `awake:project:cli` |

Both need a Vulkan driver; Mesa's lavapipe is enough on a machine with no GPU.

## Collections

| You need | Use | Module |
|---|---|---|
| A sorted set of 64-bit ids refilled every tick, and what entered and left since the last | `SortedLongSet`, `SortedLongSet.diff(previous, current, onEnter, onLeave)` | `awake:core:pool` |
| Scratch objects reused every frame | `ScratchPool` | `awake:core:pool` |
| A map from `Long` or `Int` keys without boxing | `MutableLongObjectMap`, `MutableIntObjectMap`, `MutableIntSet`, `MutableLongSet` | `androidx.collection`, which Core already depends on |
