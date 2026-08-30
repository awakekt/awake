# jni-binding-generator (vendored)

Vendored copy of [jni-binding-generator](https://github.com/ronjunevaldoz/jni-binding-generator)
at upstream revision **5638af0**, used to generate JNI marshalling C++ from `external fun` declarations in
`awake-vulkan`. See [docs/decisions/D10-codegen-derisk-findings.md](../../docs/decisions/D10-codegen-derisk-findings.md)
for why this tool was chosen over the legacy `awake-vulkan-generator`, and for the full
history of gaps found and fixed while wiring it into this project (v1.6.8 → v1.6.10).

Licensed Apache 2.0 (same as Awake) — `LICENSE` in this directory is the upstream tool's
license, not a modification to Awake's own license.

## Updating

The generator has no PyPI package; its own integration docs assume the `scripts/` directory
is copied wholesale into the consuming project. To pick up a newer version:

```bash
git clone https://github.com/ronjunevaldoz/jni-binding-generator /tmp/jni-binding-generator
git -C /tmp/jni-binding-generator checkout 5638af0
cp /tmp/jni-binding-generator/scripts/{__init__.py,_*.py,jni-binding-generator.py,jni-utils.h} \
   tools/jni-binding-generator/scripts/
```

Then re-run `./gradlew generateJniBindings` and diff the output before committing — a version
bump can change generated code shape (see that repo's CHANGELOG.md for what changed).

## Usage

See the `generateJniBindings` Gradle task in
[awake-vulkan/android-native/build.gradle.kts](../../awake-vulkan/android-native/build.gradle.kts).

## Native implementations

An external declaration may opt into a stable native implementation with `@JniNative`:

```kotlin
@JniNative("awake_vulkan_images_transition_image_layout")
actual external fun vkTransitionImageLayout(commandBuffer: Long, image: Long, oldLayout: Int, newLayout: Int, levelCount: Int)
```

The generated JNI entry point keeps the signature, marshalling, and argument checks, then
delegates to the named `extern "C"` function. The implementation belongs in a normal native
source file that is compiled alongside the generated file. Unannotated functions retain the
existing TODO-body workflow until their native implementations are migrated.

The legacy `// jni-native: symbol` form remains accepted temporarily so existing bindings can
be migrated without a flag day.

The ownership and migration contract is recorded in
[D11: JNI Native Implementation Boundary](../../docs/decisions/D11-jni-native-implementation-boundary.md).
