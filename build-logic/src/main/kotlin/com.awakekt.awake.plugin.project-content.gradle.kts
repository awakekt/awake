/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.project.GenerateAssetsLockTask
import com.awakekt.awake.build.project.GenerateContentBundlesTask
import com.awakekt.awake.build.project.GenerateProjectIndexTask
import com.awakekt.awake.build.project.ProjectContentExtension
import com.awakekt.awake.build.project.ValidateProjectTask
import com.awakekt.awake.build.project.VerifyAssetsLockTask
import java.util.Properties
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

// base brings the lifecycle tasks, check among them. Registering a bare check instead broke any
// project whose own modules later applied the lifecycle plugin to it, as Kotlin's wasm setup does
// to the root: "Cannot add task 'check' as a task with that name already exists".
plugins {
    base
}

val projectContent = extensions.create<ProjectContentExtension>("projectContent")
projectContent.manifestFile.convention(layout.projectDirectory.file("awake.project.json"))
projectContent.assetsLockFile.convention(layout.projectDirectory.file("assets.lock.json"))
projectContent.indexOutputFile.convention(layout.buildDirectory.file("project.index.json"))
projectContent.bundlesOutputDirectory.convention(layout.buildDirectory.dir("published"))
projectContent.assetRoots.set(listOf("assets"))
projectContent.indexRoots.set(listOf("assets", "scenes", "plugins", "strings"))
projectContent.indexRootPath.convention("/project")
projectContent.indexBaseUrl.convention("files")
projectContent.bundleGrouping.convention("folder")
projectContent.bundleFolderDepth.convention(1)
projectContent.targetBundleBytes.convention(16L * 1024 * 1024)
projectContent.bundleOpaqueIds.convention(false)
projectContent.bundleUrlPrefix.convention("bundles")
// Prune only a folder the build owns; a publish folder elsewhere may still serve the last release.
projectContent.pruneStaleBundles.convention(
    projectContent.bundlesOutputDirectory.zip(layout.buildDirectory) { output, build ->
        output.asFile.normalize().startsWith(build.asFile.normalize())
    },
)
projectContent.engineVersion.convention(providers.provider {
    val resource = requireNotNull(ProjectContentExtension::class.java.getResourceAsStream("/com/awakekt/awake/build/project-content.properties")) {
        "The Awake project-content plugin is missing its version resource"
    }
    resource.use { Properties().apply { load(it) }.getProperty("version") }
})

val awakeProjectContentWorker = configurations.create("awakeProjectContentWorker") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}
val localProjectModule = rootProject.findProject(":awake:project")
if (localProjectModule != null) {
    dependencies.add(awakeProjectContentWorker.name, dependencies.project(mapOf("path" to localProjectModule.path)))
} else {
    dependencies.addProvider(awakeProjectContentWorker.name, projectContent.engineVersion.map {
        require(it.isNotBlank()) { "projectContent.engineVersion must not be blank" }
        "com.awakekt.awake:project:$it"
    })
}

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
    assetFiles.from(projectContent.assetRoots.map { roots -> roots.map(layout.projectDirectory::dir) })
}

val generateProjectIndex = tasks.register<GenerateProjectIndexTask>("generateProjectIndex") {
    group = "awake codegen"
    description = "Generate the metadata-only browser project index."
    outputFile.set(projectContent.indexOutputFile)
    indexRoots.set(projectContent.indexRoots)
    indexRootPath.set(projectContent.indexRootPath)
    baseUrl.set(projectContent.indexBaseUrl)
    indexedFiles.from(
        layout.projectDirectory.file("awake.project.json"),
        layout.projectDirectory.file("assets.lock.json"),
        projectContent.indexRoots.map { roots -> roots.map(layout.projectDirectory::dir) },
    )
}

tasks.register<GenerateContentBundlesTask>("generateContentBundles") {
    group = "awake codegen"
    description = "Pack the project index's files into content bundles and write a bundled index."
    indexFile.set(generateProjectIndex.flatMap { it.outputFile })
    // The files the loose index lists are the files this reads: one list, kept by the index task.
    indexedFiles.from(generateProjectIndex.map { it.indexedFiles })
    projectDirectory.set(layout.projectDirectory)
    outputDirectory.set(projectContent.bundlesOutputDirectory)
    grouping.set(projectContent.bundleGrouping)
    folderDepth.set(projectContent.bundleFolderDepth)
    targetBundleBytes.set(projectContent.targetBundleBytes)
    opaqueIds.set(projectContent.bundleOpaqueIds)
    pathSalt.set(projectContent.bundlePathSalt)
    bundleUrlPrefix.set(projectContent.bundleUrlPrefix)
    pruneStaleBundles.set(projectContent.pruneStaleBundles)
    workerClasspath.from(awakeProjectContentWorker)
}

val checkProjectContent = tasks.register("checkProjectContent") {
    group = "awake verification"
    description = "Validate project content, verify its asset lock, and generate its index."
    dependsOn(validateProject, verifyAssetsLock, generateProjectIndex)
}

tasks.named("check") {
    dependsOn(checkProjectContent)
}
