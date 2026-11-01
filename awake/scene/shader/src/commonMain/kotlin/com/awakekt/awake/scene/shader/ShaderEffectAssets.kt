/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.CompiledShaderDocument
import com.awakekt.awake.asset.shaderdocument.ShaderDocumentException
import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.CancellationException

/**
 * The compiled shader documents and decoded images that a scene's `shader_effect`s use, each by its
 * project path, as [loadShaderEffects] reads them. [ShaderEffectSystem] draws from it.
 *
 * Give the system a new one to show edited files, as an editor's live preview does: an effect whose
 * document or images are different objects in the new one is attached again, and keeps drawing the
 * old way until the new one is ready.
 *
 * @property documents Project path to the document compiled from it. A document that failed to load
 * or compile is absent, and its effects are not drawn.
 * @property textures Project path to the image decoded from it as CPU pixels, as a host may hold them.
 * An image that failed is absent.
 * @property failed The documents that were read and could not be loaded or compiled, by project path,
 * as [loadShaderEffects] records them. A path in neither [documents] nor here has not been loaded
 * yet: a host that loads documents as they are opened, such as an editor, may still give the system
 * one, so [ShaderEffectSystem] reports its effects as not drawn yet rather than as failed.
 * @property images Project path to the image decoded for the GPU, as [loadShaderEffects] reads them: a
 * browser's WebGPU copies it without reading it back (awakekt/awake#1184). An effect takes a path from
 * here first, then from [textures].
 */
class ShaderEffectAssets(
    val documents: Map<String, CompiledShaderDocument> = emptyMap(),
    val textures: Map<String, TextureAsset> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val images: Map<String, GpuImageSource>,
) {
    /** Assets whose images are all CPU pixels, as before [images]. */
    constructor(
        documents: Map<String, CompiledShaderDocument> = emptyMap(),
        textures: Map<String, TextureAsset> = emptyMap(),
        failed: Set<String> = emptySet(),
    ) : this(documents, textures, failed, emptyMap())

    /** No assets: what [Empty] holds. Kept as its own constructor, as the JVM had it before [images]. */
    constructor() : this(emptyMap(), emptyMap(), emptySet(), emptyMap())

    /** The CPU images [effect] names, by the texture name its document declares: those in [textures] and not [images]. */
    fun texturesOf(effect: SceneShaderEffect): Map<String, TextureAsset> = buildMap {
        effect.textures.forEach { (name, path) -> if (path !in images) textures[path]?.let { put(name, it) } }
    }

    /** The decoded [images] [effect] names, by the texture name its document declares. */
    fun imagesOf(effect: SceneShaderEffect): Map<String, GpuImageSource> = buildMap {
        effect.textures.forEach { (name, path) -> images[path]?.let { put(name, it) } }
    }

    /** Whether the image at [path] loaded, as a texture or an image. */
    fun hasImage(path: String): Boolean = path in images || path in textures

    /**
     * These assets with [other]'s over them: each document and image [other] has in place of this
     * one's, as an editor lays the documents it is editing over those read for a scene. A document
     * either failed is [failed] only while neither has it.
     */
    operator fun plus(other: ShaderEffectAssets): ShaderEffectAssets {
        val merged = documents + other.documents
        return ShaderEffectAssets(merged, textures + other.textures, (failed + other.failed) - merged.keys, images + other.images)
    }

    /** The assets of a scene that has no effects. */
    companion object {
        /** Nothing loaded: for a scene with no `shader_effect`. */
        val Empty: ShaderEffectAssets = ShaderEffectAssets()
    }
}

/**
 * Reads, checks and compiles every shader document [document]'s `shader_effect`s name, each once, and
 * decodes every image they give a texture. Then it checks each effect against its document, so a
 * mismatch is reported when the project loads rather than when the effect first draws.
 *
 * It never throws for the project's content. A document that cannot be read, decoded or compiled, an
 * image that cannot be read or decoded, and an effect that does not match its document are each logged,
 * naming the node, the document and the parameter or texture; only the effects concerned are not drawn,
 * and the scene plays on without them.
 */
// TooGenericExceptionCaught: a file that fails to read or decode for any reason only loses its effects.
@Suppress("TooGenericExceptionCaught")
suspend fun loadShaderEffects(document: SceneDocument, assets: AssetSource): ShaderEffectAssets {
    val effects = document.nodes.flatMap { it.shaderEffects("") }
    if (effects.isEmpty()) return ShaderEffectAssets.Empty
    val failed = HashSet<String>()
    val documents = buildMap {
        for (path in effects.map { it.effect.shader }.distinct()) {
            try {
                put(path, ShaderDocuments.compile(assets.read(AssetPath(path)).getOrThrow().decodeToString()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: ShaderDocumentException) {
                failed += path
                log.error { "Shader document '$path' was rejected; its effects are not drawn.\n${rejected.message}" }
            } catch (failure: Exception) {
                failed += path
                log.error { "Shader document '$path' could not be read: ${failure.message}; its effects are not drawn." }
            }
        }
    }
    // decodeImage, not createBitmap: nothing reads these pixels, so a browser keeps them for the GPU.
    val images = buildMap {
        for (path in effects.flatMap { it.effect.textures.values }.distinct()) {
            try {
                put(path, decodeImage(assets.read(AssetPath(path)).getOrThrow()).asGpuImageSource())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error { "Shader effect image '$path' could not load: ${failure.message}" }
            }
        }
    }
    val loaded = ShaderEffectAssets(documents, emptyMap(), failed, images)
    for ((node, effect) in effects) {
        val compiled = documents[effect.shader] ?: continue
        shaderEffectProblems(effect, compiled, loaded).forEach { problem ->
            log.error { "Node '$node': shader_effect '${effect.shader}': $problem. The effect is not drawn." }
        }
    }
    return loaded
}

/**
 * What stops [effect] drawing [compiled]: each parameter it gives that the document does not declare or
 * that has the wrong count of numbers, each texture the document declares that it gives no loaded image
 * for, and each texture it gives that the document does not declare. Empty when it can draw.
 */
fun shaderEffectProblems(
    effect: SceneShaderEffect,
    compiled: CompiledShaderDocument,
    assets: ShaderEffectAssets,
): List<String> = buildList {
    compiled.packParameters(effect.parameters, compiled.newInputs()).forEach { add(it.message) }
    val declared = compiled.document.textures.map { it.name }
    declared.forEach { name ->
        val path = effect.textures[name]
        when {
            path == null -> add("texture '$name' is declared by the document but given no image")
            !assets.hasImage(path) -> add("texture '$name' names '$path', which did not load")
        }
    }
    (effect.textures.keys - declared.toSet()).forEach { add("texture '$it' is not declared by the document") }
}

private data class NamedEffect(val node: String, val effect: SceneShaderEffect)

private fun SceneNode.shaderEffects(parent: String): List<NamedEffect> {
    val path = if (parent.isEmpty()) name ?: "(unnamed)" else "$parent/${name ?: "(unnamed)"}"
    return components.filterIsInstance<SceneShaderEffect>().map { NamedEffect(path, it) } +
        children.flatMap { it.shaderEffects(path) }
}

private val log = Logger("scene-shader")
