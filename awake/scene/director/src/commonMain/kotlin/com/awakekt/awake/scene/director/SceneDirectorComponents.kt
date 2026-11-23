/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.core.math.ArcLengthSpline3f
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.LensSpec
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Scene component attaching a [DirectorScript] (either by project asset [scriptPath] or [inlineScript])
 * to a scene node for in-game cutscenes or trailer captures.
 *
 * @property scriptPath Optional project-relative path to a `*.director.json` script.
 * @property inlineScript Optional inline [DirectorScript] embedded directly in the scene.
 * @property inputTakePath Optional project-relative path to a recorded `*.take.json` gameplay replay.
 * @property autoPlay Whether playback starts automatically when the scene begins.
 * @property loop Whether playback loops continuously upon reaching the end.
 * @property priority Priority among multiple active directors (highest wins).
 */
@Serializable
@SerialName("camera_director")
data class SceneCameraDirector(
    val scriptPath: String? = null,
    val inlineScript: DirectorScript? = null,
    val inputTakePath: String? = null,
    val autoPlay: Boolean = true,
    val loop: Boolean = false,
    val priority: Int = 0,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (scriptPath.isNullOrBlank() && inlineScript == null) {
            add(SceneValidationIssue(path, "camera_director requires either scriptPath or inlineScript"))
        }
        inlineScript?.validate()?.forEach { issue ->
            add(SceneValidationIssue("$path.inlineScript.${issue.path}", issue.message))
        }
    }
}

/**
 * Runtime ECS component holding a scene node's authored [SceneCameraDirector] and playback state.
 *
 * @property authored Original serialized schema descriptor.
 * @property resolvedScript Loaded [DirectorScript] (from inline script or resolved [SceneCameraDirector.scriptPath]).
 * @property playing Whether this director is currently active and advancing.
 * @property loop Whether playback repeats when finished.
 * @property priority Priority order among active directors (highest wins).
 */
class CameraDirectorComponent(
    val authored: SceneCameraDirector,
    var resolvedScript: DirectorScript? = authored.inlineScript,
    var playing: Boolean = authored.autoPlay,
    var loop: Boolean = authored.loop,
    var priority: Int = authored.priority,
)

/**
 * Binding connecting [SceneCameraDirector] scene documents to [CameraDirectorComponent].
 */
@RegisteredBinding
object CameraDirectorBinding : SceneComponentBinding<CameraDirectorComponent, SceneCameraDirector> {
    override val componentClass: KClass<CameraDirectorComponent> = CameraDirectorComponent::class
    override val schemaClass: KClass<SceneCameraDirector> = SceneCameraDirector::class
    override val serializer = SceneCameraDirector.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCameraDirector,
        context: SceneResolutionContext,
    ) {
        world.add(entity, CameraDirectorComponent(component))
    }

    override fun export(world: World, entity: Entity, component: CameraDirectorComponent): SceneCameraDirector =
        component.authored
}

/**
 * Reusable named virtual camera authored in a `.scene.json` node, referenceable from a
 * [DirectorScript] via [ShotRigSpec.NamedCamera] or activated dynamically during gameplay cutscenes.
 *
 * @property eye World-space camera eye position (defaults to the node's own `Transform` when omitted).
 * @property center World-space look-at target point.
 * @property lens Optical focal length / FOV, clip planes, and Dutch roll.
 * @property priority Optional cutscene priority when activated dynamically.
 */
@Serializable
@SerialName("virtual_camera")
data class SceneVirtualCamera(
    val eye: SceneVec3? = null,
    val center: SceneVec3 = SceneVec3(0f, 0f, 0f),
    val lens: LensSpec = LensSpec(),
    val priority: Int = 0,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        val mm = lens.focalLengthMm
        if (mm != null && (mm <= 0f || !mm.isFinite())) {
            add(SceneValidationIssue("$path.lens.focalLengthMm", "virtual_camera focalLengthMm must be > 0"))
        }
        val fov = lens.fovYDegrees
        val invalidFov = fov != null && (!fov.isFinite() || fov !in MIN_FOV_DEG..MAX_FOV_DEG)
        if (invalidFov) {
            add(SceneValidationIssue("$path.lens.fovYDegrees", "virtual_camera fovYDegrees must be in (0, 180)"))
        }
        if (lens.near <= 0f || lens.far <= lens.near) {
            add(SceneValidationIssue("$path.lens", "virtual_camera near must be > 0 and far > near"))
        }
    }
}

/**
 * Runtime ECS component representing a scene-authored virtual camera rig.
 *
 * @property authored Original serialized [SceneVirtualCamera] descriptor.
 */
class VirtualCameraComponent(
    val authored: SceneVirtualCamera,
)

/**
 * Binding connecting [SceneVirtualCamera] to [VirtualCameraComponent].
 */
