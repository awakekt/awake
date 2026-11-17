/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawWithContent
import com.awakekt.awake.compose.ui.layout.layout
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

// Followers are planned while placing, not while composing (#1512): a node that moves moves its
// follower in placement alone, so a host that skips unchanged composition and measure places and
// draws it without composing or measuring anything. Composition runs again, in the same frame, only
// for what it holds: which followers are drawn, and each one's size, scale, density and pin, by
// [FollowerPlans.composeRevision].

/**
 * Plans every follower, then places the content: put on the box the followers are drawn in, so the
 * plans are current before any follower reads its own while it is placed. [plan] plans once a frame.
 */
internal fun Modifier.planningFollowers(tree: CanvasTree, plan: CanvasTree.() -> Unit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        tree.plan()
        val followers = tree.followers
        if (followers.composedChanged()) followers.composeRevision.value++
        placeable.placeRelativeAt(0, 0)
    }
}

/**
 * Plans every follower for [frame] on a canvas [width] by [height] dp at [density], thinned by
 * [limits], and steps the emitters of followers not drawn by [particleClock]; again in the same
 * frame only for another canvas size, which a layout that measures its content twice gives, so a
 * frame that places, then composes, then places again plans and projects each follower once.
 */
internal fun CanvasTree.planFollowersOnce(
    frame: Long,
    width: Float,
    height: Float,
    density: Float,
    limits: List<CanvasFollowLimit>,
    particleClock: CanvasParticlesClock,
) {
    val key = followers.plannedFor
    if (key.matches(frame, width, height, density, limits)) return
    key.set(frame, width, height, density, limits)
    planFollowers(width, height, density, limits)
    stepUncomposedVisibleFollowers(this, particleClock)
}

/** What the followers were last planned for: a frame and a canvas. */
internal class FollowerPlanKey {
    private var frame = -1L
    private var width = Float.NaN
    private var height = Float.NaN
    private var density = Float.NaN
    private var limits: List<CanvasFollowLimit>? = null

    fun matches(frame: Long, width: Float, height: Float, density: Float, limits: List<CanvasFollowLimit>): Boolean =
        frame == this.frame && width == this.width && height == this.height && density == this.density && (limits === this.limits || limits == this.limits)

    fun set(frame: Long, width: Float, height: Float, density: Float, limits: List<CanvasFollowLimit>) {
        this.frame = frame
        this.width = width
        this.height = height
        this.density = density
        this.limits = limits
    }
}

/**
 * Places a follower where its [plan] puts it this frame, on whole pixels with the remainder drawn
 * as a translation, as `Modifier.offset` does, and draws it faded as the plan says.
 */
internal fun Modifier.followerPlaced(plan: FollowerPlan): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val x = plan.x * density
        val y = plan.y * density
        val placedX = x.roundToInt()
        val placedY = y.roundToInt()
        plan.look.update(plan.alpha, x - placedX, y - placedY)
        placeable.placeRelativeAt(placedX, placedY)
    }
}.drawWithContent {
    val look = plan.look
    // Read so a retained draw repaints this follower when its fade or remainder changes (#1512).
    look.value
    val dx = if (layoutDirection == LayoutDirection.Rtl) -look.residualX else look.residualX
    withAlpha(plan.alpha) {
        if (dx != 0f || look.residualY != 0f) translate(dx, look.residualY) { drawContent() } else drawContent()
    }
}

/** Whether any follower's plan now differs from what the last composition was built for. */
private fun FollowerPlans.composedChanged(): Boolean {
    for (plan in plans.values) if (plan.composed.differs(plan)) return true
    return false
}

/** What a follower's composition was built from: whether it was composed at all, its size, scale and density, and its pin. */
internal class FollowerComposed {
    private var drawn = false
    private var width = 0f
    private var height = 0f
    private var scale = 0f
    private var density = 0f
    private var pinned = false
    private var pinAngle = 0f

    /** Forgets the last composition's record: called before a composition, which records each follower it composes. */
    fun reset() {
        drawn = false
    }

    /** Records [plan] as a composition builds its follower from it. */
    fun record(plan: FollowerPlan) {
        drawn = true
        width = plan.width
        height = plan.height
        scale = plan.scale
        density = plan.density
        pinned = plan.element.pinned
        pinAngle = plan.element.pinAngle
    }

    /** Whether [plan] needs a composition other than the one recorded. */
    fun differs(plan: FollowerPlan): Boolean {
        val element = plan.element
        val built = width == plan.width && height == plan.height && scale == plan.scale && density == plan.density &&
            pinned == element.pinned && (!pinned || pinAngle == element.pinAngle)
        return plan.drawn != drawn || (drawn && !built)
    }
}

/** A follower's fade and sub-pixel remainder, as a value a retained draw polls: a new one only when either changes. */
internal class FollowerLook : PolledState<Any>() {
    private var alpha = Float.NaN
    var residualX = 0f
        private set
    var residualY = 0f
        private set
    private var token = Any()

    fun update(alpha: Float, residualX: Float, residualY: Float) {
        if (alpha == this.alpha && residualX == this.residualX && residualY == this.residualY) return
        this.alpha = alpha
        this.residualX = residualX
        this.residualY = residualY
        token = Any()
    }

    override fun read(): Any = token
}
