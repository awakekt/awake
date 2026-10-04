/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

/**
 * A [RuntimeShaderCompiler] that compiles a given WGSL text once and hands the same SPIR-V back for
 * every later request for that text, so a vertex and a fragment stage cut from one source, or a
 * hot-reload preview that re-submits an unchanged file, skip the compiler the second time.
 *
 * The cache is keyed by the WGSL text itself, so two different sources can never share an entry.
 * It keeps the [maxEntries] most recently used texts: a live editor produces a new text on every
 * keystroke, and an unbounded cache would hold them all.
 *
 * Only a successful compile is kept. A text [delegate] rejects throws [NagaException] on every
 * request, as it would without the cache, so fixing the source is never masked by a stale failure.
 * [validate] always asks [delegate]: it is cheap next to a compile and callers want a fresh verdict.
 *
 * The returned arrays are shared between callers and must not be modified. This is not thread-safe;
 * use it from the one thread that resolves shaders, as `VulkanShaderResolver` does.
 */
class CachingShaderCompiler(
    private val delegate: RuntimeShaderCompiler,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : RuntimeShaderCompiler {
    init {
        require(maxEntries > 0) { "maxEntries must be positive, got $maxEntries" }
    }

    /** Oldest use first: [LinkedHashMap] keeps insertion order and a hit re-inserts its entry. */
    private val compiled = LinkedHashMap<String, ByteArray>()

    /** How many texts are cached right now. */
    val size: Int get() = compiled.size

    override fun wgslToSpirv(wgsl: String): ByteArray {
        compiled.remove(wgsl)?.let { hit ->
            compiled[wgsl] = hit
            return hit
        }
        val spirv = delegate.wgslToSpirv(wgsl)
        compiled[wgsl] = spirv
        if (compiled.size > maxEntries) compiled.remove(compiled.keys.first())
        return spirv
    }

    override fun validate(wgsl: String): String? = delegate.validate(wgsl)

    private companion object {
        /** Enough for every shader of a scene plus the previews of a few edits in flight. */
        const val DEFAULT_MAX_ENTRIES = 128
    }
}
