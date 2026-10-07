/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.navigation.NavigationGrid
import com.awakekt.awake.scene.navigation.SceneNavigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A scene's navigation is authored data: rows of cells a person can write and read. These cover the
 * ways that data can be wrong, that it becomes the grid agents route over, that it survives the round
 * trip, and that a behaviour arrives with the request it needs.
 */
class SceneNavigationTest {
    private fun registry() = SceneComponentRegistry().registerAiBehaviors()

    // --- the component

    @Test
    fun aWellFormedGridHasNoProblems() {
        assertEquals(emptyList(), SceneNavigation(rows = listOf("..#", "...")).validate("n").map { it.message })
    }

    @Test
    fun aGridWithoutRowsIsRefused() {
        assertTrue(SceneNavigation(rows = emptyList()).validate("n").any { "at least one row" in it.message })
    }

    @Test
    fun rowsOfDifferentWidthsAreRefusedNamingTheRow() {
        val problems = SceneNavigation(rows = listOf("...", "..", "....")).validate("n").map { it.message }

        assertTrue(problems.any { "rows[1]" in it && "2 cells wide" in it }, problems.toString())
        assertTrue(problems.any { "rows[2]" in it }, problems.toString())
    }

    @Test
    fun aCharacterThatIsNeitherWalkableNorBlockedIsRefused() {
        val problems = SceneNavigation(rows = listOf("..x")).validate("n").map { it.message }

        assertTrue(problems.any { "rows[0]" in it && "'x'" in it }, problems.toString())
    }

    @Test
    fun aCellSizeThatIsNotPositiveIsRefused() {
        assertTrue(SceneNavigation(rows = listOf("."), cellSize = 0f).validate("n").any { "cellSize" in it.message })
        assertTrue(SceneNavigation(rows = listOf("."), cellSize = -1f).validate("n").any { "cellSize" in it.message })
        assertTrue(SceneNavigation(rows = listOf("."), cellSize = Float.NaN).validate("n").any { "cellSize" in it.message })
    }

    @Test
    fun emptyRowStringsAreRefused() {
        assertTrue(SceneNavigation(rows = listOf("", "")).validate("n").any { "empty" in it.message })
    }

    // --- the grid

    @Test
    fun theGridHasTheCellsTheRowsDescribe() {
        val tile = SceneNavigation(rows = listOf(".#.", "..#"), cellSize = 2f, originX = 10f, originZ = 20f).toGrid().tile

        assertEquals(3, tile.width)
        assertEquals(2, tile.depth)
        assertEquals(2f, tile.cellSize)
        assertEquals(10f, tile.originX)
        assertEquals(20f, tile.originZ)
        assertEquals(4, tile.walkableCount)
        assertTrue(tile.isWalkable(0, 0))
        assertFalse(tile.isWalkable(1, 0), "a '#' cell is blocked")
        assertTrue(tile.isWalkable(2, 0))
        assertTrue(tile.isWalkable(0, 1))
        assertFalse(tile.isWalkable(2, 1))
    }

    @Test
    fun agentsRouteAroundABlockedWall() {
        // A wall across the middle with a gap at the east end: the only way from north to south.
        val grid = SceneNavigation(rows = listOf("......", "#####.", "......")).toGrid()

        val route = grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 2f))

        assertTrue(route.isNotEmpty(), "the gap makes the far side reachable")
        assertTrue(route.any { it.x >= 4.5f }, "the route goes through the gap at the east end: $route")
    }

    @Test
    fun aSealedWallLeavesTheFarSideUnreachable() {
        val grid = SceneNavigation(rows = listOf("......", "######", "......")).toGrid()

        assertEquals(emptyList(), grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 2f)))
    }

    @Test
    fun aMalformedGridRefusesToBuildAGridNamingTheProblem() {
        val error = assertFailsWith<IllegalArgumentException> { SceneNavigation(rows = listOf("..", ".")).toGrid() }

        assertTrue("rows[1]" in error.message.orEmpty(), error.message)
    }

    // --- the binding

    @Test
    fun navigationSurvivesTheRoundTripUnchanged() {
        val navigation = SceneNavigation(rows = listOf("..#", "..."), cellSize = 0.5f, originX = -3f, originZ = 4f)
        val document = SceneDocument(nodes = listOf(SceneNode(name = "map", components = listOf(navigation))))

        val scene = SceneLoader.instantiate(document, componentRegistry = registry())
        val exported = SceneLoader.fromWorld(scene.world, componentRegistry = registry()).nodes.single().components.single()

        assertEquals(navigation, exported)
    }

    @Test
    fun theWorldHoldsTheGridBuiltFromTheScene() {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "map", components = listOf(SceneNavigation(rows = listOf("...", "..."))))))

        val scene = SceneLoader.instantiate(document, componentRegistry = registry())

        val held = assertNotNull(scene.world.query(NavigationGrid::class).single().let { scene.world.get<NavigationGrid>(it) })
        assertEquals(3, held.grid.tile.width)
    }

    @Test
    fun navigationDecodesFromADocumentAndValidatesInIt() {
        DefaultResolversForTest.install()
        registry() // registers the AI behaviour components, navigation included, for decoding
        val document = SceneLoader.decode(
            """{ "version": 1, "nodes": [ { "name": "map", "components": [
                { "component": "navigation", "rows": ["..", ".#"], "cellSize": 2.0 } ] } ] }""",
        )

        val navigation = document.nodes.single().components.single() as SceneNavigation

        assertEquals(listOf("..", ".#"), navigation.rows)
        assertEquals(2f, navigation.cellSize)
        assertEquals(emptyList(), SceneValidator.validate(document).map { it.message })
    }

    // --- behaviours arrive with their request

    @Test
    fun everyBehaviourAttachesWithThePathRequestItAsksThrough() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(name = "player"),
                SceneNode(name = "guard", components = listOf(ScenePatrol(stops = emptyList()))),
                SceneNode(name = "hound", components = listOf(SceneChase(target = "player"))),
                SceneNode(name = "deer", components = listOf(SceneFlee(threat = "player"))),
            ),
        )

        val scene = SceneLoader.instantiate(document, componentRegistry = registry())

        listOf(PatrolBehavior::class, ChaseBehavior::class, FleeBehavior::class).forEach { type ->
            val entity = scene.world.query(type).single()
            assertNotNull(scene.world.get<PathRequest>(entity), "${type.simpleName} needs a PathRequest or it does nothing")
        }
    }

    @Test
    fun aPathRequestThatIsAlreadyThereIsKept() {
        val world = World()
        val entity = world.create()
        val existing = PathRequest()
        world.add(entity, existing)

        world.ensurePathRequest(entity)

        assertSame(existing, world.get<PathRequest>(entity))
    }

    @Test
    fun theBindingsListIncludesNavigation() {
        assertTrue(AiBehaviorBindings.bindings.any { it.schemaClass == SceneNavigation::class })
        assertTrue(AiBehaviorBindings.all.size == AiBehaviorBindings.bindings.size)
    }

    /** Core's default components, which a decoded document with a `transform` or `name` needs registered. */
    private object DefaultResolversForTest {
        fun install() = com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers.install()
    }
}
