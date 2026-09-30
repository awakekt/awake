# Run in the browser

<p class="awake-lede">Run the scene from the earlier pages in a browser with the WebGPU backend, behind AwakeKt's loading screen.</p>

<div class="awake-badges" markdown>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Web (wasmJs)</span>
</div>

The app and `GameRenderPlan` stay in `commonMain` unchanged. This page adds a `wasmJs` target, the
WebGPU backend, and a page to host it.

## Add the WebGPU backend

The WebGPU backend builds on wgpu4k, which only publishes snapshot builds. So the backend is
published as a snapshot too, to Maven Central's snapshot repository, and recent Core releases do
not include it.

Add the snapshot repository in `settings.gradle.kts`:

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

Add the backend to the catalog. For `<snapshot version>`, take the newest `-SNAPSHOT` listed in
the [backend's snapshot metadata](https://central.sonatype.com/repository/maven-snapshots/com/awakekt/awake/backend/webgpu/maven-metadata.xml):

```toml title="gradle/libs.versions.toml"
[libraries]
awake-backend-webgpu = { module = "com.awakekt.awake.backend:webgpu", version = "<snapshot version>" }
```

Then add a browser target and the dependency:

```kotlin title="build.gradle.kts"
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.awake.backend.webgpu)
        }
    }
}
```

The snapshot carries the exact wgpu4k builds it was tested with, so Gradle resolves those without
more setup.

!!! warning "The browser build runs on Core snapshots"
    The WebGPU snapshot depends on Core modules at its own snapshot version, such as
    `0.1.0-alpha.16-SNAPSHOT`. Gradle picks the higher version, so the `wasmJs` target uses those
    Core snapshots rather than `{{ awake_version }}`. Snapshots change with every push to `main`.

## Launch the app

In `wasmJsMain`, hand the same app and plan to `launchWebGpuGame`:

```kotlin title="wasmJsMain/kotlin/Main.kt"
--8<-- "samples/engine-showcase/src/wasmJsTest/kotlin/com/awakekt/awake/showcase/docs/browser/RunInTheBrowserDocsSampleTest.kt:imports"

--8<-- "samples/engine-showcase/src/wasmJsTest/kotlin/com/awakekt/awake/showcase/docs/browser/RunInTheBrowserDocsSampleTest.kt:main"
```

`launchWebGpuGame` finds the `<canvas>` with id `awake-canvas`, keeps it the size of the window,
and draws a frame on every animation frame. Pass `canvasId` to use a different canvas.

## Add the page and the loading screen

Put an `index.html` in `src/wasmJsMain/resources/`:

```html title="src/wasmJsMain/resources/index.html"
--8<-- "website/docs/snippets/get-started/index.html"
```

- The canvas id matches what `launchWebGpuGame` looks for.
- `hello.js` stands for your app's bundle, which is named after the Gradle module.
- `awake-loader.js` is AwakeKt's loading screen. Copy
  [`awake-loader.js`](https://github.com/awakekt/awake/blob/main/awake/backend/webgpu/src/wasmJsMain/resources/awake-loader.js)
  from the WebGPU backend into `src/wasmJsMain/resources/`, next to `index.html`. Load it before
  the bundle.

The loading screen shows the AwakeKt mark and a progress bar: first the `.wasm` download, by
bytes, then WebGPU and shader startup. The first frame drawn to the canvas dismisses it. If the
download fails, the browser has no WebGPU, or the engine fails to start, it shows a message instead
of leaving a black canvas. `data-product` is the name shown after "AwakeKt" on the screen; leave it
out to show only "AwakeKt".

## Run it

```bash
./gradlew :your-module:wasmJsBrowserDevelopmentRun
```

Open the address it prints in a browser with WebGPU. The loading screen suggests a current Chrome
or Edge, or Safari 26 or later.

!!! tip "One plan for both backends"
    The WebGPU backend runs as much of `GameRenderPlan` as it supports and reports anything it
    leaves out, so the plan never needs a browser copy.

!!! warning "No WebGPU, no start"
    In a browser without WebGPU, `launchWebGpuGame` stops before the engine starts, with "WebGPU is
    unavailable in this browser or device", and the loading screen says so.

## See also

- [WebGPU backend](../guides/webgpu.md) for how the backend works.
- [Releases and compatibility](../reference/releases.md) for which targets each release supports.
