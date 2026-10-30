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
    simulation slows down instead of freezing the display.

!!! tip "One `Input` per app"
    Every app has one `Input` service, created with it. Look it up with `requireService(Input::class)`;
    see [Input](input.md).

## See also

- [ECS](ecs.md) for writing systems.
- [Scene DSL](scene-dsl.md) for the entities inside `scene { }`.
- [Your first window](../get-started/first-window.md) for the desktop entry point.
