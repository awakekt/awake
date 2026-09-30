# AwakeKt Engine

<p class="awake-lede">A Kotlin Multiplatform engine for 2D and 3D apps and games. One codebase runs on desktop, Android, iOS, and the web, with Vulkan and WebGPU underneath.</p>

!!! warning "Alpha"
    AwakeKt is alpha software. APIs and published modules can change between releases. Use the
    version selector in the header to read the docs for the release you use.

## Two ways to build a scene

A scene is ECS content: entities with components. Write it in whichever form suits you.

<div class="grid cards" markdown>

-   **Write Kotlin: the scene DSL**

    ---

    Build entities in code with `scene { entity("…") { … } }`, and add systems that run every
    frame.

    [Your first scene](get-started/first-scene.md)

-   **Describe it as data: scene documents**

    ---

    Describe a scene as data in a `*.scene.json` file, by hand or in AwakeKt Studio, and load it
    at runtime.

    [Load a scene document](get-started/load-a-scene-document.md)

</div>

Both forms describe the same ECS components; neither is a separate engine.

[Get started](get-started/installation.md){ .md-button .md-button--primary }

## Platforms and backends

| Platform | Backend | Notes |
| --- | --- | --- |
| Desktop (JVM) | Vulkan | macOS arm64 and x86_64 through MoltenVK, Linux x86_64. Windows is not shipped yet. |
| Android | Vulkan | `arm64-v8a` and `x86_64`. |
| iOS | Vulkan through MoltenVK | Device and simulator (arm64). |
| Web (wasmJs) | WebGPU | Published as a snapshot only. See [Run in the browser](get-started/run-in-the-browser.md). |

See [Releases and compatibility](reference/releases.md) for versions.
