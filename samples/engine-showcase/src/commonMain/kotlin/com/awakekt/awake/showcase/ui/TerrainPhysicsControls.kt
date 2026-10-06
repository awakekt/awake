/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.runtime.LocalWorld
import com.awakekt.awake.showcase.examples.PropShapeKind
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

private val GAP = 6.dp
private val TABS_WIDTH = 220.dp

/** Controls for spawning dynamic physics props and toggling camera modes in heightfield terrain. */
context(_: Composer)
internal fun TerrainPhysicsControls() {
    val world = TerrainPhysicsExampleDriver.activeWorld ?: runCatching { LocalWorld.current }.getOrNull()

    CameraModeTabs(world)
    PropSpawnerButtons(world)
    Toggle(
        label = "Random Impulses",
        tag = ShowcaseDebugTags.PROP_IMPULSE,
        checked = TerrainPhysicsExampleDriver.applyImpulsesOnSpawn,
        onChange = { TerrainPhysicsExampleDriver.applyImpulsesOnSpawn = it },
    )
    ResetPropsButton(world)
}

context(_: Composer)
private fun CameraModeTabs(world: com.awakekt.awake.ecs.World?) {
    ShadcnText("Camera Mode", modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    ShadcnTabs(
        selectedValue = TerrainPhysicsExampleDriver.cameraMode.name,
        onSelectedChange = { name ->
            CameraMode.entries.find { it.name == name }?.let { mode ->
                if (world != null) {
                    TerrainPhysicsExampleDriver.setCameraMode(world, mode)
                } else {
                    TerrainPhysicsExampleDriver.cameraMode = mode
                }
            }
        },
        modifier = Modifier.width(TABS_WIDTH).padding(top = GAP),
    ) {
        tab(value = CameraMode.ThirdPerson.name, label = "Orbit", tag = "${ShowcaseDebugTags.CAMERA_MODE}-orbit")
        tab(value = CameraMode.FreeFly.name, label = "Free-Fly", tag = "${ShowcaseDebugTags.CAMERA_MODE}-freefly")
    }
}

context(_: Composer)
private fun PropSpawnerButtons(world: com.awakekt.awake.ecs.World?) {
    ShadcnText("Spawn Dynamic Props", modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = GAP),
    ) {
        ShadcnButton(
            label = "Box",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            modifier = Modifier.testTag(ShowcaseDebugTags.PROP_SPAWN_BOX),
            onClick = { world?.let { TerrainPhysicsExampleDriver.spawnProp(it, PropShapeKind.Box) } },
        )
        Spacer(Modifier.width(GAP))
        ShadcnButton(
            label = "Sphere",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            modifier = Modifier.testTag(ShowcaseDebugTags.PROP_SPAWN_SPHERE),
            onClick = { world?.let { TerrainPhysicsExampleDriver.spawnProp(it, PropShapeKind.Sphere) } },
        )
        Spacer(Modifier.width(GAP))
        ShadcnButton(
            label = "Wedge",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            modifier = Modifier.testTag(ShowcaseDebugTags.PROP_SPAWN_WEDGE),
            onClick = { world?.let { TerrainPhysicsExampleDriver.spawnProp(it, PropShapeKind.Wedge) } },
        )
    }
}

context(_: Composer)
private fun ResetPropsButton(world: com.awakekt.awake.ecs.World?) {
    val countText = if (TerrainPhysicsExampleDriver.spawnedCount > 0) {
        "Reset Props (${TerrainPhysicsExampleDriver.spawnedCount} spawned)"
    } else {
        "Reset Props"
    }

    ShadcnButton(
        label = countText,
        variant = ShadcnButtonVariant.Secondary,
        size = ShadcnButtonSizeVariant.Sm,
        modifier = Modifier.fillMaxWidth().padding(top = GAP).testTag(ShowcaseDebugTags.PROP_RESET),
        onClick = { world?.let { TerrainPhysicsExampleDriver.resetProps(it) } },
    )
}
