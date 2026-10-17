# Awake Engine Window

`awake:engine:window` opens each platform's native window and turns its input into the engine's
`Input`. It drives the `engine:platform` lifecycle and knows nothing about a GPU API: a backend
creates its own surface from the window handle it is given.

## Owns

- Desktop: the GLFW window (`GlfwWindow`, in `libawake-window`), its frame loop
  (`runDesktopWindow`), keyboard, pointer, scroll, text input, clipboard and cursor.
- iOS: `AwakeMetalView`, a `UIView` on a `CAMetalLayer` with a `CADisplayLink` loop, touch and
  text input.
- Android: `AwakeSurfaceView`, a `SurfaceView` with its render thread, touch, key and IME input.
- Browser (Web / Wasm): `runBrowserCanvas`, HTML canvas resizing, touch, pointer, wheel, keyboard, and
  hidden `<input>` IME / clipboard text bridge (`DomTextInputBridge`).

Each reports its display to the lifecycle: the density, the safe area in dp (Android's system
bars and display cutout, iOS's view safe area, the browser's `env(safe-area-inset-*)`; a desktop
window has none), and the real dots per inch where the platform can tell (Android's display
metrics, and on desktop the monitor the window is on, from its video mode and the physical size it
reports, checked about once a second so a window dragged to another monitor follows). A browser and
iOS report no dots per inch.

## Does not own

- Surface or swapchain creation, which belong to each backend.
- UI, scenes, or app composition.

## Native library

`libawake-window` is built per host with `./gradlew :awake:engine:window:buildDesktopNative` and
ships in the desktop jar as `natives/<platform>/`. A release jar carries every supported platform;
`verifyDesktopNatives` fails if one is missing. Needs GLFW: `brew install glfw` on macOS,
`libglfw3-dev` on Linux. On Windows (Visual Studio with C++, and CMake) the build fetches GLFW 3.4
itself and ships it as `glfw3.dll` beside `awake-window.dll`; `GlfwWindow` loads it first, and
`libawake-vulkan` shares it.
