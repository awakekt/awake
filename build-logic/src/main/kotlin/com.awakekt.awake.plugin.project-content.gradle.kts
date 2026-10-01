/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.project.GenerateAssetsLockTask
import com.awakekt.awake.build.project.GenerateProjectIndexTask
import com.awakekt.awake.build.project.ProjectContentExtension
import com.awakekt.awake.build.project.ValidateProjectTask
import com.awakekt.awake.build.project.VerifyAssetsLockTask

val projectContent = extensions.create<ProjectContentExtension>("projectContent")
projectContent.manifestFile.convention(layout.projectDirectory.file("awake.project.json"))
projectContent.assetsLockFile.convention(layout.projectDirectory.file("assets.lock.json"))
projectContent.indexOutputFile.convention(layout.buildDirectory.file("project.index.json"))
projectContent.assetRoots.set(listOf("assets"))
projectContent.indexRoots.set(listOf("assets", "scenes", "plugins"))
projectContent.indexRootPath.convention("/project")
projectContent.indexBaseUrl.convention("files")

val validateProject = tasks.register<ValidateProjectTask>("validateProject") {
    group = "awake verification"
    description = "Validate the Awake project manifest, scenes, assets, and plugins."
    manifestFile.set(projectContent.manifestFile)
    assetRoots.set(projectContent.assetRoots)
    indexRoots.set(projectContent.indexRoots)
}

val verifyAssetsLock = tasks.register<VerifyAssetsLockTask>("verifyAssetsLock") {
    group = "awake verification"
    description = "Verify every tracked asset against assets.lock.json."
    assetsLockFile.set(projectContent.assetsLockFile)
    assetRoots.set(projectContent.assetRoots)
}

tasks.register<GenerateAssetsLockTask>("generateAssetsLock") {
    group = "awake codegen"
    description = "Generate the canonical assets.lock.json."
    outputFile.set(projectContent.assetsLockFile)
    assetRoots.set(projectContent.assetRoots)
}

val generateProjectIndex = tasks.register<GenerateProjectIndexTask>("generateProjectIndex") {
    group = "awake codegen"
    description = "Generate the metadata-only browser project index."
    outputFile.set(projectContent.indexOutputFile)
    indexRoots.set(projectContent.indexRoots)
    indexRootPath.set(projectContent.indexRootPath)
    baseUrl.set(projectContent.indexBaseUrl)
}

val checkProjectContent = tasks.register("checkProjectContent") {
    group = "awake verification"
    description = "Validate project content, verify its asset lock, and generate its index."
    dependsOn(validateProject, verifyAssetsLock, generateProjectIndex)
}

val check = tasks.findByName("check")?.let { tasks.named("check") }
    ?: tasks.register("check") {
        group = "verification"
        description = "Run project-content verification."
    }
check.configure {
    dependsOn(checkProjectContent)
}
