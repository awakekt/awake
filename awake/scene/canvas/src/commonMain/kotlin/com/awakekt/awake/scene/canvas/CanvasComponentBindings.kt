/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions") // A load and a save mapping for each component.

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasAppearance
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasFollow
import com.awakekt.awake.ui.canvas.CanvasFollowArrow
import com.awakekt.awake.ui.canvas.CanvasInput
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasText
import com.awakekt.awake.ui.canvas.CanvasTooltip
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

// Where each canvas component of a scene file becomes the live component, and back: plain field
// copies, one binding for each. The widgets' are in CanvasWidgetBindings.kt. Attaching one gives the
// entity's canvas element the component too, and an export first brings the two in step, so what is
// saved is what the element draws.

/** The binding of one canvas component: [load] copies the saved fields into a new component, [save] back. */
internal class CanvasComponentBinding<C : Any, S : SceneComponent>(
    override val componentClass: KClass<C>,
    override val schemaClass: KClass<S>,
    override val serializer: KSerializer<S>,
    private val load: (S) -> C,
    private val save: (C) -> S,
) : SceneComponentBinding<C, S> {
    override fun attachTyped(world: World, entity: Entity, component: S, context: SceneResolutionContext) {
        world.attachCanvasComponent(entity, load(component))
    }

    override fun export(world: World, entity: Entity, component: C): S = save(component)

    override fun exportFrom(world: World, entity: Entity): S? {
        world.syncCanvasElement(entity)
        return super.exportFrom(world, entity)
    }
}

/** Loads and saves a [CanvasRect] as the `canvas_rect` scene component. */
object CanvasRectBinding : SceneComponentBinding<CanvasRect, SceneCanvasRect> by CanvasComponentBinding(
    CanvasRect::class,
    SceneCanvasRect::class,
    SceneCanvasRect.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasText] as the `canvas_text` scene component. */
object CanvasTextBinding : SceneComponentBinding<CanvasText, SceneCanvasText> by CanvasComponentBinding(
    CanvasText::class,
    SceneCanvasText::class,
    SceneCanvasText.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasAppearance] as the `canvas_appearance` scene component. */
object CanvasAppearanceBinding : SceneComponentBinding<CanvasAppearance, SceneCanvasAppearance> by CanvasComponentBinding(
    CanvasAppearance::class,
    SceneCanvasAppearance::class,
    SceneCanvasAppearance.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasLayout] as the `canvas_layout` scene component. */
object CanvasLayoutBinding : SceneComponentBinding<CanvasLayout, SceneCanvasLayout> by CanvasComponentBinding(
    CanvasLayout::class,
    SceneCanvasLayout::class,
    SceneCanvasLayout.serializer(),
    { it.layout },
    { SceneCanvasLayout(it) },
)

/** Loads and saves a [CanvasCluster] as the `canvas_cluster` scene component. */
object CanvasClusterBinding : SceneComponentBinding<CanvasCluster, SceneCanvasCluster> by CanvasComponentBinding(
    CanvasCluster::class,
    SceneCanvasCluster::class,
    SceneCanvasCluster.serializer(),
    { it.cluster },
    { SceneCanvasCluster(it) },
)

/** Loads and saves a [CanvasBinding] as the `canvas_bind` scene component. */
object CanvasBindBinding : SceneComponentBinding<CanvasBinding, SceneCanvasBind> by CanvasComponentBinding(
    CanvasBinding::class,
    SceneCanvasBind::class,
    SceneCanvasBind.serializer(),
    { it.bind },
    { SceneCanvasBind(it) },
)

/** Loads and saves a [CanvasFollow] as the `canvas_follow` scene component. */
object CanvasFollowBinding : SceneComponentBinding<CanvasFollow, SceneCanvasFollow> by CanvasComponentBinding(
    CanvasFollow::class,
    SceneCanvasFollow::class,
    SceneCanvasFollow.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasFollowArrow] as the `canvas_follow_arrow` scene component. */
object CanvasFollowArrowBinding : SceneComponentBinding<CanvasFollowArrow, SceneCanvasFollowArrow> by CanvasComponentBinding(
    CanvasFollowArrow::class,
    SceneCanvasFollowArrow::class,
    SceneCanvasFollowArrow.serializer(),
    { CanvasFollowArrow() },
    { SceneCanvasFollowArrow() },
)

/** Loads and saves a [CanvasInput] as the `canvas_input` scene component. */
object CanvasInputBinding : SceneComponentBinding<CanvasInput, SceneCanvasInput> by CanvasComponentBinding(
    CanvasInput::class,
    SceneCanvasInput::class,
    SceneCanvasInput.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasTooltip] as the `canvas_tooltip` scene component. */
object CanvasTooltipBinding : SceneComponentBinding<CanvasTooltip, SceneCanvasTooltip> by CanvasComponentBinding(
    CanvasTooltip::class,
    SceneCanvasTooltip::class,
    SceneCanvasTooltip.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

internal fun SceneCanvasRect.toComponent(): CanvasRect = CanvasRect().also {
    it.anchor = anchor
    it.offsetX = offsetX
    it.offsetY = offsetY
    it.width = width
    it.height = height
    it.order = order
    it.visible = visible
    it.touchOnly = touchOnly
    it.safeArea = safeArea
    it.grow = grow
    it.elementId = elementId
    it.controlId = controlId
}

internal fun CanvasRect.toScene(): SceneCanvasRect =
    SceneCanvasRect(anchor, offsetX, offsetY, width, height, order, visible, touchOnly, safeArea, grow, elementId, controlId)

internal fun SceneCanvasText.toComponent(): CanvasText = CanvasText().also {
    it.text = text
    it.textKey = textKey
    it.textArgs = textArgs
    it.fontSize = fontSize
    it.font = font
    it.textAlign = textAlign
    it.links = links
}

internal fun CanvasText.toScene(): SceneCanvasText = SceneCanvasText(text, textKey, textArgs, fontSize, font, textAlign, links)

internal fun SceneCanvasAppearance.toComponent(): CanvasAppearance = CanvasAppearance().also {
    it.color = color
    it.background = background
    it.style = style
    it.skin = skin
}

internal fun CanvasAppearance.toScene(): SceneCanvasAppearance = SceneCanvasAppearance(color, background, style, skin)

internal fun SceneCanvasFollow.toComponent(): CanvasFollow = CanvasFollow().also {
    it.node = node
    it.offset = Vec3f(offset.x, offset.y, offset.z)
    it.bounds = bounds
    it.outset = outset
    it.rules = rules
}

internal fun CanvasFollow.toScene(): SceneCanvasFollow = SceneCanvasFollow(node, SceneVec3(offset.x, offset.y, offset.z), bounds, outset, rules)

internal fun SceneCanvasInput.toComponent(): CanvasInput = CanvasInput().also {
    it.pressable = pressable
    it.draggable = draggable
    it.dropTarget = dropTarget
    it.wheel = wheel
}

internal fun CanvasInput.toScene(): SceneCanvasInput = SceneCanvasInput(pressable, draggable, dropTarget, wheel)

internal fun SceneCanvasTooltip.toComponent(): CanvasTooltip = CanvasTooltip().also {
    it.text = text
    it.textArgs = textArgs
}

internal fun CanvasTooltip.toScene(): SceneCanvasTooltip = SceneCanvasTooltip(text, textArgs)
