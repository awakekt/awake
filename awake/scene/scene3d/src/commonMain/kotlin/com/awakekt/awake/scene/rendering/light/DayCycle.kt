/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.sky.Skybox
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.reflect.KClass

/**
 * A day on the scene's sun: put it on the node of the directional `light`. [DayCycleSystem] moves
 * the light's direction along the sun's path and blends the light, the `skybox` and the `fog`
 * between [stops].
 *
 * The sun rises at [sunriseAzimuthDegrees] at time 0.25, is highest at noon (0.5), a quarter turn
 * clockwise from where it rose, at [noonElevationDegrees], sets opposite its rise at 0.75 and is as
 * far below the horizon at midnight. Below the horizon the light still points at the sun, so the
 * sky's moon stays opposite it and the ground gets no direct light; how dark night looks comes
 * from the stops.
 *
 * @property dayLengthSeconds Seconds in one day. 0 holds the time of day still.
 * @property time The time of day the scene starts at: 0 midnight, 0.25 sunrise, 0.5 noon, 0.75
 *   sunset, up to but not including 1.
 * @property sunriseAzimuthDegrees Where the sun rises, clockwise from -Z seen from above, as camera
 *   yaw turns: 90 is +X.
 * @property noonElevationDegrees The sun's height above the horizon at noon, above 0 and at most 90.
 * @property stops How the scene looks at times of day, in time order.
 */
@Serializable
@SerialName("day_cycle")
data class SceneDayCycle(
    @PropertyRange(min = 0.0) val dayLengthSeconds: Float = 600f,
    @PropertyRange(min = 0.0, max = 1.0, exclusiveMax = true) val time: Float = 0.5f,
    val sunriseAzimuthDegrees: Float = 90f,
    @PropertyRange(min = 0.0, max = 90.0, exclusiveMin = true) val noonElevationDegrees: Float = 60f,
    val stops: List<SceneDayStop> = emptyList(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!dayLengthSeconds.isFinite() || dayLengthSeconds < 0f) {
            add(SceneValidationIssue(path, "day_cycle.dayLengthSeconds must be finite and at least 0; was $dayLengthSeconds"))
        }
        if (!(time >= 0f && time < 1f)) add(SceneValidationIssue(path, "day_cycle.time must be from 0 up to 1; was $time"))
        if (!sunriseAzimuthDegrees.isFinite()) {
            add(SceneValidationIssue(path, "day_cycle.sunriseAzimuthDegrees must be finite; was $sunriseAzimuthDegrees"))
        }
        if (!(noonElevationDegrees > 0f && noonElevationDegrees <= 90f)) {
            add(SceneValidationIssue(path, "day_cycle.noonElevationDegrees must be above 0 and at most 90; was $noonElevationDegrees"))
        }
        addAll(stopIssues(path, stops))
    }
}

private fun stopIssues(path: String, stops: List<SceneDayStop>): List<SceneValidationIssue> = buildList {
    val times = stops.map { it.time }
    if (times.any { !(it >= 0f && it < 1f) } || times.zipWithNext().any { (a, b) -> b < a }) {
        add(SceneValidationIssue(path, "day_cycle.stops must be in time order from 0 up to 1"))
    }
    stops.forEach { stop ->
        val intensity = stop.lightIntensity
        if (intensity != null && !(intensity.isFinite() && intensity >= 0f)) {
            add(SceneValidationIssue(path, "day_cycle stop lightIntensity must be finite and at least 0; was $intensity"))
        }
        val skyExposure = stop.skyExposure
        if (skyExposure != null && !(skyExposure.isFinite() && skyExposure >= 0f)) {
            add(SceneValidationIssue(path, "day_cycle stop skyExposure must be finite and at least 0; was $skyExposure"))
        }
        val ambient = stop.ambient
        if (ambient != null && !(ambient > 0f && ambient <= 1f)) {
            add(SceneValidationIssue(path, "day_cycle stop ambient must be above 0 and at most 1; was $ambient"))
        }
    }
}

