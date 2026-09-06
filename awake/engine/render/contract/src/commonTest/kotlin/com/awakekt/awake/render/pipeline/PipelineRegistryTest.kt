/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the registry's two load-bearing properties: it compiles a given pipeline once, and it
 * never compiles on the frame path.
 *
 * Both fail silently if broken. Compiling twice wastes GPU memory and startup time without any
 * visible symptom; compiling lazily at draw time produces a frame hitch that looks like
 * unrelated jank.
 */
class PipelineRegistryTest {

    /** Records what the registry actually asked it to compile. */
    private class CountingFactory : PipelineFactory<String> {
        val built = mutableListOf<PipelineSpec>()
        override suspend fun create(key: PipelineKey, spec: PipelineSpec): String {
            built += spec
            return "$key:${spec.vertexFormat}:${spec.variant}"
        }
    }

    private fun spec(
        format: VertexFormat = VertexFormat.PositionColorUv,
        vertexPath: String = "shaders/lit.vert.spv",
        variant: PipelineVariant = PipelineVariant.Opaque,
    ) = PipelineSpec(
        vertexFormat = format,
        vertexShader = ShaderSource.ResourcePath(vertexPath, "vertexMain"),
        fragmentShader = ShaderSource.ResourcePath("shaders/lit.frag.spv", "fragmentMain"),
        variant = variant,
    )

    private fun request(spec: PipelineSpec, key: PipelineKey = PipelineKey.Primary) =
        PipelineRequest(key = key, spec = spec)

    @Test
    fun theSameSpecRegisteredTwiceCompilesOnce() = runTest {
        val factory = CountingFactory()
        val registry = PipelineRegistry(factory)

        registry.register(listOf(request(spec())))
        registry.register(listOf(request(spec())))

        assertEquals(
            1,
            factory.built.size,
            "PipelineSpec is a data class over shader PATHS, so two structurally identical " +
                "specs must be one key. A key holding ShaderStages would fail here -- that type " +
                "is a plain class with identity equality.",
        )
    }

    /** The collision that ruled out keying on variant alone: instanced and skinned-instanced
     * share a variant but need different pipelines. */
    @Test
    fun specsDifferingOnlyInVertexFormatCompileSeparately() = runTest {
        val factory = CountingFactory()
        val registry = PipelineRegistry(factory)

        registry.register(
            listOf(
                request(spec(format = VertexFormat.PositionNormalColor, variant = PipelineVariant.Instanced)),
                request(
                    spec(format = VertexFormat.PositionNormalColorSkin, variant = PipelineVariant.Instanced),
                    key = PipelineKey.SkinnedInstanced,
                ),
            ),
        )

        assertEquals(2, factory.built.size)
    }

    @Test
    fun specsDifferingOnlyInShaderPathCompileSeparately() = runTest {
        val factory = CountingFactory()
        val registry = PipelineRegistry(factory)

        registry.register(listOf(request(spec(vertexPath = "shaders/a.vert.spv"))))
        registry.register(listOf(request(spec(vertexPath = "shaders/b.vert.spv"))))

        assertEquals(2, factory.built.size)
    }

    /** The frame-path rule. A miss must stay a miss -- compiling here is the hitch the whole
     * design exists to prevent. */
    @Test
    fun getNeverCompilesAnUnregisteredSpec() = runTest {
        val factory = CountingFactory()
        val registry = PipelineRegistry(factory)

        assertNull(registry[spec()])
        assertTrue(factory.built.isEmpty(), "get must never reach the factory")
    }

    @Test
    fun aRegisteredSpecIsRetrievableAndCompanionsAreRegisteredToo() = runTest {
        val registry = PipelineRegistry(CountingFactory())
        val fill = spec()

        registry.register(
            listOf(PipelineRequest(key = PipelineKey.Primary, spec = fill, buildTransparent = true)),
        )

        assertTrue(registry[fill] != null, "the fill spec should be registered")
        assertTrue(
            registry.specs.any { it.variant == PipelineVariant.AlphaBlended },
            "a request's companions are compiled too, not just its fill pipeline",
        )
    }

    @Test
    fun destroyAllHandsBackEveryCompiledPipelineAndEmpties() = runTest {
        val registry = PipelineRegistry(CountingFactory())
        registry.register(
            listOf(request(spec()), request(spec(format = VertexFormat.PositionUv), PipelineKey.Particle)),
        )
        val destroyed = mutableListOf<String>()

        registry.destroyAll { destroyed += it }

        assertEquals(2, destroyed.size)
        assertTrue(registry.specs.isEmpty())
    }
}
