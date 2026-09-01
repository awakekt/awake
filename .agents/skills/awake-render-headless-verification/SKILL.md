---
name: awake-render-headless-verification
description: Verify what the engine actually renders, without a window. Read before writing any pixel test, before claiming a rendering change works, and before asking a human to look at a screenshot.
---

# Awake Render: Headless Verification

A rendering change is not verified by a passing test. It is verified by a **number you measured**
that would be different if the change were wrong. This skill is how to get that number, and the
traps that make a measurement lie.

## Rule 1: never ask a human to look

If the question is "does this render correctly", render it here and read the pixels. Every
capability needed is in the repo:

| Question | Tool |
|---|---|
| Does this geometry/material render right? | `Renderer.renderToTexture` + `readPixels` |
| Does the frame the app PRESENTS render right? | `HeadlessSurface` + `Renderer.draw` + `readPresentedPixels` |
| Does the app's own plan render right? | `VulkanEngine(lifecycle, plan).createBackendResources(HeadlessSurface(w, h))` |
| Does the app's scene wiring render right? | the above, plus `SceneLoader.instantiate` and `RenderSystem.update` |

Worked examples: `ShowcasePlanFrameTest` (all four layers) and
`RendererHeadlessCascadedShadowTest` (probe scenes).

## Rule 2: a fixture is not the app

A hand-built renderer -- one pipeline, one depth target, no content features -- cannot reproduce a
scene that looks wrong on screen while every probe passes. It has happened: the offscreen path
once recorded no content features at all, and the shadow probes all passed throughout.

Prefer, in order: the app's real `RenderPlan` > a plan you built > a pipeline you built.

## Rule 3: every pixel assertion needs a recorded negative control

Before committing a pixel test, break the thing it tests and write the measured broken value into
its KDoc. If you cannot make it fail, it is not a test.

This is not optional diligence -- probes in this repo have passed while measuring nothing:

- an "acne" probe that counted the caster's own dark pixels as distance zero
- a contact probe whose tolerance was tighter than one pixel of its own camera
- a shadow-mode comparison whose "dark pixels" were mostly the black sky

## Rule 4: measure world distances under an orthographic camera

Under perspective, a caster of height `h` seen from `e` up hides ground out to
`half * e / (e - h)`. That occlusion reads as displacement and will be attributed to whatever you
are testing. `Lens.projection = Orthographic` removes it, and makes one pixel one fixed distance
everywhere in the frame.

## Rule 5: prove the run actually ran

More wrong conclusions in this repo have come from stale runs than from stale code.

- `cmake` must be on PATH or `:awake:backend:vulkan:bindings` fails and Gradle reports **the
  previous run's XML**. Export `/opt/homebrew/bin` and restart the daemon -- it does not inherit
  a PATH exported after it started.
- `sed -i` replaces the file's inode; Gradle's watcher misses it and reports `UP-TO-DATE`. Edit in
  place (the Edit tool, or a Python truncate-write).
- When a control run must execute, pass `--rerun`.
- A control that "passes" is suspicious. Check the task list for `compileKotlinDesktop`.

## Rule 6: read the code before sweeping a constant

If a knob's effect is non-monotonic -- 80, 672, 632, 72, 0 across a sweep -- stop. Something else
is coupled to it. In the case that produced those numbers, cascade SELECTION had been made to
depend on the normal offset, so changing the offset moved fragments between cascades instead of
moving the lookup within one. No amount of tuning would have found that; reading the shader did.

## What to assert

Assert properties of the frame, not exact pixels, and name the file a scene came from:

- shadows exist: count ground pixels between a background cutoff and a shadow cutoff, never
  simply "below the shadow cutoff" -- a cleared sky is black and swamps the count
- acne: dark pixels whose **four** neighbours are all lit. Three lit neighbours is the convex
  corner of a real shadow
- brightness: compare against the ambient-only value you compute from the shader's own constants
  (`ambient = colour * 0.08`, Reinhard, then gamma), so "lit by nothing" fails rather than dims

## Load a scene, never copy its numbers

`SceneLoader.decode(file.readText())` costs nothing and catches what a copy hides. Copying the
cascaded-shadows scene's values hid two bugs at once: the ground node's `scale` was ignored, and
the authored sun `direction` had never reached the renderer at all because `SceneLight` had no
such field.
