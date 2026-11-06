/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.damp
import kotlin.math.abs

/**
 * How things standing between a follow camera and its target fade out of the way: down to
 * [opacity] while they block the view, and back to opaque once they stop. [OccluderFader] applies it.
 *
 * The defaults are neutral starting points, not a game's feel; a game authors its own values.
 *
 * @property opacity Opacity an occluder fades to, from `0` (gone) to `1` (no fade).
 * @property fadeSeconds Half-life of the fade, both ways, in seconds: an occluder covers half of what
 *   remains of the way to its target opacity every [fadeSeconds]. Zero or less fades at once.
 */
data class OccluderFade(
    val opacity: Float = DEFAULT_OPACITY,
    val fadeSeconds: Float = DEFAULT_FADE_SECONDS,
) {
    init {
        require(opacity in 0f..1f) { "opacity must be within 0..1: $opacity" }
    }

    /** Neutral defaults for [OccluderFade]. */
    companion object {
        /** Default [opacity]. */
        const val DEFAULT_OPACITY = 0.35f

        /** Default [fadeSeconds]. */
        const val DEFAULT_FADE_SECONDS = 0.2f
    }
}

/**
 * Each occluder's current opacity, eased toward [OccluderFade.opacity] while it blocks the view and
 * back to `1` once it doesn't. [K] names an occluder however the caller does, such as an entity.
 *
 * Each frame the caller reports the occluders it found with [blocking], then calls [step], and reads
 * the result with [opacityOf] or [forEach]. An occluder that has eased back to opaque is forgotten,
 * and [onReleased] runs for it, so the caller can clear whatever it drew the fade with.
 */
class OccluderFader<K : Any>(private val onReleased: (K) -> Unit = {}) {
    private val opacities = LinkedHashMap<K, Float>()
    private val blockingNow = HashSet<K>()
    private val released = ArrayList<K>()

    /** How many occluders are faded, partly or fully. */
    val size: Int get() = opacities.size

    /** Reports [occluder] as blocking the view this frame. */
    fun blocking(occluder: K) {
        blockingNow += occluder
        if (occluder !in opacities) opacities[occluder] = 1f
    }

    /**
     * Eases every occluder over [delta] seconds: toward [settings]' opacity if it was reported since
     * the last step, toward `1` otherwise. Forgets, and releases, any that reached `1` unreported.
     */
    fun step(settings: OccluderFade, delta: Float) {
        for (entry in opacities.entries) {
            val target = if (entry.key in blockingNow) settings.opacity else 1f
            val eased = damp(entry.value, target, settings.fadeSeconds, delta)
            entry.setValue(if (abs(eased - target) < SETTLED) target else eased)
            if (entry.value >= 1f && entry.key !in blockingNow) released += entry.key
        }
        for (index in released.indices) {
            opacities.remove(released[index])
            onReleased(released[index])
        }
        released.clear()
        blockingNow.clear()
    }

    /** The current opacity of [occluder]: `1` for one that is not faded. */
    fun opacityOf(occluder: K): Float = opacities[occluder] ?: 1f

    /** Visits every faded occluder with its current opacity. */
    inline fun forEach(action: (occluder: K, opacity: Float) -> Unit) {
        for (entry in entries()) action(entry.key, entry.value)
    }

    /** Releases every occluder at once, as when fading is turned off. */
    fun clear() {
        val all = opacities.keys.toList()
        opacities.clear()
        blockingNow.clear()
        all.forEach(onReleased)
    }

    @PublishedApi
    internal fun entries(): Set<Map.Entry<K, Float>> = opacities.entries

    private companion object {
        // Closer than this to its target, an opacity is the target: a fade never lingers a hair off 1.
        const val SETTLED = 1e-3f
    }
}
