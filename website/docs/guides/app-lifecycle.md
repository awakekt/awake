# App lifecycle

<p class="awake-lede">An app is one <code>app { }</code> block: its window, the callbacks the host calls as it starts, draws each frame and shuts down, and the modules and scene it installs.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.engine:bootstrap</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

The app is shared code. A platform entry point, such as `runVulkanDesktopGame` on desktop or
`launchWebGpuGame` in the browser, creates the window and renderer and drives the app; see
[Your first window](../get-started/first-window.md).

## Describe an app

`window` sets the title, size and backend. `ready` runs once the renderer exists, `render` runs every
frame, and `dispose` runs when the app shuts down.

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:app"
```

A host then calls the callbacks in order. A test can call them itself, with a stand-in renderer:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:drive"
```

## Split an app into modules

`appModule { }` holds services and callbacks without a window, so a feature can be written once and
installed into any app with `module(…)`:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:module"
```

`FrameCounter` is an ordinary class:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:frame-counter"
```

`appSpec { }` takes the same block as `app { }` but returns the spec instead of a running app. Use it
in tests and tools that inspect the configuration or create the lifecycle themselves:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:app-spec"
```

## Register scene systems

`scene { }` (or `ecs { }`) installs the scene runtime as a module. It owns the `World` and runs your
systems in two phases: fixed steps for simulation, and one pass per rendered frame.

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AppLifecycleDocsSampleTest.kt:scene-systems"
```

| In `scene { }` | Runs | Use it for |
| --- | --- | --- |
| `fixedSystem(name) { … }` | Zero or more times a frame, each with a delta of 1/60 s | Physics and gameplay that must not depend on frame rate. |
| `update { step, input -> }` | Once per fixed step, after the fixed systems | Small bits of gameplay without a system class. |
| `frameSystem(name) { … }` | Once per rendered frame, with the frame's delta | Cameras, input, animation, anything drawn. |
| `onReady { … }` | Once, after the scene's entities are created | Tagging entities, looking things up by name. |
| `onDispose { … }` | Once, when the app shuts down | Releasing what `onReady` took. |

A system factory runs once, when the renderer is ready and before the scene's entities are created.
Its receiver is the scene runtime, so it can read `world`, `renderer` and `requireService(…)`; look
entities up by name later, in `onReady` or when the system runs. `fixedSystem` and `frameSystem`
return a handle; `runtime.system(handle)` gives back the instance.

## Handle errors

Core catches failures and hands them to the app; it never draws anything. Register an
`AppErrorHandler` like any other service and decide what to show, for instance by storing the error in
the state your UI reads:

```kotlin title="Kotlin"
app {
    service(AppErrorHandler::class, AppErrorHandler { error ->
        failures += error // error.source, error.cause, error.subject, error.retry
    })
    service(AppCoroutineScope::class, AppCoroutineScope(serviceLookup()))
}
```

Each failure reaches the handler once, with where it failed (`AppErrorSource`), its cause, a
`subject` naming what failed, and a `retry` when running the step again makes sense:

| Source | Caught when | `retry` |
| --- | --- | --- |
| `Bootstrap` | The graphics engine or an app `ready { }` callback throws, or a step you launched with `launchStep` does. | `null` for the engine's own start-up; a step you launched with `launchStep` runs again. The engine does not start, as before, and `GraphicsEngine.startupError` is still set. |
| `ProjectLoad` | A `projectRoute` or `deferredRoute` loader throws. The route keeps its loading scene with `Failed(cause)`, as before. | Loads the route again. |
| `SceneSwitch` | A scene a `SceneSwitch` asked for cannot be read or played, or a routed scene fails to go in. Before the old scene is taken out it plays on, as before; after, the new scene plays (a project) or the route that was active is put back (a router). | Asks for the scene again. |
| `Coroutine` | A coroutine launched in an `AppCoroutineScope` throws. Its siblings and the scope go on. | `null` |
| `System` | A system, the app's update block (subject `updateBlock`) or its UI build (subject `ui`) throws in the frame loop. | `null` |