/**
 * How the scene looks at [time] of day. Every field is optional: between the stops that set a
 * field it blends linearly, wrapping from the day's last such stop to its first across midnight.
 * A field no stop sets keeps the value authored on its own component.
 *
 * @property time The time of day, as [SceneDayCycle.time].
 * @property horizonColor The `skybox` horizon colour. A solid-colour sky takes it as its colour.
 * @property zenithColor The `skybox` zenith colour.
 * @property lightColor The light's colour.
 * @property lightIntensity The light's intensity, 0 or more.
 * @property ambient The light's ambient share, above 0 and at most 1.
 * @property fogColor The `fog` colour.
 * @property skyTint The colour a cubemap `skybox` is multiplied by; white leaves it as baked.
 * @property skyExposure A multiplier on a cubemap `skybox`'s exposure, 0 or more; 1 leaves it as authored.
 */
@Serializable
data class SceneDayStop(
    val time: Float,
    val horizonColor: SceneColor? = null,
    val zenithColor: SceneColor? = null,
    val lightColor: SceneColor? = null,
    val lightIntensity: Float? = null,
    val ambient: Float? = null,
    val fogColor: SceneColor? = null,
    val skyTint: SceneColor? = null,
    val skyExposure: Float? = null,
) {
    /** The constructor as released before the sky fields, kept so compiled callers still link. */
    @Deprecated("Use the constructor with the sky fields.", level = DeprecationLevel.HIDDEN)
    constructor(
        time: Float,
        horizonColor: SceneColor? = null,
        zenithColor: SceneColor? = null,
        lightColor: SceneColor? = null,
        lightIntensity: Float? = null,
        ambient: Float? = null,
        fogColor: SceneColor? = null,
    ) : this(time, horizonColor, zenithColor, lightColor, lightIntensity, ambient, fogColor, null, null)

    /** [copy] as released before the sky fields. */
    @Deprecated("Use copy with the sky fields.", level = DeprecationLevel.HIDDEN)
    fun copy(
        time: Float = this.time,
        horizonColor: SceneColor? = this.horizonColor,
        zenithColor: SceneColor? = this.zenithColor,
        lightColor: SceneColor? = this.lightColor,
        lightIntensity: Float? = this.lightIntensity,
        ambient: Float? = this.ambient,
        fogColor: SceneColor? = this.fogColor,
    ): SceneDayStop = copy(time, horizonColor, zenithColor, lightColor, lightIntensity, ambient, fogColor, skyTint, skyExposure)
}

/**
 * A playing day: the [authored] component and the current [time] of day, which [DayCycleSystem]
 * advances. Saving writes [authored] back, so play time is never saved.
 */
class DayCycle(val authored: SceneDayCycle) {
    /** The time of day, as [SceneDayCycle.time]. Set it to jump to another time; the system wraps it. */
    var time: Float = authored.time
}

/** Binding definition connecting [SceneDayCycle] descriptors to ECS [DayCycle] components. */
@RegisteredBinding
object DayCycleBinding : SceneComponentBinding<DayCycle, SceneDayCycle> {
    override val componentClass: KClass<DayCycle> = DayCycle::class
    override val schemaClass: KClass<SceneDayCycle> = SceneDayCycle::class
    override val serializer = SceneDayCycle.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneDayCycle,
        context: SceneResolutionContext,
    ) {
        world.add(entity, DayCycle(component))
    }

    override fun export(world: World, entity: Entity, component: DayCycle): SceneDayCycle = component.authored
}

/**
 * Advances each [DayCycle] and writes the sun's direction and the blended stops to the [Light] on
 * its entity and to the first [Skybox] and [Fog] in the world, the ones the renderer reads. A
 * procedural sky takes both colours, a solid-colour sky the horizon colour, a cubemap sky its tint, exposure and panoramas (see [CubemapSkySpans]).
 *
 * Nothing changes until this runs, so a scene that is not playing shows its light, sky and fog as
 * authored. Running it with a delta of 0 applies the current time without advancing it, for a host
 * that wants to show a time of day in a still scene.
 *
 * The light's direction, colour and intensity are written in place. A sky or fog colour is an
 * immutable [Color] and the ambient share a boxed `Float?`, so those are replaced only once they
 * move by a quarter of an 8-bit step, which keeps a slow day from allocating every frame.
 *
 * A rotated light node shines along its rotation and ignores the direction written here.
 */
