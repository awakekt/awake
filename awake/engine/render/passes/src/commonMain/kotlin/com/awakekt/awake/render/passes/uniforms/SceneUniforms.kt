/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.putVec4
import com.awakekt.awake.render.renderer.MAX_POINT_LIGHTS
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * Packs the scene light as `[direction.xyz, shadowTexelDepthScale, color.rgb, pad]`.
 *
 * Both halves are `vec4f` rather than `vec3f` on the WGSL side specifically to sidestep vec3's
 * implicit 16-byte alignment padding inside a uniform-buffer struct -- see `triangle.wgsl`'s own
 * `Uniforms` doc comment. That leaves two otherwise-wasted floats, and the direction's carries
 * something real on a backend that casts shadows.
 *
 * @param light This frame's scene light.
 * @param shadowTexelDepthScale Rides in `direction.w`, which the shadow-sampling shader reads as
 * its depth bias scale. Defaults to `0f` for a backend that samples no shadow map -- the shader
 * variants without shadows never read the slot.
 */
fun sceneLightFloats(light: SceneLight, shadowTexelDepthScale: Float = 0f): FloatArray =
    floatArrayOf(
        light.direction.x,
        light.direction.y,
        light.direction.z,
        shadowTexelDepthScale,
        light.color.x,
        light.color.y,
        light.color.z,
        0f,
    )

/**
 * Fills [into] with [light]'s point-light slots: positions with range in `w`, then colours.
 *
 * Writes into a caller-owned buffer rather than returning lists. This runs once per frame from
 * inside the render path, and the list-returning version it replaces allocated an `ArrayList`, two
 * mapped `List`s and `2N` `Pair`s every frame -- the per-frame allocation `skills/awake-core-math`
 * rules out, in a function the rule names directly.
 *
 * Slots past [light]'s light count keep whatever [into] held, so the buffer is zeroed first: an
 * unwritten slot must read `w = 0`, which is how the shader spells "off". A stale range from last
 * frame would light a fragment from a light that is gone.
 *
 * Over [MAX_POINT_LIGHTS] lights the nearest to [eye] win, chosen by an insertion scan rather than
 * `sortedBy` so the selection itself allocates nothing. Distance is what attenuation is about to
 * divide by, so the survivors are the ones that would have contributed most.
 */
fun packPointLights(light: SceneLight, eye: Vec3f, into: FloatArray) {
    into.fill(0f)
    if (light.points.isEmpty()) return
    val colorBase = MAX_POINT_LIGHTS * VEC4
    var filled = 0
    var farthest = -1f
    var farthestSlot = 0
    light.points.forEach { point ->
        val distance = point.position.squaredDistanceTo(eye)
        val slot = when {
            filled < MAX_POINT_LIGHTS -> filled++
            distance < farthest -> farthestSlot
            else -> return@forEach
        }
        into.putVec4(slot * VEC4, point.position, point.range)
        into.putVec4(colorBase + slot * VEC4, point.color)
        // Re-scan for the new farthest only when the set changed; MAX_POINT_LIGHTS is 4, so this
        // is cheaper than keeping a sorted structure and allocates nothing either way.
        farthest = -1f
        for (i in 0 until filled) {
            val p = i * VEC4
            val d = squaredDistance(into[p], into[p + 1], into[p + 2], eye)
            if (d > farthest) {
                farthest = d
                farthestSlot = i
            }
        }
    }
}

private fun squaredDistance(x: Float, y: Float, z: Float, eye: Vec3f): Float {
    val dx = x - eye.x
    val dy = y - eye.y
    val dz = z - eye.z
    return dx * dx + dy * dy + dz * dz
}

private const val VEC4 = 4

/**
 * Packs `[fogColor.rgb, fogDensity]` -- the density rides in the colour's alpha slot, which the
 * shader never reads as alpha.
 *
 * @param fogColor Colour distant geometry blends toward.
 * @param fogDensity Exponential density; `0f` means no fog.
 */
fun fogUniformFloats(fogColor: Color, fogDensity: Float): FloatArray =
    floatArrayOf(fogColor.r, fogColor.g, fogColor.b, fogDensity)

/**
 * Packs the camera's world position as `[x, y, z, pad]`.
 *
 * `vec4f` not `vec3f` for the same std140 reason as [sceneLightFloats]; the shader reads `.xyz`.
 *
 * @param eye Camera position in world space.
 */
fun cameraPositionFloats(eye: Vec3f): FloatArray = floatArrayOf(eye.x, eye.y, eye.z, 0f)

/**
 * One frame's lighting, ready to write: the directional light's eight floats and the point-light
 * slots.
 *
 * A bundle rather than two threaded parameters. The backends pass light data down through six
 * call layers each; adding a second parameter beside `lightFloats` would mean six signatures per
 * backend that can disagree about whether they got both. [writeTo] also keeps the four fields
 * written together and in layout order, which is the ordering `UniformWriter` enforces anyway.
 */
/**
 * One frame's lighting, ready to write: the directional light's eight floats and the point-light
 * slots as one flat block, positions then colours.
 *
 * A bundle rather than two threaded parameters. The backends pass light data down through six call
 * layers each; a second parameter beside `lightFloats` would mean six signatures per backend that
 * can disagree about whether they got both.
 *
 * Not reused across frames -- [sceneLightUniforms] builds one per frame, which is one object, not
 * one per light. The buffers inside it are what the allocation rule cares about, and those are
 * sized once from [MAX_POINT_LIGHTS].
 */
class SceneLightUniforms(
    /** The directional light's eight floats -- `lightDirection` then `lightColor`. Exposed for
     * the one caller that uses them as a whole uniform block rather than as fields of a larger
     * one: Vulkan's unshadowed primary path, whose shader is the light block and nothing else. */
    val directional: FloatArray,
    private val pointSlots: FloatArray,
) {
    /**
     * Writes all four light fields, in layout order -- for a layout whose shader declares the
     * point-light slot arrays. Today that is the two PBR paths, `textured` and `lit_shadow`.
     */
    fun writeTo(writer: UniformWriter): UniformWriter = writer
        .put(directional, UniformFields.LightDirection, UniformFields.LightColor)
        .put(pointSlots, UniformFields.PointLightPositions, UniformFields.PointLightColors)

    /**
     * Writes the directional light only, for a shader with no point-light slots.
     *
     * `triangle`, `instanced` and `skinned_instanced` deliberately skip point lights: they are the
     * non-PBR and instanced-crowd paths, where four extra per-fragment lights buy little and cost
     * most. A layout must match its shader exactly -- writing four fields into a two-field block is
     * what [UniformWriter] exists to catch, so the choice is made here rather than left to whether
     * a call site remembered.
     */
    fun writeDirectionalTo(writer: UniformWriter): UniformWriter =
        writer.put(directional, UniformFields.LightDirection, UniformFields.LightColor)
}

/** [SceneLightUniforms] for [light] viewed from [eye], with [shadowTexelDepthScale] riding in
 * `lightDirection.w` as the lit-shadow shader expects. */
fun sceneLightUniforms(
    light: SceneLight,
    eye: Vec3f,
    shadowTexelDepthScale: Float = 0f,
): SceneLightUniforms {
    val slots = FloatArray(MAX_POINT_LIGHTS * 4 * 2)
    packPointLights(light, eye, slots)
    return SceneLightUniforms(sceneLightFloats(light, shadowTexelDepthScale), slots)
}
