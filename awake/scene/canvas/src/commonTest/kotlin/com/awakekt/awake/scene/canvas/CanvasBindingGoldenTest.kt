/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What each canvas binding saves, byte for byte, and a prefab saved in the current form, recorded
 * from the hand-written bindings before they were generated, so the generated ones are shown to read
 * and write exactly what those did.
 */
class CanvasBindingGoldenTest {
    @Test
    fun eachBindingSavesAndLoadsAsTheHandWrittenOneDid() {
        assertEquals(SAVED.keys, goldenCases.keys)
        for ((name, case) in goldenCases) {
            val expected = SAVED.getValue(name)
            assertEquals(expected, case.encode(), "$name as recorded")
            assertRoundTrips(expected, case.binding, name)
        }
    }

    @Test
    fun aPrefabSavesAsTheHandWrittenBindingsSavedIt() {
        val older = CanvasPrefabs.fromJson(CanvasComponentsSceneTest.OLDER_PREFAB)
        val saved = older.copy(root = loadCanvas(listOf(older.root)).saveCanvas().single())
        assertEquals(PREFAB, CanvasPrefabs.toJson(saved))

        val current = CanvasPrefabs.fromJson(PREFAB)
        assertEquals(PREFAB, CanvasPrefabs.toJson(current.copy(root = loadCanvas(listOf(current.root)).saveCanvas().single())))
    }
}

private val json = SceneSerializers.createJson()

@Suppress("UNCHECKED_CAST") // A case's binding and saved form share their schema type.
private fun GoldenCase<*>.encode(): String = json.encodeToString((binding.serializer as kotlinx.serialization.KSerializer<SceneComponent>), saved)

private class NoLinks(override val world: World) : SceneResolutionContext {
    override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

    override fun recordRequest(request: Any) = Unit
}

/** Asserts [saved] loads through [binding] and saves back as exactly [saved]. */
private fun <S : SceneComponent> assertRoundTrips(saved: String, binding: SceneComponentBinding<*, S>, name: String) {
    val serializer = checkNotNull(binding.serializer)
    val world = World()
    val entity = world.create()
    binding.attach(world, entity, json.decodeFromString(serializer, saved), NoLinks(world))
    assertEquals(saved, json.encodeToString(serializer, checkNotNull(binding.exportFrom(world, entity))), "$name saved back")
}

private val SAVED: Map<String, String> = mapOf(
    "rect" to """{"anchor":"BottomRight","offsetX":2.0,"offsetY":4.0,"width":64.0,"height":32.0,"order":3,"visible":false,"touchOnly":true,"safeArea":false,"grow":0.5,"elementId":"hp","controlId":"jump"}""",
    "text" to """{"text":"Hi","textKey":"hud.hi","textArgs":{"n":"1"},"fontSize":18.0,"font":"mono","textAlign":"Center","links":false}""",
    "appearance" to """{"color":"#ff0000","background":"#00000080","style":{"background":"#123456","fillImageMode":"Cut"},"skin":{}}""",
    "layout" to """{"layout":{"direction":"Column","wrap":false,"gap":4.0,"padding":0.0,"justify":"Start","align":"Start"}}""",
    "cluster" to """{"cluster":{"pivotX":1.0,"pivotY":1.0,"radius":48.0,"startAngle":180.0,"spacing":45.0,"primaryAtPivot":true}}""",
    "bind" to """{"bind":{"node":"Player","value":"hp","max":"","text":"","textArgs":{},"cooldown":"","cooldownMax":"","charges":"","enabled":""}}""",
    "follow" to """{"node":"Enemy","offset":{"x":0.0,"y":2.0,"z":0.5},"bounds":true,"outset":4.0,"rules":{"cullOffScreen":true,"maxDistance":32.0,"occlusion":false,"group":"","priority":0.0,"pinToEdge":false,"edgeMargin":0.0}}""",
    "follow_arrow" to """{}""",
    "input" to """{"pressable":true,"draggable":true,"dropTarget":true,"wheel":true}""",
    "tooltip" to """{"text":"Tip","textArgs":{"n":"2"}}""",
    "button" to """{"action":"jump","doubleAction":"dash","settings":{"trigger":"Tap","longPressSeconds":0.5,"longPressAction":"","releaseAction":"","blockWhileCooling":true,"cooldownSeconds":0.0},"enabled":false}""",
    "bar" to """{"value":0.5,"settings":{"direction":"LeftToRight","thickness":4.0,"trailDelay":0.5,"trailSpeed":1.5,"segments":0,"segmentGap":2.0,"ticks":0,"tickColor":"#00000080","label":false}}""",
    "sweep" to """{"value":0.25,"settings":{"startAngle":90.0,"clockwise":true,"mask":"Box","thickness":0.0}}""",
    "stepper" to """{"settings":{"value":2.0,"min":1.0,"max":100.0,"step":1.0,"decimals":0,"repeatDelay":0.5,"repeatInterval":0.125}}""",
    "joystick" to """{"action":"move","settings":{"mode":"Fixed","radius":40.0,"restX":0.5,"restY":0.5,"deadZone":0.0,"response":"Linear","directions":"Analog","snapBack":true,"sprintThreshold":0.0,"sprintAction":"","idleAlpha":1.0,"idleDelay":0.0,"fadeSeconds":0.25}}""",
    "look_area" to """{"action":"look","settings":{"sensitivity":2.0,"invertX":false,"invertY":false,"pinchZoom":true,"zoomSensitivity":1.0,"turnsCamera":true,"zoomAction":""}}""",
    "text_field" to """{"mask":"*","maxLength":8,"maxLengthUnit":"CodePoints","placeholder":"Name","padding":{"horizontal":2.0,"vertical":1.0}}""",
    "view" to """{"camera":"Cam"}""",
    "image" to """{}""",
    "scaler" to """{"mode":"ConstantPixelSize","referenceWidth":640.0,"referenceHeight":360.0,"match":0.5,"pixelsPerUnit":2.0,"referenceDpi":192.0,"minScale":0.5,"maxScale":4.0,"playerScale":1.5,"snap":"HalfStep","scope":"Subtree"}""",
    "controls" to """{"touchVisibility":"Always","minTouchSize":48.0,"theme":{"preset":"MinimalFlat","colors":{},"images":{},"skin":{}},"playerLayout":{"scale":1.5,"opacity":1.0,"controls":{}}}""",
    "follow_limit" to """{"group":"enemies","max":4,"rankBy":"Priority"}""",
    "follow_occlusion" to """{"raysPerFrame":8,"reuseFrames":3,"layer":0}""",
)

