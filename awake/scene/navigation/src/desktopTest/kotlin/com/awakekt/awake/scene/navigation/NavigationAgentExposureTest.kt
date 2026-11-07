/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.navigation.walkable.NavAgent
import com.awakekt.awake.navigation.walkable.NavPathFollower
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every tuning option of the capability types a `navigation_agent` maps onto is either set from a
 * [SceneNavigationAgent] field or listed as code-only with a reason, and every field maps to one.
 */
@OptIn(ExperimentalSerializationApi::class)
class NavigationAgentExposureTest {
    private val setFromSceneFile: Map<String, String> = mapOf(
        "radius" to "NavAgent.radius",
        "speed" to "NavigationAgent.speed",
        "arriveDistance" to "NavPathFollower.arriveDistance",
        "waypointRadius" to "NavPathFollower.waypointRadius",
        "repathInterval" to "NavPathFollower.repathInterval",
        "stuckSeconds" to "NavPathFollower.stuckSeconds",
        "stuckDistance" to "NavPathFollower.stuckDistance",
        "destinationX" to "NavPathFollower.destination",
        "destinationZ" to "NavPathFollower.destination",
    )

    private val codeOnly: Map<String, String> = mapOf(
        "NavAgent.height" to "the scene's navigation rows are baked without heights; baking from colliders is a #1426 follow-up",
        "NavAgent.stepHeight" to "as height",
        "NavAgent.maxSlopeDegrees" to "as height",
        "NavPathFollower.agent" to "built from radius",
    )

    /** Runtime state, not tuning. */
    private val state: Set<String> = setOf(
        "NavPathFollower.path",
        "NavPathFollower.status",
        "NavPathFollower.waypoint",
        "NavPathFollower.plans",
        "NavPathFollower.planDue",
        "NavPathFollower.sinceRepath",
        "NavPathFollower.sinceProgress",
        "NavPathFollower.progressX",
        "NavPathFollower.progressZ",
    )

    @Test
    fun everySchemaFieldMapsToALiveOption() {
        val fields = SceneNavigationAgent.serializer().descriptor.elementNames.toSet()
        assertEquals(fields, setFromSceneFile.keys)
    }

    @Test
    fun everyLiveOptionIsMappedOrListed() {
        val live = listOf(NavAgent::class.java, NavPathFollower::class.java).flatMap { type ->
            type.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { "${type.simpleName}.${it.name}" }
        }.toSet()
        val accounted = setFromSceneFile.values.toSet() + codeOnly.keys + state
        assertEquals(emptySet(), live - accounted, "unmapped options")
        assertEquals(emptySet(), accounted.filter { it.startsWith("Nav") && !it.startsWith("NavigationAgent") }.toSet() - live, "stale names")
    }
}
