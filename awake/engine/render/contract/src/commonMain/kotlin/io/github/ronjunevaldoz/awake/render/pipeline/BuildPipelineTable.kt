// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.pipeline

import io.github.ronjunevaldoz.awake.render.renderer.CullMode

/**
 * Builds every pipeline in [requests] through [factory], fanning each request out into its own
 * companions.
 *
 * This is the single source of "which pipelines exist and what state each one has". Both
 * backends used to answer that themselves -- Vulkan in `buildRequestedPipelines`, WebGPU as ten
 * hand-written constructor calls -- which is why the two drifted: WebGPU had no transparent
 * companion at all for as long as Vulkan did.
 *
 * A failure is wrapped per request rather than once around the loop: a missing shader resource
 * otherwise surfaces as a bare file path with no hint which of the (up to seven) pipelines being
 * built was the one that failed.
 */
// Broad by design: a backend's pipeline creation can fail with anything from a missing shader
// resource to a native error, and the whole point of catching here is to say WHICH pipeline
// failed before rethrowing. Narrowing it would let the least informative failures through
// unlabelled.
@Suppress("TooGenericExceptionCaught")
suspend fun <P> buildPipelineTable(
    requests: List<PipelineRequest>,
    factory: PipelineFactory<P>,
): Map<PipelineKey, PipelineSet<P>> = buildMap {
    requests.forEach { request ->
        val spec = request.spec
        try {
            put(
                request.key,
                PipelineSet(
                    fill = factory.create(request.key, spec),
                    wireframe = if (request.buildWireframe) {
                        factory.create(request.key, spec.copy(wireframe = true))
                    } else {
                        null
                    },
                    backCulled = if (request.buildBackCulled) {
                        factory.create(request.key, spec.copy(cullMode = CullMode.Back))
                    } else {
                        null
                    },
                    // The variant is REPLACED, not merged: a transparent companion is defined by
                    // being alpha-blended and non-depth-writing, whatever its fill pipeline was.
                    // Only ever requested for non-instanced draws (see PipelineRequest), so
                    // discarding the fill's variant can't drop an instancing binding here.
                    transparent = if (request.buildTransparent) {
                        factory.create(request.key, spec.copy(variant = PipelineVariant.AlphaBlended))
                    } else {
                        null
                    },
                ),
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to build pipeline '${request.key}': ${e.message}", e)
        }
    }
}
