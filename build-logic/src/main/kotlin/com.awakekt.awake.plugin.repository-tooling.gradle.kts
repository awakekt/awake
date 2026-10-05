/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.AwakeRepositoryVerificationTask
import com.awakekt.awake.build.tasks.VerifyCapabilityLayeringTask
import com.awakekt.awake.build.tasks.VerifyPublishedArtifactsTask

plugins {
    id("com.awakekt.awake.plugin.release-cut")
}

/**
 * Modules that depend on an `awake:scene` module from main code and were there before the rule. Each is a
 * capability, or a binding that sits outside `awake/scene/`, still to be separated. **Shrink this list,
 * never grow it:** a new capability goes in a module of its own (see `awake/scene/README.md`), and the
 * verification task fails on an entry that no longer applies so it gets deleted.
 */
val capabilityLayeringDebt = setOf(
    // Patrol, chase and flee bind into the scene document and the scene's Transform: a scene binding that
    // lives outside awake/scene/. The behaviour logic belongs in awake:ai, its binding in awake:scene:ai.
    ":awake:ai:behavior",
    // Terrain layer kit built on scene3d's terrain components.
    ":awake:kit:terrain-layers",
    // Grid navigation that depends on scene-core and scene:world.
    ":awake:navigation",
)

val verifyCapabilityLayering = tasks.register<VerifyCapabilityLayeringTask>("verifyCapabilityLayering") {
    group = "awake verification"
    description = "Reject a capability module that depends on an awake:scene module in main code."
    rootPath.set(layout.projectDirectory.asFile.absolutePath)
    buildFiles.from(
        fileTree(layout.projectDirectory.dir("awake")) {
            include("**/build.gradle.kts")
            exclude("**/build/**")
        },
    )
    // The scene wrappers, the modules that compose and run whole projects, and a benchmark harness that
    // measures the scene by design.
    exemptModulePrefixes.set(listOf(":awake:scene", ":awake:project", ":awake:ecs:benchmark"))
    debt.set(capabilityLayeringDebt)
}

tasks.register<AwakeRepositoryVerificationTask>("awakeVerify") {
    group = "awake verification"
    description = "Run Awake's repository, documentation, publication, and ownership gates."
    dependsOn(verifyCapabilityLayering)
}

tasks.register<VerifyPublishedArtifactsTask>("verifyPublishedArtifacts") {
    group = "awake release"
    description = "Verify Maven-local POM metadata and published native JAR contents."
}
