/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.NumberRange
import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.scene.document.SceneComponentCatalog
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A `@PropertyRange` is a promise that a value outside it is invalid. For every one on a registered
 * component this loads a value just outside the range and expects the component's own `validate()`
 * to report something it did not report before, so an annotation cannot drift from the rule that
 * enforces it.
 */
class RangeAnnotationsAreEnforcedTest {
    /**
     * Made on first use, after the component kits are installed: the serializers module is a snapshot, so a
     * `Json` built earlier cannot decode a component registered later, and this test would depend on running
     * after another that installs them.
     */
    private val json by lazy {
        installEveryComponentKit()
        SceneSerializers.createJson()
    }

    /** Every property with a range, wherever it is (a list element is found so [withValue] reports it, not skips it). */
    private fun constrainedProperties(): List<SchemaNode> {
        installEveryComponentKit()
        return SceneComponentCatalog.schemas()
            .flatMap { (id, schema) -> descendantsOf(id, schema) }
            .filter { it.schema.constraints.range != null }
    }

    /** Values just outside [range]: on its excluded edge, or one past an included one. */
    private fun outside(range: NumberRange): List<Double> = buildList {
        range.min?.let { add(if (range.exclusiveMin) it else it - 1.0) }
        range.max?.let { add(if (range.exclusiveMax) it else it + 1.0) }
    }

    private fun number(value: Double, kind: PropertyKind): JsonElement =
        if (kind == PropertyKind.Int) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

    /** [root] with the property at [steps] set to [value], leaving every other property as it was. */
    private fun withValue(root: JsonObject, steps: List<String>, value: JsonElement): JsonObject {
        // Map element paths use [] in the schema walk. A seed supplies a concrete named entry.
        val head = if (steps.first() == "[]") {
            root.keys.firstOrNull() ?: error("A range inside an empty map needs a seeded entry: ${steps.joinToString(".")}")
        } else {
            steps.first()
        }
        if (steps.size == 1) return JsonObject(root + (head to value))
        val inner = root[head] as? JsonObject
            ?: error("`$head` has no default object to set `${steps.drop(1).joinToString(".")}` inside; give this test a seed")
        return JsonObject(root + (head to withValue(inner, steps.drop(1), value)))
    }

    private fun issuesFor(component: String, fields: JsonObject): Set<String> {
        val node = json.decodeFromJsonElement(
            SceneNode.serializer(),
            JsonObject(mapOf("components" to JsonArray(listOf(JsonObject(fields + ("component" to JsonPrimitive(component))))))),
        )
        return node.components.single().validate("test").map { it.message }.toSet()
    }

    private val componentSeeds: Map<String, (JsonObject) -> JsonObject> = mapOf(
        "tilemap" to { base ->
            JsonObject(base + mapOf("width" to JsonPrimitive(1), "height" to JsonPrimitive(1), "tiles" to JsonArray(listOf(JsonPrimitive(0)))))
        },
        "camera" to { base ->
            JsonObject(
                base + mapOf(
                    "projection" to JsonPrimitive("orthographic"),
                    "viewport" to JsonObject(mapOf("width" to JsonPrimitive(16f), "height" to JsonPrimitive(9f))),
                ),
            )
        },
        "keyframe_animation" to { base -> JsonObject(base + ("duration" to JsonPrimitive(1.0f))) },
        "particle_emitter" to { base -> JsonObject(base + ("texture" to JsonPrimitive("particle.png"))) },
        "canvas_element" to { base ->
            val image = JsonObject(mapOf("path" to JsonPrimitive("frame.png")))
            val empty = JsonObject(emptyMap())
            val state = JsonObject(mapOf("image" to image))
            JsonObject(
                base + (
                    "style" to JsonObject(
                        mapOf("image" to image, "fillImage" to image, "shadow" to empty, "hovered" to state, "pressed" to state),
                    )
                    ),
            )
        },
        "sprite_clips" to { base -> JsonObject(base + ("clips" to JsonObject(mapOf("idle" to JsonObject(emptyMap()))))) },
    )

    @Test
    fun everyRangeRejectsAValueJustOutsideIt() {
        val failures = constrainedProperties().flatMap { property ->
            val range = checkNotNull(property.schema.constraints.range)
            val defaultDoc = property.component.let { id -> SceneComponentCatalog.schema(id)?.default as? JsonObject }
                ?: error("${property.component} has no default document to start from; give this test a seed")
            val base = componentSeeds[property.component]?.invoke(defaultDoc) ?: defaultDoc
            val before = issuesFor(property.component, base)
            outside(range).mapNotNull { value ->
                val patched = withValue(base, property.steps, number(value, property.schema.kind))
                val added = issuesFor(property.component, patched) - before
                if (added.isEmpty()) "${property.component}.${property.steps.joinToString(".")} = $value is outside $range but validate() reports nothing new" else null
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theCheckSeesTheFirstBatch() {
        val paths = constrainedProperties().map { "${it.component}.${it.steps.joinToString(".")}" }

        assertTrue(
            paths.containsAll(expectedAnnotatedPaths),
            "the annotations this test exists to check were not found: $paths",
        )
    }

    private companion object {
        val expectedAnnotatedPaths: List<String> = listOf(
            "camera.near",
            "camera.fovYDegrees",
            "light.shadowDistance",
            "tone_mapping.exposure",
            "spin_control.speed",
            "camera_rig.flySpeed",
            "movement_control.speed",
            "movement_control.runSpeed",
            "movement_control.turnSpeed",
            "character_controller.radius",
            "character_controller.halfHeight",
            "character_controller.stepHeight",
            "character_controller.jumpSpeed",
            "navigation.cellSize",
            "patrol.dwellSeconds",
            "patrol.speed",
            "chase.speed",
            "flee.panicRadius",
            "flee.speed",
            "day_cycle.dayLengthSeconds",
            "day_cycle.time",
            "day_cycle.noonElevationDegrees",
            "canvas_element.width",
            "canvas_element.height",
            "canvas_element.fontSize",
            "canvas_element.value",
            "canvas_element.style.image.sliceLeft",
            "canvas_element.style.fillImage.regionWidth",
            "canvas_element.style.cornerRadius",
            "canvas_element.style.alpha",
            "canvas_element.style.shadow.blur",
            "canvas_element.style.pressed.alpha",
            "pbr_material.metallic",
            "pbr_material.roughness",
            "pbr_material.alphaCutoff",
            "physics_body.layer",
            "locomotion_animation.airborneAbove",
            "locomotion_animation.crossFade",
            "keyframe_animation.duration",
            "texture_animation.columns",
            "texture_animation.rows",
            "texture_animation.framesPerSecond",
            "texture_clips.columns",
            "texture_clips.rows",
            "terrain.width",
            "terrain.depth",
            "terrain.scaleX",
            "terrain.scaleY",
            "terrain.scaleZ",
            "particle_emitter.maxParticles",
            "particle_emitter.spawnRate",
            "particle_emitter.lifetime",
            "particle_emitter.startAlpha",
            "particle_emitter.scale",
            "particle_emitter.endScale",
            "particle_emitter.spawnRadius",
            "particle_emitter.frameCount",
            "particle_emitter.frameRate",
            "particle_emitter.turbulenceFrequency",
            "particle_emitter.stretchFactor",
            "particle_emitter.burstCount",
        )
    }
}
