/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Ray
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.rayThroughViewport
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlin.reflect.KClass

/**
 * One reversible scene edit. [revert] restores the exact state [apply] replaced rather than
 * computing an inverse, so it cannot drift against a system editing the same value.
 */
interface EditCommand {
    /** Shown after "Undo" and "Redo". */
    val label: String

    /** Applies the edit to the scene. */
    fun apply()

    /** Reverts the edit, restoring the previous scene state. */
    fun revert()

    /** Equal non-null keys on consecutive commands make one undo step, such as a drag or typed number. */
    val mergeKey: Any? get() = null

    /** This command widened to also undo [earlier]. Override it when the command holds a before-value. */
    fun absorb(earlier: EditCommand): EditCommand = this
}

/** The host's undo history. Every scene edit a plugin makes goes through it. */
interface EditHistory {
    /** Applies [command] and records it. */
    fun execute(command: EditCommand)

    /** Records a [command] whose effect already happened, such as a drag that wrote live values. */
    fun record(command: EditCommand)
}

/** The entities selected in the editor. [primary] is the one the inspector and gizmo act on. */
interface SceneSelection {
    /** Set of all entities currently included in the selection. */
    val entities: Set<Entity>
    val primary: Entity?

    /** Selects only [entity], or clears the selection when it is null. */
    fun select(entity: Entity?)
}

/**
 * A line a viewport tool draws over the scene, in world space.
 *
 * @property start Starting coordinate of the line segment in world space.
 * @property end Ending coordinate of the line segment in world space.
 * @property color Color used to render the overlay line.
 */
data class OverlayLine(val start: Vec3f, val end: Vec3f, val color: Color)

/**
 * What a viewport tool sees on a frame: the edited world, the edit camera, and the pointer in
 * viewport pixels with input the editor UI has already claimed taken out.
 *
 * @property world Active ECS world being inspected or edited.
 * @property selection Current scene entity selection.
 * @property history Editor undo/redo history manager.
 * @property camera Active editor camera lens.
 * @property viewProjection Combined view-projection transformation matrix.
 * @property clipSpace Target platform clip space convention.
 * @property viewportWidth Width of the 3D viewport canvas in pixels.
 * @property viewportHeight Height of the 3D viewport canvas in pixels.
 * @property pointerX Horizontal coordinate of the pointer in viewport pixels.
 * @property pointerY Vertical coordinate of the pointer in viewport pixels.
 * @property isPointerDown Whether the primary pointer button is pressed.
 * @property keysDown Set of currently depressed keyboard keys not consumed by UI.
 */
@Suppress("LongParameterList") // The camera, viewport, and pointer are all needed to aim a tool.
class ViewportContext(
    val world: World,
    val selection: SceneSelection,
    val history: EditHistory,
    val camera: Lens,
    val viewProjection: Mat4,
    val clipSpace: ClipSpace,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pointerX: Float,
    val pointerY: Float,
    val isPointerDown: Boolean,
    val keysDown: Set<Key>,
) {
    /** The world-space ray under the pointer, or null when the camera cannot be inverted. */
    fun pointerRay(): Ray? =
        camera.rayThroughViewport(pointerX, pointerY, viewProjection, viewportWidth, viewportHeight, clipSpace)
}

/**
 * A tool that works inside the viewport, such as a brush or a placement tool. The host picks the
 * active tool and keeps the gestures it reserves, such as orbiting the camera.
 */
interface ViewportToolProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.ViewportTool
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Whether this tool takes the pointer in [context]. */
    fun isApplicable(context: ViewportContext): Boolean

    /** Lines to draw this frame while the pointer is over the viewport. */
    fun onHover(context: ViewportContext): List<OverlayLine> = emptyList()

    /** Called each frame the pointer is pressed. Returns true when the tool consumed it. */
    fun onPointerDrag(context: ViewportContext): Boolean = false

    /** Called once when a press the tool consumed ends; the place to record the drag's undo step. */
    fun onPointerUp(context: ViewportContext) {}
}

/**
 * ECS systems that run every frame on the world being edited, and never on the play-mode world.
 * Gameplay ships as runtime code the game registers, not as an editor plugin.
 */
interface SceneSystemsProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.SceneSystems
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Fresh systems each time the host builds its edit loop, such as on scene load. */
    fun createSystems(): List<System>
}

/**
 * Fields an inspector section can contain. Each field writes its value and records the undo step
 * itself; the host decides how the fields look.
 */
interface InspectorFieldScope {
    /** A short text or asset reference. Hosts without text editing may leave it out. */
    fun text(label: String, value: String, write: (String) -> Unit) {}

    /** A text value with host-provided [choices], such as project asset paths. */
    fun choices(label: String, value: String, choices: List<String>, write: (String) -> Unit) =
        text(label, value, write)

    /** A number. Consecutive edits to the same field make one undo step. */
    fun scalar(label: String, value: Float, write: (Float) -> Unit)

    /** A number between [min] and [max], snapped to [step] unless it is 0. */
    fun slider(
        label: String,
        value: Float,
        min: Float = 0f,
        max: Float = 1f,
        step: Float = 0f,
        write: (Float) -> Unit,
    ) = scalar(label, value, write)

    /** An on/off. Each click is its own undo step. */
    fun toggle(label: String, value: Boolean, write: (Boolean) -> Unit)

    /** A choice between an enum's [cases]. */
    fun <T : Enum<T>> options(label: String, value: T, cases: List<T>, write: (T) -> Unit)

    /** Three numbers, edited in place on the vector the component holds. */
    fun vector(label: String, value: Vec3f)

    /** Three numbers, written back through [write] for a component that holds an immutable value. */
    fun vector(label: String, value: Vec3f, write: (Vec3f) -> Unit) = vector(label, value)
}

/**
 * Inspector fields for one component type. The host shows it for a selected entity that carries
 * [componentType], after its own built-in sections.
 */
interface ComponentInspectorProvider : ComponentProvider {
    override val codec: ProviderCodec get() = NoProviderConfiguration

    val componentType: KClass<out Any>

    /** Emits the fields. Read the component defensively: a system may remove it before this runs. */
    fun fields(scope: InspectorFieldScope, world: World, entity: Entity)
}
