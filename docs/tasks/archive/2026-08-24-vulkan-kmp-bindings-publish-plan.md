# vulkan-kmp: publishing Awake's Vulkan bindings as the first maintained KMP Vulkan library

Decision (2026-08-24): **core profile first, vk.xml later; published from this repo.** The
library is the raw Vulkan API surface only — `Vk*` structs/enums/handles, the `external fun
vk*` JNI layer, Android/MoltenVK platform glue. Awake's renderer, `render:contract`, and ASL
are consumers, never contents; the relationship is LWJGL-to-engine.

## What exists (recon, 2026-08-24)

- `awake:backend:vulkan:bindings` — 132 Kotlin files: 110 `external fun vk*`, 52 enums, ~40
  structs. The proven core rendering path: instance → physical device → device/queues →
  swapchain → render pass/framebuffer → pipeline → command buffers → draw → sync, plus
  debug utils and Android/iOS surfaces. Targets: desktop JVM (JNI), Android (JNI +
  `bindings:android-native`), iosArm64 (MoltenVK); wasmJs deliberately off (browsers have no
  Vulkan — WebGPU is that story).
- **The boundary already holds.** `bindings` has zero project dependencies on engine modules
  and zero imports outside its own package; `backend:vulkan` api-consumes it. Nothing to
  untangle before publishing.
- `awake:backend:vulkan:generator` — Kotlin-first: reflects over the hand-authored model
  classes and emits the C++ JNI glue. It is the reason adding a function is one Kotlin class
  + regeneration, and it is NOT vk.xml-driven — that inversion is phase B.
- Living conformance suite: the backend's headless GPU tests (pixel baselines, shadow map)
  exercise the bindings on real devices every CI run.

## Phase A — publish the core profile (weeks)

1. **Naming** — needs one decision: the artifact group. The repo-wide `com.awakekt.awake`
   rename is planned as a single pre-publish pass (see memory/decision log); publishing
   bindings *now* under `io.github.awakelab` and renaming later breaks early adopters.
   Options: (a) do the awake-lab rename for the published bindings artifacts only, as an
   island; (b) execute the full repo rename pass first; (c) publish under the current group
   and accept one coordinated break. Lean: (a).
   Working artifact names: `vulkan-kmp` (bindings), `vulkan-kmp-android-native`.
2. **Product framing** — bindings README rewritten as a standalone library front page:
   what's covered (the core profile list, honestly), what isn't (yet), platform matrix,
   MoltenVK setup, the generator's role, versioning policy (track Vulkan minor versions in
   the artifact version metadata).
3. **Publish wiring** — the modules already apply `awake.publish-convention`; add the POM
   identity, confirm `android-native` packs into the AAR, and dry-run `publishToMavenLocal`
   from a consumer sample outside the repo graph.
4. **Docs site** — `kmp-docs-site` skill shape: getting-started (triangle on all three
   platforms), coverage table generated from the binding surface (a small reflection dump —
   the generator already walks it), Dokka API reference.
5. **Conformance badge** — CI job that runs the headless GPU suite and publishes the result;
   "tested on real devices every commit" is the differentiator no first-mover competitor
   will have.

### Phase A progress — setup cleanup (landed 2026-08-24)

- **The boundary claim is now true in the build, not just the sources.** `bindings` carried
  `implementation(":awake:core:input")` backing a single KDoc cross-link and zero code; the
  dependency is gone and the link is plain text. Bindings now have literally no engine
  dependencies.
- **Two triplicated hardcode patterns extracted to build-logic**:
  `VulkanDesktopEnv.environment()` (the Homebrew ICD glob + DYLD fallback that `bindings`,
  `backend:vulkan`, and `samples:studio` each hand-copied) and
  `registerGeneratedDefCinterop(...)` (the write-def-with-linkerOpts dance jolt/MoltenVK/naga
  each hand-rolled). All six call sites consume the shared helpers.
- **arm64 parity with jolt confirmed**: desktop macOS arm64 (host CMake), Android
  `arm64-v8a` + `x86_64` (`android-native` abiFilters), iOS device + Apple-Silicon simulator
  (both MoltenVK xcframework slices wired). `awake.publish-convention` added to `bindings`
  with the `Vulkan KMP Bindings` POM; `android-native` (plain AAR module) still needs its
  publish wiring during the naming step.
- Verified: bindings + backend GPU desktop suites, jolt iOS cinterop compile, naga iOS
  framework link, studio drift test — all through the shared helpers.
