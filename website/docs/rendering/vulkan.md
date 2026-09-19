# Vulkan backend

The Vulkan backend hosts Awake applications on native targets. It provides the desktop window
host, Vulkan device setup, swapchain management, command recording, and native resource upload.

For the baseline desktop dependency set, see [Installation](../getting-started.md#installation).
Add `libs.awake.vulkan` to the desktop source set.

## Desktop entry point

Use `runVulkanDesktopGame` from a desktop source set after creating the shared application
lifecycle. The overload accepts the lifecycle and a `RenderPlan`; custom engine factories are also
available for applications that need them.

Vulkan is the native graphics path. Platform packaging and native-library requirements depend on
the release and target, so check [Releases and compatibility](../releases.md) when selecting a
version. The [Engine Showcase desktop entry point](https://github.com/awakekt/awake/blob/main/samples/engine-showcase/src/desktopMain/kotlin/com/awakekt/awake/showcase/app/Main.kt)
is a complete compiled integration example.