@RegisteredBinding
object VirtualCameraBinding : SceneComponentBinding<VirtualCameraComponent, SceneVirtualCamera> {
    override val componentClass: KClass<VirtualCameraComponent> = VirtualCameraComponent::class
    override val schemaClass: KClass<SceneVirtualCamera> = SceneVirtualCamera::class
    override val serializer = SceneVirtualCamera.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneVirtualCamera,
        context: SceneResolutionContext,
    ) {
        world.add(entity, VirtualCameraComponent(component))
    }

    override fun export(world: World, entity: Entity, component: VirtualCameraComponent): SceneVirtualCamera =
        component.authored
}

/**
 * Named 3D dolly spline track authored directly in a `.scene.json` node so director scripts can
 * reference it cleanly by `railName`.
 *
 * @property waypoints Ordered world-space (or node-local when [localSpace] is true) control points (at least 2).
 * @property closed Whether the rail loops smoothly from the last waypoint back to the first.
 * @property alpha Centripetal parameterization exponent in `0..1` (default `0.5f`).
 * @property localSpace Whether [waypoints] are offset by the node's `Transform.position`.
 */
@Serializable
@SerialName("camera_rail")
data class SceneCameraRail(
    val waypoints: List<SceneVec3> = emptyList(),
    val closed: Boolean = false,
    val alpha: Float = DEFAULT_ALPHA,
    val localSpace: Boolean = false,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (waypoints.size < 2) {
            add(SceneValidationIssue("$path.waypoints", "camera_rail requires at least 2 waypoints"))
        }
        if (alpha !in 0f..1f || !alpha.isFinite()) {
            add(SceneValidationIssue("$path.alpha", "camera_rail alpha must be in 0..1"))
        }
    }
}

/**
 * Runtime ECS component holding an authored [SceneCameraRail] and its lazily compiled
 * [ArcLengthSpline3f].
 *
 * @property authored Original serialized [SceneCameraRail] descriptor.
 * @property spline Precomputed arc-length parameterized 3D spline.
 */
class CameraRailComponent(
    val authored: SceneCameraRail,
    val spline: ArcLengthSpline3f,
)

/**
 * Binding connecting [SceneCameraRail] to [CameraRailComponent].
 */
@RegisteredBinding
object CameraRailBinding : SceneComponentBinding<CameraRailComponent, SceneCameraRail> {
    override val componentClass: KClass<CameraRailComponent> = CameraRailComponent::class
    override val schemaClass: KClass<SceneCameraRail> = SceneCameraRail::class
    override val serializer = SceneCameraRail.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCameraRail,
        context: SceneResolutionContext,
    ) {
        if (component.waypoints.size < 2) return
        val points = component.waypoints.map { Vec3f(it.x, it.y, it.z) }
        val spline = ArcLengthSpline3f(points, closed = component.closed, alpha = component.alpha)
        world.add(entity, CameraRailComponent(component, spline))
    }

    override fun export(world: World, entity: Entity, component: CameraRailComponent): SceneCameraRail =
        component.authored
}

/**
 * Lightweight named spatial landmark or actor blocking mark placed in a scene so AI directors and
 * choreographed cues can reference meaningful places (such as `"HarborOverlook"` or `"DuelCenter"`)
 * without guessing raw world coordinates.
 *
 * @property tags Semantic descriptor tags (e.g. `["overlook", "vista"]`, `["actor_mark"]`).
 * @property radius Optional staging footprint radius in world units (`> 0f`).
 */
@Serializable
@SerialName("stage_marker")
data class SceneStageMarker(
    val tags: List<String> = emptyList(),
    val radius: Float = DEFAULT_RADIUS,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (radius <= 0f || !radius.isFinite()) {
            add(SceneValidationIssue("$path.radius", "stage_marker radius must be finite and > 0"))
        }
    }
}

/**
 * Runtime ECS component for a [SceneStageMarker].
 *
 * @property authored Original serialized [SceneStageMarker] descriptor.
 */
class StageMarkerComponent(
    val authored: SceneStageMarker,
)

/**
 * Binding connecting [SceneStageMarker] to [StageMarkerComponent].
 */
@RegisteredBinding
object StageMarkerBinding : SceneComponentBinding<StageMarkerComponent, SceneStageMarker> {
    override val componentClass: KClass<StageMarkerComponent> = StageMarkerComponent::class
    override val schemaClass: KClass<SceneStageMarker> = SceneStageMarker::class
    override val serializer = SceneStageMarker.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneStageMarker,
        context: SceneResolutionContext,
    ) {
        world.add(entity, StageMarkerComponent(component))
    }

    override fun export(world: World, entity: Entity, component: StageMarkerComponent): SceneStageMarker =
        component.authored
}

private const val MIN_FOV_DEG = 0.01f
private const val MAX_FOV_DEG = 179.99f
private const val DEFAULT_ALPHA = 0.5f
private const val DEFAULT_RADIUS = 1f
