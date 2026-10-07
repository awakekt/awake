/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.CompiledShaderDocument
import com.awakekt.awake.asset.shaderdocument.ShaderEffectInputs
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Draws every [ShaderEffectSource] entity by attaching its document's content feature to [host], and
 * keeps it running: each frame it advances the effect's clock, copies the entity's place into the
 * document's model matrix, and shows or hides it by [SceneShaderEffect.enabled].
 *
 * - **Changes.** A replaced component whose settings differ only in parameters or `enabled` updates
 *   the running effect in place. One with another document or other textures is attached again, and
 *   so is every effect whose document or images are new objects in a new [assets].
 * - **Failures.** An attach that fails keeps the effect that was drawing, if there was one, as the sky
 *   does. An effect that does not match its document ([shaderEffectProblems]) is logged and hidden,
 *   and one whose document did not load is never drawn. None of these stops the frame.
 * - **The clock.** A frame context has no clock to read, so each effect keeps its own: the seconds
 *   since this system first saw it, carried across a re-attach.
 *
 * Attaches start from [update] on the frame thread and may resume anywhere; results are applied on the
 * next [update]. [close] it when the scene is disposed: systems have no dispose hook of their own.
 *
 * @param host What content features attach to. Null, as with a renderer that draws nothing, runs the
 * system without drawing.
 * @param assets The scene's documents and images, from [loadShaderEffects].
 */
