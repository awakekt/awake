/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.canvas.CanvasAnchor
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Game UI" guide shows one HUD as a scene document and in the scene DSL, and a system that
 * reacts to its button. Both forms are included from here, so this test keeps them equal.
 */
class GameUiDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameHud() {
        val world = World()
        // --8<-- [start:hud-dsl]
        world.scene {
            entity("health_bar") {
                configure(::CanvasElement) {
                    kind = CanvasElementKind.Bar
                    anchor = CanvasAnchor.TopLeft
                    width = 240f
                    height = 16f
                    value = 0.75f
                    color = "#E5484D"
                    background = "#00000080"
                }
            }
            entity("score") {
                configure(::CanvasElement) {
                    kind = CanvasElementKind.Text
                    anchor = CanvasAnchor.TopRight
                    width = 160f
                    text = "Score 0"
                }
            }
            entity("jump_button") {
                configure(::CanvasElement) {
                    kind = CanvasElementKind.Button
                    anchor = CanvasAnchor.BottomRight
                    width = 120f
                    height = 56f
                    text = "Jump"
                    action = "jump"
                    background = "#2563EB"
                }
            }
        }
        // --8<-- [end:hud-dsl]

        val fromDocument = loadHud()

        assertEquals(fromDocument.savedElements(), world.savedElements())
        assertEquals(setOf("health_bar", "score", "jump_button"), world.savedElements().keys)
    }

    @Test
    fun aTapOnTheJumpButtonReachesTheGameOnce() {
        val world = loadHud()
        var jumps = 0
        val jumpSystem = JumpButtonSystem(world.named("jump_button")) { jumps++ }

        // --8<-- [start:own-host]
        val host = ComposeHost()
        host.frame(FrameInput(viewportWidth = 800, viewportHeight = 600)) { SceneCanvas(world) }
        // --8<-- [end:own-host]

        // BottomRight, 16 dp in from each edge, 120 x 56: its centre is (724, 556).
        host.frame(FrameInput(800, 600, pointerX = 724, pointerY = 556, pointerDown = true, pointerPressed = true)) {
            SceneCanvas(world)
        }
        host.frame(FrameInput(800, 600, pointerX = 724, pointerY = 556, pointerReleased = true)) {
            SceneCanvas(world)
        }
        jumpSystem.update(world, 1f / 60f)
        jumpSystem.update(world, 1f / 60f)

        assertEquals(1, jumps, "one tap is one jump, however many frames read it")
    }

    private fun loadHud(): World {
        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "ui/hud.scene.json").readText())
        return SceneLoader.instantiate(document).world
    }

    private fun World.named(name: String): Entity =
        query(Name::class).single { get<Name>(it)?.value == name }

    private fun World.savedElements(): Map<String, SceneCanvasElement> = buildMap {
        family<CanvasElement>().forEach { entity, element ->
            put(requireNotNull(get<Name>(entity)).value, element.toSceneComponent())
        }
    }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}

// --8<-- [start:jump-system]
class JumpButtonSystem(private val button: Entity, private val jump: () -> Unit) : System {
    override fun update(world: World, delta: Float) {
        if (world.get<CanvasElement>(button)?.consumePress() == true) jump()
    }
}
// --8<-- [end:jump-system]
