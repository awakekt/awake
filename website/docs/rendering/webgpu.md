# WebGPU backend

The WebGPU backend runs Awake applications in a browser through WasmJs. It consumes the same
backend-neutral render plan and translates resources and passes to browser WebGPU objects.

Use the WebGPU launch function from a WasmJs source set and provide the application factory for the
browser host. Browser capabilities and shader behavior are governed by the WebGPU implementation
available in the target browser.

See [Releases and compatibility](../releases.md) for the supported target matrix for a specific
release.