Call `retry` on the frame thread. It returns at once, and a second failure reaches the handler as a
new error.

Work that should stop with the scene, such as waiting on a server's reply for something the scene
spawns, goes in the session's `runtime.session.coroutineScope` instead. It is a child of the
`AppCoroutineScope` when one is registered, so its failures reach the same handler, and it is cancelled
when the scene's session is disposed. It runs off the frame thread, on `sessionDispatcher` (default
`Dispatchers.Default`): send results to a system through a channel rather than changing the world from
the coroutine.

A system that throws is reported once and then **skipped for the rest of the scene**: the frame
carries on with the other systems, the renderer and the UI, and a system that fails every frame does
not call the handler every frame. Its scene's next switch builds the system afresh. Guarding costs one
flag read per system call and allocates nothing on a frame that succeeds. The app's update block is
treated the same way. The UI build is not skipped for good, because it is rebuilt from nothing every
frame and is where the app shows the error: a build that throws draws nothing that frame and runs again
on the next, and the handler hears of it once until a build succeeds.

With no handler registered nothing changes: a failed switch or load is logged and the scene or route
carries on, a failed start sets `startupError`, and a system's exception is thrown out of the frame.
A project you load yourself before the app exists can use the same contract with `launchStep`:

```kotlin title="Kotlin"
scope.launchStep(AppErrorSource.ProjectLoad, errors, subject = "world", onDone = ::play) {
    loadProject(files, physicsWorld = ::createJoltPhysicsWorld)
}
```

## Coroutine scopes

Every long-lived coroutine scope in Awake has an owner that cancels it:

```
AppCoroutineScope (engine:platform; the app; failures go to AppErrorHandler)
├── SceneSession.coroutineScope (scene:runtime; one scene session; cancelled when it is disposed)
└── store scopes (core:state; one store)
```

The approved owners are `AppCoroutineScope`, `SceneSession`, `core:state`'s store scopes
(`DefaultStoreScope`, `MutableStore`, the `reducerStore` factories), and the game server host in
`net:server-ktor` (`ServerHost`, `SaveScheduler`, `GameTransport`, `GameServerHostPlugin`). Four rules
hold for everything else:

1. **Only an owner builds a long-lived scope.** Anything else runs its work in a child of one:
   `scope.childScope(dispatcher)` under a scope it was given, `runtime.session.coroutineScope` for work
   that ends with the scene, or `services.appChildScope(dispatcher)` for work that outlives one scene,
   as the scene router's deferred loads do. A scene asset resolver that loads off the frame gets the
   session's scope from `SceneAssetResolver.attach` before the first frame.
2. **No `GlobalScope`.**
3. **Background coroutines never write to the `World`, the renderer or the `SceneAssetLibrary`.** They
   send results through a `Channel` that a system drains on the frame thread.
4. **Cancellation is never swallowed.** A `catch` that can see a `CancellationException` rethrows it.

The build checks them. `./gradlew awakeVerify` runs `verifyCoroutineScopes`, which fails on a
`CoroutineScope(...)` or `MainScope()` built outside an owner and on any `GlobalScope`. The sites that
predate the rule are listed in `config/coroutine-scopes-baseline.txt`, which only shrinks. Detekt's
`GlobalCoroutineUsage` runs with `./gradlew detekt`. `SuspendFunSwallowedCancellation`, which needs
type resolution, runs with `./gradlew detektMainDesktop` and checks rule 4. It reports a suspend call
inside `runCatching`, and a `try` around one whose `catch` can see a `CancellationException` without
rethrowing it. The cases that predate it are in each module's `detekt-baseline-mainDesktop.xml`.

