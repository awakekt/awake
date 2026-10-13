- **A desktop app can start on Windows and Linux, not only macOS.** The published
  `shader-compiler-desktop` jar carried `libawake_naga` for `macos-arm64` alone in every
  0.6.0-0.11.1 release, because the one job that built it ran on a single macOS host and nothing
  else fed it the other platforms. `VulkanShaderResolver` compiles every WGSL shader through it at
  startup, so a Windows or Linux app threw `Native Naga library 'awake_naga' not found` before its
  first frame. A `naga-desktop` matrix now builds the library on macOS, Linux and Windows and the
  publish jobs collect all three before the jar is packaged, and `verifyNagaDesktopNatives` fails
  the build if one goes missing again.
