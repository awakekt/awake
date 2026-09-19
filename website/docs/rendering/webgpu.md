# WebGPU backend

The WebGPU backend runs Awake applications in a browser through WasmJs. It consumes the same
backend-neutral render plan and translates resources and passes to browser WebGPU objects.

Add `libs.awake.webgpu` to `wasmJsMain`; the alias and shared version setup are in
[Installation](../getting-started.md#graphics-backends).

Use the WebGPU launch function from a WasmJs source set and provide the application factory for the
browser host. Browser capabilities and shader behavior are governed by the WebGPU implementation
available in the target browser.

See [Releases and compatibility](../releases.md) for the supported target matrix for a specific
release. The [Engine Showcase browser entry point](https://github.com/awakekt/awake/blob/main/samples/engine-showcase/src/wasmJsMain/kotlin/com/awakekt/awake/showcase/app/Main.kt)
is a complete compiled integration example.
