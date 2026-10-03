# Awake Engine Window

`awake:engine:window` opens each platform's native window and turns its input into the engine's
`Input`. It drives the `engine:platform` lifecycle and knows nothing about a GPU API: a backend
creates its own surface from the window handle it is given.

## Owns

- Desktop: the GLFW window (`GlfwWindow`, in `libawake-window`), its frame loop
  (`runDesktopWindow`), keyboard, pointer, scroll, text input, clipboard and cursor.
- iOS: `AwakeMetalView`, a `UIView` on a `CAMetalLayer` with a `CADisplayLink` loop, touch and
  text input.

## Does not own

- Surface or swapchain creation, which belong to each backend.
- UI, scenes, or app composition.

## Native library

`libawake-window` is built per host with `./gradlew :awake:engine:window:buildDesktopNative` and
ships in the desktop jar as `natives/<platform>/`. A release jar carries every supported platform;
`verifyDesktopNatives` fails if one is missing. Needs GLFW: `brew install glfw` on macOS,
`libglfw3-dev` on Linux.