private const val PREFAB: String = """{
    "guid": "c0ffee00-0000-4000-8000-0000000000aa",
    "name": "Bar",
    "root": {
        "name": "Bar",
        "transform": {
            "position": {
                "x": 0.0,
                "y": 0.0,
                "z": 0.0
            },
            "rotation": {
                "x": 0.0,
                "y": 0.0,
                "z": 0.0
            },
            "scale": {
                "x": 1.0,
                "y": 1.0,
                "z": 1.0
            }
        },
        "components": [
            {
                "component": "canvas_appearance",
                "color": "#FFFFFF",
                "background": "#202020FF",
                "style": {
                    "fillImageMode": "Cut"
                }
            },
            {
                "component": "canvas_rect",
                "anchor": "TopLeft",
                "offsetX": 16.0,
                "offsetY": 16.0,
                "width": 120.0,
                "height": 40.0,
                "order": 0,
                "visible": true,
                "touchOnly": false,
                "safeArea": true,
                "grow": 0.0,
                "elementId": "bar",
                "controlId": ""
            }
        ],
        "children": [
            {
                "name": "bar.slot-0",
                "transform": {
                    "position": {
                        "x": 0.0,
                        "y": 0.0,
                        "z": 0.0
                    },
                    "rotation": {
                        "x": 0.0,
                        "y": 0.0,
                        "z": 0.0
                    },
                    "scale": {
                        "x": 1.0,
                        "y": 1.0,
                        "z": 1.0
                    }
                },
                "components": [
                    {
                        "component": "canvas_appearance",
                        "color": "#FFD700",
                        "background": "#00000000",
                        "style": {
                            "fillImageMode": "Cut"
                        }
                    },
                    {
                        "component": "canvas_button",
                        "action": "skill.1",
                        "doubleAction": "",
                        "enabled": true
                    },
                    {
                        "component": "canvas_rect",
                        "anchor": "TopLeft",
                        "offsetX": 16.0,
                        "offsetY": 16.0,
                        "width": 32.0,
                        "height": 32.0,
                        "order": 0,
                        "visible": true,
                        "touchOnly": false,
                        "safeArea": true,
                        "grow": 0.0,
                        "elementId": "bar.slot-0",
                        "controlId": ""
                    },
                    {
                        "component": "canvas_text",
                        "text": "Q",
                        "textKey": "",
                        "textArgs": {},
                        "fontSize": 18.0,
                        "font": "",
                        "links": true
                    }
                ],
                "children": []
            }
        ]
    }
}"""