class DayCycleSystem : System {
    private val lightColor = StopSpan { it.lightColor }
    private val lightIntensity = StopSpan { it.lightIntensity }
    private val ambient = StopSpan { it.ambient }
    private val horizon = StopSpan { it.horizonColor }
    private val zenith = StopSpan { it.zenithColor }
    private val fogColor = StopSpan { it.fogColor }
    private val cubemapSky = CubemapSkySpans()
    private var families: Families? = null

    override fun update(world: World, delta: Float) {
        val families = families(world)
        families.cycles.forEach { _, cycle, light ->
            val day = cycle.authored
            if (day.dayLengthSeconds > 0f) cycle.time += delta / day.dayLengthSeconds
            cycle.time = cycle.time.mod(1f)
            sunDirection(cycle.time, day.sunriseAzimuthDegrees, day.noonElevationDegrees, light.direction)
            if (day.stops.isNotEmpty()) applyStops(families, day.stops, cycle.time, light)
            val sky = families.skies.takeIf { it.size > 0 }?.componentAt(0)?.mode
            if (sky is Skybox.Mode.Cubemap) cubemapSky.apply(sky, day.stops, cycle.time)
        }
    }

    private fun families(world: World): Families {
        val current = families
        if (current != null && current.world === world && current.generation == world.generation) return current
        return Families(world).also { families = it }
    }

    private fun applyStops(families: Families, stops: List<SceneDayStop>, time: Float, light: Light) {
        if (lightColor.find(stops, time)) lightColor.colorInto(light.color)
        if (lightIntensity.find(stops, time)) light.intensity = lightIntensity.value()
        if (ambient.find(stops, time)) {
            val share = ambient.value()
            val current = light.ambient
            if (current == null || abs(current - share) >= COLOR_STEP) light.ambient = share
        }
        val fog = families.fogs.takeIf { it.size > 0 }?.componentAt(0)
        if (fog != null && fogColor.find(stops, time)) fog.color = fogColor.color(fog.color)
        when (val sky = families.skies.takeIf { it.size > 0 }?.componentAt(0)?.mode) {
            is Skybox.Mode.Procedural -> {
                if (horizon.find(stops, time)) sky.horizonColor = horizon.color(sky.horizonColor)
                if (zenith.find(stops, time)) sky.zenithColor = zenith.color(sky.zenithColor)
            }
            is Skybox.Mode.SolidColor -> if (horizon.find(stops, time)) sky.color = horizon.color(sky.color)
            else -> Unit
        }
    }

    /**
     * The families this reads for one [world], valid while its generation is unchanged. Resolving
     * a family allocates, so it is done once rather than every frame.
     */
    private class Families(val world: World) {
        val generation = world.generation
        val cycles = world.family<DayCycle, Light>()
        val fogs = world.family<Fog>()
        val skies = world.family<Skybox>()
    }
}

/**
 * Writes the direction toward the sun at [time] of day into [out]: a circle through the sunrise
 * point at 0.25, the noon point at 0.5 and the sunset point at 0.75.
 */
private fun sunDirection(time: Float, sunriseAzimuthDegrees: Float, noonElevationDegrees: Float, out: Vec3f) {
    // 0 at noon, -1/4 turn at sunrise, +1/4 turn at sunset.
    val hourAngle = (time - NOON) * TURN
    val towardNoon = cos(hourAngle)
    val towardSunset = sin(hourAngle)
    val azimuth = sunriseAzimuthDegrees * RADIANS_PER_DEGREE
    val elevation = noonElevationDegrees * RADIANS_PER_DEGREE
    // Sunrise is (sin a, 0, -cos a); noon lies a quarter turn clockwise, along (cos a, 0, sin a).
    val noonHorizontal = towardNoon * cos(elevation)
    out.set(
        noonHorizontal * cos(azimuth) - towardSunset * sin(azimuth),
        towardNoon * sin(elevation),
        noonHorizontal * sin(azimuth) + towardSunset * cos(azimuth),
    )
}

/** A quarter of an 8-bit display step: too small to see, large enough to skip most frames' writes. */
internal const val COLOR_STEP = 1f / 1024f
private const val NOON = 0.5f
private const val TURN = (2.0 * PI).toFloat()
private const val RADIANS_PER_DEGREE = (PI / 180.0).toFloat()
