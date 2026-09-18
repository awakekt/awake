# Vulkan backend

The Vulkan backend hosts Awake applications on native targets. It provides the desktop window
host, Vulkan device setup, swapchain management, command recording, and native resource upload.

## Desktop entry point

Use `runVulkanDesktopGame` from a desktop source set after creating the shared application
lifecycle. The overload accepts the lifecycle and a `RenderPlan`; custom engine factories are also
available for applications that need them.

Vulkan is the native graphics path. Platform packaging and native-library requirements depend on
the release and target, so check [Releases and compatibility](../releases.md) when selecting a
version.