A debug build also checks rule 3. The scene session records its frame thread when it initializes, and
`World`'s structural changes, `SceneAssetLibrary.requireMesh` and `requireMaterial`, and
`ScenePrewarm.close` then throw on any other thread. The checks are on when `FrameThreadGuard.enabled`
is: on the JVM when assertions are on (`-ea`, as in Gradle's tests) or the `awake.frameThreadChecks`
system property is `true`, and in a debug Kotlin/Native binary. A debug Android app sets it itself. On
wasmJs, which has one thread, they never fire. With the flag off, each check is one field read.

## Properties

`window { }`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | string | `"Awake"` | Window title. |
| `size(width, height)` | pixels | `800 × 600` | Initial window size. |
| `backend` | `default()` · `vulkan()` · `webGpu()` · `openGl()` | `default()` | Which renderer the host should use. |
| `presentMode` | `PresentMode` | `Auto` | How finished frames reach the display. A request; the host logs what it got. |
| `frameRateMode` | `FrameRateMode` | `Auto` | Target frame rate. |
| `throttleCpuWhenNotForeground` | boolean | `true` | Slow down when the window is in the background. |
| `backgroundFrameRate` | frames per second | `15` | The rate used while throttled. |

App callbacks, on `app { }` and `appModule { }`:

| Callback | Called |
| --- | --- |
| `ready { renderer -> }` | Once, when the renderer exists. Suspends. |
| `render { frame -> }` | Every frame. `frame` has `delta`, `viewportWidth`, `viewportHeight`, `input` and `density`. |
| `resize { width, height -> }` | When the surface changes size. |
| `pause { }`, `resume { }` | When the host pauses and resumes the app. |
| `dispose { }` | Once, at shutdown. |
| `service(type, value)` | Registers a value any module can look up with `requireService(type)`. Register an `AppErrorHandler` to receive failures; see [Handle errors](#handle-errors). |

## How it works

Every callback is added to a list, and each module adds to the same lists, so installing two modules
runs both of their `render` blocks every frame, in the order they were installed. `dispose` callbacks
run in reverse, so the last module installed is the first torn down. After them, services that are
`AutoCloseable` are closed.

Each frame, the scene runtime adds the frame's delta to an accumulator and runs one fixed step,
fixed systems then `update`, for every 1/60 s it holds. Then it runs the frame systems, then its own
transform and render systems, so a frame system's changes are drawn in the same frame. A fixed
system that implements `InterpolatedSystem` also gets `interpolate(world, alpha)` just before the
frame systems, with how far the clock is into the next step, so it can blend its last two states. A new scene
runs one frame pass with a delta of 0 right after `onReady`.

!!! warning "At most five fixed steps per frame"
    After a long frame the runtime runs five fixed steps and drops the rest of the backlog, so the
    simulation slows down instead of freezing the display. `maxFixedStepsPerFrame(15)` in the scene
    block raises the cap. The runtime's `fixedStep` reports what each frame did, before the frame
    systems run: `stepsThisFrame`, the `droppedSeconds` and whole `droppedSteps` it dropped, and
    `droppedStepsTotal`. To be told instead of reading it each frame, add a listener with
    `fixedStep.addTimeDroppedListener { seconds, steps -> }`.

### Dropped time in a networked game

A game that predicts its local player against a server has to account for every fixed step. After a
long frame, such as a garbage collection pause in the browser, the player has walked less than the
time that passed, while the server walked on. The two drift apart until a correction warps the
player back. Either:

- **Send or replay the dropped steps.** In a frame system, or a `TimeDroppedListener`, read
  `droppedSteps` and send the inputs those steps would have sent, or step the local player that many
  more times, so it walks as long as the server does.
- **Raise the cap** with `maxFixedStepsPerFrame(...)`, so ordinary long frames drop nothing. Keep a
  cap: when the steps themselves are what is slow, running more of them makes the next frame slower
  still.

A capability's systems read the same report from `SceneHostServices.fixedStep`. Don't copy the
default cap of 5 into the game and work out the dropped steps from the frame's delta: read them.

!!! tip "One `Input` per app"
    Every app has one `Input` service, created with it. Look it up with `requireService(Input::class)`;
    see [Input](input.md).

## See also

- [ECS](ecs.md) for writing systems.
- [Scene DSL](scene-dsl.md) for the entities inside `scene { }`.
- [Your first window](../get-started/first-window.md) for the desktop entry point.