class ShaderEffectSystem(
    private val host: ContentFeatureHost?,
    assets: ShaderEffectAssets,
) : System, AutoCloseable {
    /** The documents and images effects draw from. Setting a new one re-attaches what changed in it. */
    var assets: ShaderEffectAssets = assets
        set(value) {
            if (value !== field) assetsChanged = true
            field = value
        }

    private val log = Logger("scene-shader")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var assetsChanged = false
    private var warnedNoHost = false

    /** A list, not a map: a scene holds a handful of effects, and its inline searches allocate nothing. */
    private val tracked = ArrayList<Tracked>()
    private val attached = Channel<Attached>(Channel.UNLIMITED)
    private val model = Mat4()

    /** A field rather than a lambda in [update], so querying allocates nothing per frame. */
    private val trackNew: (Entity, ShaderEffectSource) -> Unit = { entity, source ->
        if (tracked.none { it.entity == entity }) startAttach(Tracked(entity, source).also { tracked += it })
    }

    override fun update(world: World, delta: Float) {
        if (host == null) {
            // Checked once, so a scene with no effects pays for the query only on its first frame.
            if (!warnedNoHost) {
                warnedNoHost = true
                if (world.query(ShaderEffectSource::class).isNotEmpty()) {
                    log.warn { "The renderer takes no content features, so shader_effect components are not drawn." }
                }
            }
            return
        }
        refreshTracked(world)
        if (assetsChanged) {
            assetsChanged = false
            reattachChangedAssets()
        }
        applyAttached()
        world.queryEach(ShaderEffectSource::class, trackNew)
        for (index in tracked.indices) advance(world, tracked[index], delta)
    }

    /** Detaches every effect this system attached and cancels what is still attaching. */
    override fun close() {
        tracked.forEach(Tracked::release)
        tracked.clear()
        while (true) {
            val pending = attached.tryReceive().getOrNull() ?: break
            pending.shown.feature.detach()
        }
        scope.cancel()
    }

    /**
     * Drops the effects whose entity or component is gone, and takes in replaced components: another
     * document or other textures attach again, other parameters are written in place.
     */
    private fun refreshTracked(world: World) {
        for (index in tracked.indices.reversed()) {
            val entry = tracked[index]
            val current = if (world.isAlive(entry.entity)) world.get<ShaderEffectSource>(entry.entity) else null
            if (current == null) {
                entry.release()
                tracked.removeAt(index)
            } else if (current !== entry.source) {
                takeIn(entry, current)
            }
        }
    }

    private fun takeIn(entry: Tracked, current: ShaderEffectSource) {
        val before = entry.source.settings
        entry.source = current
        val after = current.settings
        when {
            after.shader != before.shader || after.textures != before.textures -> startAttach(entry)
            after.parameters != before.parameters -> entry.shown?.let { repack(entry, it) }
        }
    }

    private fun reattachChangedAssets() {
        for (index in tracked.indices) {
            val entry = tracked[index]
            val settings = entry.source.settings
            val shown = entry.shown
            if (shown == null || assets.documents[settings.shader] !== shown.compiled || assets.texturesOf(settings) != shown.textures) {
                startAttach(entry)
            }
        }
    }

    /** A result for an effect that has since gone, or been attached again since, is freed straight away. */
    private fun applyAttached() {
        while (true) {
            val result = attached.tryReceive().getOrNull() ?: return
            val entry = result.entry.takeIf { it.generation == result.generation && tracked.any { tracked -> tracked === it } }
            if (entry == null) {
                result.shown.feature.detach()
            } else {
                entry.shown?.feature?.detach()
                entry.shown = result.shown
                repack(entry, result.shown)
            }
        }
    }

    // TooGenericExceptionCaught: whatever stops one effect attaching must not stop the frame.
    @Suppress("TooGenericExceptionCaught")
    private fun startAttach(entry: Tracked) {
        val attachTo = checkNotNull(host)
        entry.generation++
        entry.job?.cancel()
        val compiled = drawableDocument(entry) ?: return
        val settings = entry.source.settings
        val textures = assets.texturesOf(settings)
        val inputs = compiled.newInputs()
        val generation = entry.generation
        entry.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val source = compiled.contentFeature(inputs, textures, name = "shader-effect '${settings.shader}'")
                val feature = attachTo.attachContentFeature(source)
                attached.trySend(Attached(entry, generation, Shown(compiled, textures, inputs, feature)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error(failure) {
                    "shader_effect on entity ${entry.entity}, '${settings.shader}', could not be attached" +
                        if (entry.shown != null) "; it keeps drawing as it was." else " and is not drawn."
                }
            }
        }
    }

    /** The document [entry] draws, or null, logged, when it did not load or the effect does not match it. */
    private fun drawableDocument(entry: Tracked): CompiledShaderDocument? {
        val settings = entry.source.settings
        val compiled = assets.documents[settings.shader]
        val problems = compiled?.let { shaderEffectProblems(settings, it, assets) } ?: listOf("the document did not load")
        if (problems.isNotEmpty()) {
            log.error {
                "shader_effect on entity ${entry.entity}, '${settings.shader}': ${problems.joinToString("; ")}" +
                    if (entry.shown != null) ". It keeps drawing as it was." else ". Not drawn."
            }
        }
        return compiled.takeIf { problems.isEmpty() }
    }

    /** Writes the current parameters; a set that does not match the document hides the effect. */
    private fun repack(entry: Tracked, shown: Shown) {
        val issues = shown.compiled.packParameters(entry.source.settings.parameters, shown.inputs)
        entry.blocked = issues.isNotEmpty()
        if (entry.blocked) {
            log.error {
                "shader_effect on entity ${entry.entity}, '${entry.source.settings.shader}': " +
                    "${issues.joinToString("; ") { it.message }}. Hidden until its parameters match."
            }
        }
    }

    private fun advance(world: World, entry: Tracked, delta: Float) {
        entry.timeSeconds += delta
        val inputs = entry.shown?.inputs ?: return
        inputs.timeSeconds = entry.timeSeconds
        inputs.deltaSeconds = delta
        inputs.visible = entry.source.settings.enabled && !entry.blocked
        val transform = world.get<Transform>(entry.entity) ?: return
        // The transform pass runs after frame systems, so a root node's world matrix is last frame's;
        // its local matrix is current, and is its world matrix. A child's is as of the last pass.
        inputs.setModel(if (transform.parent == null) transform.computeLocalMatrix(model) else transform.worldMatrix)
    }

    private class Tracked(val entity: Entity, var source: ShaderEffectSource) {
        var job: Job? = null
        var generation = 0
        var shown: Shown? = null
        var blocked = false
        var timeSeconds = 0f

        fun release() {
            job?.cancel()
            shown?.feature?.detach()
            shown = null
        }
    }

    private class Shown(
        val compiled: CompiledShaderDocument,
        val textures: Map<String, TextureAsset>,
        val inputs: ShaderEffectInputs,
        val feature: AttachedContentFeature,
    )

    private class Attached(val entry: Tracked, val generation: Int, val shown: Shown)
}
