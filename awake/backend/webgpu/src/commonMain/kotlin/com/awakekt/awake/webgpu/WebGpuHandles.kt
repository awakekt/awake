/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

/**
 * The `expect` renderer seam (GraphicsDevice/Mesh/RenderPipeline/etc.) types every handle as
 * `Long`, mirroring Vulkan's raw-integer-handle model -- but wgpu4k hands back real typed Kotlin
 * objects (GPUDevice, GPUBuffer, GPURenderPipeline, ...), not integers. This table assigns each
 * such object an incrementing Long id so the wasmJs actuals can satisfy the existing Long-typed
 * contract without changing it (a contract change would ripple back into commonMain and force
 * re-verification across the 4 already-proven platforms, for zero benefit to them).
 * wasmJsMain-internal only -- invisible to commonMain and every other platform.
 */
object WebGpuHandles {
    private var nextHandle = 1L
    private val objects = HashMap<Long, Any>()

    /**
     * Stores [value] in the table and returns the id that resolves back to it.
     *
     * @param value The wgpu object to keep alive; kept until [release] is called for the returned
     * id.
     * @return A new, never-reused id, starting at 1.
     */
    fun register(value: Any): Long {
        val handle = nextHandle
        nextHandle += 1
        objects[handle] = value
        return handle
    }

    // `as? T` fails fast with a clear error on a caller/handle-type mismatch rather than
    // corrupting state -- correctness relies on every caller resolving with the same T it
    // registered, same as the untyped table itself.
    /**
     * Looks up the object registered under [handle].
     *
     * @param T The type the object was registered as.
     * @param handle An id returned by [register] and not yet released.
     * @return The object, cast to [T] without a check (generics are erased), so asking for the
     * wrong type fails later, at the point of use.
     * @throws IllegalStateException If no object is registered under [handle].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(handle: Long): T =
        objects[handle] as? T ?: error("No WebGPU object registered for handle $handle")

    /**
     * Removes the object registered under [handle] so it can be garbage collected. Releasing an
     * unknown or already-released id does nothing.
     *
     * @param handle An id returned by [register].
     */
    fun release(handle: Long) {
        objects.remove(handle)
    }
}
