/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.AwakeRepositoryVerificationTask
import com.awakekt.awake.build.tasks.VerifyCapabilityLayeringTask
import com.awakekt.awake.build.tasks.VerifyPublishedArtifactsTask
import com.awakekt.awake.build.tasks.WaitForVulkanCentralTask
import org.gradle.api.artifacts.ProjectDependency

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
)

val verifyCapabilityLayering = tasks.register<VerifyCapabilityLayeringTask>("verifyCapabilityLayering") {
    group = "awake verification"
    description = "Reject a capability module whose main code depends on an awake:scene module."
    rootPath.set(layout.projectDirectory.asFile.absolutePath)
    mainSources.from(
        fileTree(layout.projectDirectory.dir("awake")) {
            include("**/src/*Main/**/*.kt", "**/src/main/**/*.kt")
            exclude("**/build/**")
        },
    )
    // The scene wrappers, the modules that compose and run whole projects, and a benchmark harness that
    // measures the scene by design.
    exemptModulePrefixes.set(listOf(":awake:scene", ":awake:project", ":awake:ecs:benchmark"))
    debt.set(capabilityLayeringDebt)
}

// Read once every module has been configured, so a dependency a convention plugin or an afterEvaluate block
// adds is seen as well as one in the build file. Plain values rather than a provider over the projects, so
// the task keeps working with the configuration cache.
gradle.projectsEvaluated {
    val declared = rootProject.allprojects
        .filter { it.path.startsWith(":awake:") }
        .associate { module ->
            module.path to module.configurations
                .associate { configuration ->
                    configuration.name to configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
                }
                .filterValues { it.isNotEmpty() }
        }
    verifyCapabilityLayering.configure { projectDependencies.set(declared) }
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

tasks.register<WaitForVulkanCentralTask>("waitForVulkanCentral") {
    group = "awake release"
    description = "Wait for a Vulkan release's metadata and platform files to be served by Maven Central."
}
