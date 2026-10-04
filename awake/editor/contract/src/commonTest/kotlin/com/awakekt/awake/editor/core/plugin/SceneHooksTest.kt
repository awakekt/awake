/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneHooksTest {
    @Test
    fun aPluginContributesTheSceneHooksUnderTheirOwnKinds() {
        val providers = ProviderRegistry()

        PluginRegistry(providers).install(MarkerPlugin())

        assertEquals(
            listOf(EditorProviderKind.ViewportTool, EditorProviderKind.SceneSystems, EditorProviderKind.Component),
            providers.all.map { it.kind },
        )
    }

    @Test
    fun aViewportToolPlacesOnThePointerRayAndRecordsOneUndoStepPerDrag() {
        val world = World()
        val marker = world.create().also { world.add(it, Marker(Vec3f(5f, 0f, 5f))) }
        val history = StackHistory()
        val tool = MarkerTool()
        val frame = { down: Boolean -> viewport(world, SingleSelection(marker), history, isPointerDown = down) }

        assertEquals(1, tool.onHover(frame(false)).size)
        assertTrue(tool.onPointerDrag(frame(true)))
        assertTrue(tool.onPointerDrag(frame(true)))
        tool.onPointerUp(frame(false))

        val placed = world.get<Marker>(marker)!!.position
        assertTrue(abs(placed.x) < 1e-3f && abs(placed.z) < 1e-3f, "the centre pixel's ray meets the ground at the origin")
        assertEquals(listOf("Place marker"), history.labels)

        history.undo()

        assertEquals(Vec3f(5f, 0f, 5f), world.get<Marker>(marker)!!.position)
    }

    @Test
    fun aToolOnlyTakesThePointerWhenItApplies() {
        val world = World()
        val bare = world.create()

        assertFalse(MarkerTool().isApplicable(viewport(world, SingleSelection(bare), StackHistory(), isPointerDown = true)))
    }

    @Test
    fun sceneSystemsAreFreshEachTimeTheHostBuildsItsLoop() {
        val provider = MarkerPlugin().systems
        val world = World()
        val marker = world.create().also { world.add(it, Marker(Vec3f(0f, 0f, 0f))) }

        val first = provider.createSystems()
        first.forEach { it.update(world, delta = 0.5f) }

        assertEquals(0.5f, world.get<Marker>(marker)!!.position.y)
        assertTrue(first.single() !== provider.createSystems().single())
    }

    @Test
    fun anInspectorWritesThroughTheHostsFields() {
        val world = World()
        val marker = world.create().also { world.add(it, Marker(Vec3f(1f, 2f, 3f), size = 1f)) }
        val scope = RecordingFields(nextScalar = 4f)
        val inspector = MarkerPlugin().inspector

        inspector.fields(scope, world, marker)

        assertEquals(Marker::class, inspector.componentType)
        assertEquals(listOf("Position", "Size"), scope.labels)
        assertEquals(4f, world.get<Marker>(marker)!!.size)
    }
}

private class Marker(val position: Vec3f, var size: Float = 1f)

private class MarkerPlugin : EditorPlugin {
    override val metadata = PluginMetadata(PluginId("com.example.marker"), "Marker", "1.0.0", PluginApi.currentVersion)

    val tool = MarkerTool()

    val systems = object : SceneSystemsProvider {
        override val metadata = ProviderMetadata(ProviderId("marker.systems"), "Marker bob")

        override fun createSystems(): List<System> = listOf(BobSystem())
    }

    val inspector = object : ComponentInspectorProvider {
        override val metadata = ProviderMetadata(ProviderId("marker.inspector"), "Marker")
        override val componentType = Marker::class

        override fun fields(scope: InspectorFieldScope, world: World, entity: Entity) {
            val marker = world.get<Marker>(entity) ?: return
            scope.vector("Position", marker.position)
            scope.scalar("Size", marker.size) { marker.size = it }
        }
    }

    override fun createProviders(): List<EditorProvider> = listOf(tool, systems, inspector)
}

/** Moves the selected marker to where the pointer meets the ground, as one undo step per drag. */
private class MarkerTool : ViewportToolProvider {
    override val metadata = ProviderMetadata(ProviderId("marker.tool"), "Place marker")
    private var before: Vec3f? = null

    override fun isApplicable(context: ViewportContext): Boolean =
        context.selection.primary?.let { context.world.get<Marker>(it) } != null

    override fun onHover(context: ViewportContext): List<OverlayLine> {
        val hit = context.pointerRay()?.intersectGroundPlane() ?: return emptyList()
        return listOf(OverlayLine(hit, Vec3f(hit.x, hit.y + 1f, hit.z), Color(1f, 0f, 0f)))
    }

    override fun onPointerDrag(context: ViewportContext): Boolean {
        val marker = context.selection.primary?.let { context.world.get<Marker>(it) }
        val hit = context.pointerRay()?.intersectGroundPlane()
        if (marker == null || hit == null) return false
        if (before == null) before = marker.position.copy()
        marker.position.set(hit.x, hit.y, hit.z)
        return true
    }

    override fun onPointerUp(context: ViewportContext) {
        val marker = context.selection.primary?.let { context.world.get<Marker>(it) } ?: return
        val start = before ?: return
        before = null
        val end = marker.position.copy()
        context.history.record(
            object : EditCommand {
                override val label = "Place marker"

                override fun apply() {
                    marker.position.set(end.x, end.y, end.z)
                }

                override fun revert() {
                    marker.position.set(start.x, start.y, start.z)
                }
            },
        )
    }
}

private class BobSystem : System {
    override fun update(world: World, delta: Float) {
        world.query(Marker::class).forEach { entity -> world.get<Marker>(entity)!!.position.y += delta }
    }
}

private class SingleSelection(override val primary: Entity?) : SceneSelection {
    override val entities: Set<Entity> get() = setOfNotNull(primary)

    override fun select(entity: Entity?) = Unit
}

private class StackHistory : EditHistory {
    private val done = ArrayDeque<EditCommand>()
    val labels: List<String> get() = done.map { it.label }

    override fun execute(command: EditCommand) {
        command.apply()
        record(command)
    }

    override fun record(command: EditCommand) {
        done.addLast(command)
    }

    fun undo() {
        done.removeLast().revert()
    }
}

private class RecordingFields(private val nextScalar: Float) : InspectorFieldScope {
    val labels = mutableListOf<String>()

    override fun scalar(label: String, value: Float, write: (Float) -> Unit) {
        labels += label
        write(nextScalar)
    }

    override fun toggle(label: String, value: Boolean, write: (Boolean) -> Unit) {
        labels += label
    }

    override fun <T : Enum<T>> options(label: String, value: T, cases: List<T>, write: (T) -> Unit) {
        labels += label
    }

    override fun vector(label: String, value: Vec3f) {
        labels += label
    }
}

/** A 100×100 viewport looking down at the origin, with the pointer on its centre pixel. */
private fun viewport(world: World, selection: SceneSelection, history: EditHistory, isPointerDown: Boolean): ViewportContext {
    val camera = Lens(eye = Vec3f(0f, 10f, 10f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
    return ViewportContext(
        world = world,
        selection = selection,
        history = history,
        camera = camera,
        viewProjection = camera.viewProjectionMatrix(aspect = 1f, clipSpace = ClipSpace.Vulkan),
        clipSpace = ClipSpace.Vulkan,
        viewportWidth = 100f,
        viewportHeight = 100f,
        pointerX = 50f,
        pointerY = 50f,
        isPointerDown = isPointerDown,
        keysDown = emptySet(),
    )
}
