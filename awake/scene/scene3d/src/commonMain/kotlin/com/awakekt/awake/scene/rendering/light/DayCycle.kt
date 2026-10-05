/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
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
    val dayLengthSeconds: Float = 600f,
    val time: Float = 0.5f,
    val sunriseAzimuthDegrees: Float = 90f,
    val noonElevationDegrees: Float = 60f,
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
)

/**
 * A playing day: the [authored] component and the current [time] of day, which [DayCycleSystem]
 * advances. Saving writes [authored] back, so play time is never saved.
 */
class DayCycle(val authored: SceneDayCycle) {
    /** The time of day, as [SceneDayCycle.time]. Set it to jump to another time; the system wraps it. */
    var time: Float = authored.time
}

/** Binding definition connecting [SceneDayCycle] descriptors to ECS [DayCycle] components. */
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
 * procedural sky takes both colours, a solid-colour sky the horizon colour, a cubemap sky neither.
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
    private var families: Families? = null

    override fun update(world: World, delta: Float) {
        val families = families(world)
        families.cycles.forEach { _, cycle, light ->
            val day = cycle.authored
            if (day.dayLengthSeconds > 0f) cycle.time += delta / day.dayLengthSeconds
            cycle.time = cycle.time.mod(1f)
            sunDirection(cycle.time, day.sunriseAzimuthDegrees, day.noonElevationDegrees, light.direction)
            if (day.stops.isNotEmpty()) applyStops(families, day.stops, cycle.time, light)
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

/** One stop field's values either side of a time of day, and how far between them the time is. */
private class StopSpan<T : Any>(private val field: (SceneDayStop) -> T?) {
    lateinit var from: T
    lateinit var to: T
    var fraction = 0f

    /** Finds the stops around [time] that set this field; false when none does. */
    fun find(stops: List<SceneDayStop>, time: Float): Boolean {
        var first = -1
        var last = -1
        var before = -1
        var after = -1
        for (index in stops.indices) {
            if (field(stops[index]) == null) continue
            if (first < 0) first = index
            last = index
            if (stops[index].time <= time) before = index else if (after < 0) after = index
        }
        // Before the day's first stop, yesterday's last one leads in; after its last, tomorrow's first follows.
        if (first >= 0) between(stops[if (before >= 0) before else last], stops[if (after >= 0) after else first], time)
        return first >= 0
    }

    private fun between(fromStop: SceneDayStop, toStop: SceneDayStop, time: Float) {
        from = checkNotNull(field(fromStop))
        to = checkNotNull(field(toStop))
        val gap = (toStop.time - fromStop.time).mod(1f)
        fraction = if (gap > 0f) (time - fromStop.time).mod(1f) / gap else 0f
    }
}

private fun StopSpan<Float>.value(): Float = lerp(from, to, fraction)

private fun StopSpan<SceneColor>.colorInto(target: Vec3f) {
    target.set(lerp(from.r, to.r, fraction), lerp(from.g, to.g, fraction), lerp(from.b, to.b, fraction))
}

/** The blended colour, or [current] while it is within [COLOR_STEP] of it. */
private fun StopSpan<SceneColor>.color(current: Color): Color {
    val r = lerp(from.r, to.r, fraction)
    val g = lerp(from.g, to.g, fraction)
    val b = lerp(from.b, to.b, fraction)
    val a = lerp(from.a, to.a, fraction)
    val moved = maxOf(maxOf(abs(current.r - r), abs(current.g - g)), maxOf(abs(current.b - b), abs(current.a - a)))
    return if (moved < COLOR_STEP) current else Color(r, g, b, a)
}

/** A quarter of an 8-bit display step: too small to see, large enough to skip most frames' writes. */
private const val COLOR_STEP = 1f / 1024f
private const val NOON = 0.5f
private const val TURN = (2.0 * PI).toFloat()
private const val RADIANS_PER_DEGREE = (PI / 180.0).toFloat()
