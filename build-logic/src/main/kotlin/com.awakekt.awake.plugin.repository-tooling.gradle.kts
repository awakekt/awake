/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.AwakeRepositoryVerificationTask
import com.awakekt.awake.build.tasks.VerifyCapabilityLayeringTask
import com.awakekt.awake.build.tasks.VerifyHeadlessRuntimeTask
import com.awakekt.awake.build.tasks.VerifyPublishedArtifactsTask
import com.awakekt.awake.build.tasks.WaitForVulkanCentralTask
import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("com.awakekt.awake.plugin.release-cut")
}

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
}

val verifyHeadlessRuntime = tasks.register<VerifyHeadlessRuntimeTask>("verifyHeadlessRuntime") {
    group = "awake verification"
    description = "Reject a GPU backend or window module on the path of a project played with no renderer."
    // What a game server or CI runner loads to play a project; the host passes backends in.
    modules.set(listOf(":awake:project", ":awake:project:runtime"))
    forbiddenModulePrefixes.set(listOf(":awake:backend", ":awake:engine:window"))
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
    verifyHeadlessRuntime.configure { projectDependencies.set(declared) }
}

tasks.register<AwakeRepositoryVerificationTask>("awakeVerify") {
    group = "awake verification"
    description = "Run Awake's repository, documentation, publication, and ownership gates."
    dependsOn(verifyCapabilityLayering, verifyHeadlessRuntime)
}

tasks.register<VerifyPublishedArtifactsTask>("verifyPublishedArtifacts") {
    group = "awake release"
    description = "Verify Maven-local POM metadata and published native JAR contents."
}

tasks.register<WaitForVulkanCentralTask>("waitForVulkanCentral") {
    group = "awake release"
    description = "Wait for a Vulkan release's metadata and platform files to be served by Maven Central."
}
