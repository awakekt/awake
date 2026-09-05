# D11: JNI Native Implementation Boundary

## Decision

`jni-binding-generator` owns the JNI boundary only: exported JNI names, parameter
marshalling, null/error checks, and delegation. Backend behavior belongs in ordinary
native source files compiled beside the generated output.

An `actual external fun` opts into the boundary with a directly preceding `@JniNative`
annotation:

```kotlin
@JniNative("awake_vulkan_images_transition_image_layout")
actual external fun vkTransitionImageLayout(
    commandBuffer: Long,
    image: Long,
    oldLayout: Int,
    newLayout: Int,
    levelCount: Int,
)
```

The generator emits an `extern "C"` declaration and a JNI wrapper that delegates to that
symbol. The implementation must be supplied by a normal `*_native.cpp` source listed in
every platform CMake target that consumes the generated wrapper.

## Migration Rule

Unannotated functions may retain a temporary generated TODO body while they are migrated.
The migration unit is one function at a time: add the annotation, move the backend body to
`*_native.cpp`, compile every native target, and then remove the old generated body. The
generator must never infer backend behavior from Kotlin names or emit Vulkan API calls.

## Attribution

The generator and this boundary implementation are authored by **Ron June Valdoz** and
remain Apache-2.0 source in this repository. **AwakeLab** identifies the project and
publishing organization; it is not substituted for individual copyright ownership unless
the project establishes that legal ownership explicitly.

`VkPointer` is intentionally retained: it describes a single native pointer and is not
interchangeable with `VkArray`, which describes a counted array. Its former deprecation notice
was removed after the historical usages were verified against the legacy generator.
