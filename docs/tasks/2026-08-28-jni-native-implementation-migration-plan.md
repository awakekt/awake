# JNI Native Implementation Migration Plan

**Status:** Open
**Owner:** Awake Vulkan bindings
**Related decision:** [D11: JNI Native Implementation Boundary](../decisions/D11-jni-native-implementation-boundary.md)
**Upstream generator:** `jni-binding-generator` revision `5638af0`

## Goal

Move backend behavior out of generated JNI files without changing the Kotlin API or
native behavior. Generated files should contain only JNI entry points, marshalling,
validation, and delegation. Vulkan and GLFW behavior should live in ordinary native
source files.

## Current State

The bindings are in a hybrid state:

- The generator produces JNI signatures, marshalling helpers, and TODO bodies for
  unannotated functions.
- Vulkan and GLFW implementations were subsequently hand-authored inside generated
  files.
- `vkTransitionImageLayout` is already migrated through `@JniNative` and
  `VulkanImages_native.cpp`.
- The vendored generator README is pinned to upstream `5638af0`.
- A real generator run currently reports drift in seven output paths and would replace
  legacy bodies with TODO stubs. Dry runs are safe; regeneration is deferred.

## Boundary To Confirm

Before migrating struct-heavy functions, settle the delegated implementation signature:

1. **Raw JNI boundary:** generated code delegates `JNIEnv*` and JNI values; native code
   performs object/array extraction. This keeps generated struct types private but
   duplicates some conversion logic.
2. **Marshalled native boundary:** generated code delegates resolved C++ values; shared
   generated declarations must make those values available to `*_native.cpp` without
   including a generated implementation file.

Choose one contract and add a focused generator test before migrating the next batch.
The existing simple scalar/handle delegation path is not sufficient evidence for the
struct and array cases.

## Migration Order

### Phase 1: Images

- Keep the existing `vkTransitionImageLayout` migration as the reference implementation.
- Migrate the remaining image and sampler functions one function at a time.
- Preserve Vulkan layout, handle, offset, and error semantics exactly.
- Compile Android and Desktop native targets after each logical batch.

### Phase 2: Buffers

- Migrate allocation, mapping, upload, readback, and command functions.
- Treat byte offsets as values, not nullable handles.
- Verify array length and local-reference cleanup behavior.

### Phase 3: Descriptors

- Migrate descriptor layout/pool creation and update functions.
- Verify nested struct and array marshalling against the generated type helpers.

### Phase 4: GLFW Windowing

- Move GLFW calls and scroll callback state into a Desktop native source file.
- Keep GLFW out of generated JNI files and preserve Desktop-only CMake wiring.
- Verify callback registration, event polling, cursor behavior, and surface creation.

## Verification Gates

Each phase must pass all applicable gates before the next phase starts:

```bash
./gradlew :awake:backend:vulkan:bindings:android-native:generateJniBindings
./gradlew :awake:backend:vulkan:bindings:android-native:checkJniBindings
./gradlew :awake:backend:vulkan:bindings:desktop-native:build
```

Also inspect the generated diff to ensure it contains no backend calls and run the
repository's Vulkan layering and native compilation checks. A dry run is the safe
inspection command while legacy bodies remain.

## Completion Criteria

- No generated JNI file contains Vulkan or GLFW API calls.
- Every delegated function has a directly preceding `@JniNative` annotation.
- Every delegated symbol has one platform-appropriate native implementation and CMake
  source registration.
- `checkJniBindings` passes without ignoring generated files.
- Android and Desktop native builds pass.
- The generator contract and migration examples are covered by tests and documented in
  D11 and the vendored generator README.

## Non-Goals

- Do not change the public Kotlin binding API.
- Do not rewrite Vulkan behavior while relocating it.
- Do not make the shared/common `UiRenderPipeline` or unrelated renderer changes part of
  this migration.
- Do not commit regenerated TODO bodies merely to make the drift check appear clean.
