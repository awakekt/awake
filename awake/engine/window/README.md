# Awake Engine Window

`awake:engine:window` opens each platform's native window and turns its input into the engine's
`Input`. It drives the `engine:platform` lifecycle and knows nothing about a GPU API: a backend
creates its own surface from the window handle it is given.

## Owns

- Desktop: the GLFW window (`GlfwWindow`, in `libawake-window`), its frame loop
  (`runDesktopWindow`), keyboard, pointer, scroll, text input, clipboard and cursor, and a
  controller through GLFW's gamepad API (`pollGlfwGamepad`).
- iOS: `AwakeMetalView`, a `UIView` on a `CAMetalLayer` with a `CADisplayLink` loop, touch and
  text input, and a controller through GameController (`pollGameController`).
- Android: `AwakeSurfaceView`, a `SurfaceView` with its render thread, touch, key and IME input, and
  a controller's key and joystick events.
- Browser (Web / Wasm): `runBrowserCanvas`, HTML canvas resizing, touch, pointer, wheel, keyboard, a
  controller through the Gamepad API, and hidden `<input>` IME / clipboard text bridge
  (`DomTextInputBridge`).

Each reads one controller into `Input.setGamepadButton`, by button position: GLFW's gamepad
mappings on desktop, the standard layout in a browser (one in another layout is not read, and a
browser lists a controller only once a button on it is pressed), GameController's extended gamepad
on iOS, and Android's `KEYCODE_BUTTON_*`, d-pad keys and hat and trigger axes. A desktop window
reads it only while focused, and a controller that goes away releases its buttons.

On desktop, typed text is what the platform sends through GLFW's character callback, so the
keyboard layout, Shift, Caps Lock, AltGr and dead keys apply as they do in any other app. Keys
themselves drive only editing (Backspace, Delete, Enter, the arrows, Home and End) and Ctrl+A, C, V
and X (Cmd on macOS). Ctrl with Alt is AltGr on Windows, so it types its character rather than
taking a shortcut.

Each reports its display to the lifecycle: the density, the safe area in dp (Android's system
bars and display cutout, iOS's view safe area, the browser's `env(safe-area-inset-*)`; a desktop
window has none), and the real dots per inch where the platform can tell (Android's display
metrics, and on desktop the monitor the window is on, from its video mode and the physical size it
reports, checked about once a second so a window dragged to another monitor follows). A browser and
iOS report no dots per inch.

Launch parameters and host actions, on every platform: `LaunchParameters` (a list of values per
key and bare flags, read from the page URL query, the `main` arguments, an Android launch
`Intent`, or an iOS launch URL the host app passes in) and `HostActions` (`openUrl` everywhere,
`pageReload` on the web only, `null` elsewhere). The parsing is common code; each platform has a
small factory (`browserLaunchParameters`, `desktopLaunchParameters`, `Intent.launchParameters`,
`iosLaunchParameters`, and `browserHostActions` and its siblings). See
[Launch parameters](../../../website/docs/guides/launch-parameters.md); they are untrusted input.
`LaunchEnvironment` sits beside them: the page's `LaunchOrigin` on the web (`null` elsewhere) and
a `PrimaryInput`, touch-first or pointer-first, read once at launch (`browserLaunchEnvironment`,
`desktopLaunchEnvironment`, `androidLaunchEnvironment`, `iosLaunchEnvironment`). Both are hints,
not security boundaries.

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