- **Naming + publish chain landed (2026-08-24)**: `coordinates("com.awakekt.awake",
  "vulkan-kmp")` on the bindings (name unique, so the capability-collision guard stays
  satisfied); `android-native` publishes as `vulkan-kmp-android-native`
  (`AndroidSingleVariantLibrary`, release variant). Maven Local dry-run produces all six
  artifacts and the Android POM's cross-dependency resolves — the wiring gap that motivated
  the item. Also fixed en route: the dokka convention's README-includes format break that had
  every module's publication failing.

### Phase A — complete (2026-08-24)

The remaining three items landed in one pass:
- **External-consumer smoke** (`tools/vulkan-kmp-smoke/`): a standalone Gradle project —
  deliberately NOT in the root build, so resolution goes through real Maven metadata — that
  consumes `com.awakekt.awake:vulkan-kmp` from mavenLocal and runs a live
  `vkCreateInstance`/`vkDestroyInstance` round trip through the published jar, JNI library,
  loader, and MoltenVK. It also documents two things every external macOS consumer needs:
  the ICD/DYLD environment, and the portability opt-in
  (`VK_KHR_portability_enumeration` + `ENUMERATE_PORTABILITY` flag, without which loaders
  ≥ 1.3.216 hide MoltenVK and creation fails `VK_ERROR_INCOMPATIBLE_DRIVER`).
- **Conformance CI**: the lavapipe job now runs the bindings' own desktop suite
  (`VulkanDesktopNativeSmokeTest` had no CI home before — only the renderer's suite ran).
- **Docs site**: `website/docs/vulkan-kmp.md` single-sources the bindings README via
  mkdocs snippets; nav entry added. The Dokka API reference generates cleanly since the
  convention fix.

## Phase B — vk.xml growth engine (later, on demand signal)

Invert the generator: consume Khronos' `vk.xml` registry → emit Kotlin models, enums, JNI
C++, and the iOS cinterop surface. Sequenced only after Phase A ships and real users name
missing surface (compute is the likely first ask). Constraints learned from the current
generator to carry over: committed reviewable output (repo precedent), per-type opt-in list
first (generate what's requested, not all 800 structs at once), and the hand-authored
annotations become generator hints rather than the source of truth.

## Phase C (exploratory, unscheduled) — a wasmJs actual: Vulkan emulated over WebGPU

The bindings are already true KMP (`expect object Vulkan`/`VulkanDescriptors`, actuals for
desktop/Android/iOS); wasm is the deliberately absent target. Filling it means implementing
a Vulkan *driver* on WebGPU — the reverse of what wgpu/Dawn do — and the complexity is the
mismatch between Vulkan's explicit model and WebGPU's implicit one:

- **Synchronization/layout**: barriers, semaphores, fences, image layout transitions have no
  WebGPU counterpart (the browser tracks state itself). Emulation = a shadow state machine
  that validates and mostly no-ops — every `vkCmdPipelineBarrier` call a consumer writes is
  theater.
- **Memory**: `vkAllocateMemory`/`vkMapMemory` are synchronous; WebGPU mapping is
  `mapAsync`-only on a thread that must never block. Faithful sync semantics are
  *impossible*; the emulated API goes async-poisoned or copy-heavy.
- **Render passes**: subpasses with dependencies must be split into separate WebGPU passes —
  correctness cliff for any consumer using them as intended.
- **Descriptors → bind groups, push constants → emulated uniform ring, pipeline caches →
  fakes**; secondary command buffers and multi-queue unsupported outright.
- **Shaders**: `vkCreateShaderModule` takes SPIR-V; browsers take WGSL — needs SPIR-V→WGSL
  in-browser (naga compiled to wasm; ironically the exact inverse of
  `awake:asset:shader-compiler`).

Scale honestly: a *subset* emulator covering the core profile is months of solo work and
leaks forever; complete fidelity is a multi-year driver project (wgpu/Dawn team-scale, in
reverse). And architecturally it inverts the correct layering Awake itself already has: the
portable abstraction (`render:contract`) sits *above* Vulkan and WebGPU, where each API runs
native-shaped. A consumer wanting portable GPU code should abstract like the engine does —
not write Vulkan into an emulator.

Kept as a phase because the *idea* has one legitimate slice if demand appears: publishing
naga-as-wasm (SPIR-V→WGSL in the browser) so Vulkan-first consumers can reuse shaders on
web without the driver emulation. That slice is weeks, not months, and honest.

## Not in the library, ever

`render:contract`, `Renderer`, `Material`, pipelines/features, ASL, shader-pack — engine
land. The library's ceiling is Khronos' API surface, verbatim, in Kotlin.
