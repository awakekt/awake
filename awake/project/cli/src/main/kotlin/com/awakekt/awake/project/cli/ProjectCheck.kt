/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.io.FileSystemAssetSource
import com.awakekt.awake.core.io.createPlatformFileSystem
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.project.AwakeEngineBuildInfo
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.ProjectContentIssue
import com.awakekt.awake.project.ProjectContentValidator
import com.awakekt.awake.project.ProjectIssueCode
import com.awakekt.awake.project.ProjectIssueSeverity
import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import com.awakekt.awake.project.runtime.checkProjectOpens
import com.awakekt.awake.scene.core.SceneTag
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import kotlinx.coroutines.runBlocking
import java.io.File

/** How much a [Finding] matters: an error fails `awake validate`, a warning doesn't. */
internal enum class Severity { Error, Warning }

/**
 * One thing `awake validate` found.
 *
 * @property severity Whether it fails the project.
 * @property message What is wrong, in a sentence.
 * @property file The project file it's in, from the project root.
 * @property path Where in [file]: a scene's node path, as Studio names it.
 * @property code The project-content code, when it has one.
 */
internal data class Finding(
    val severity: Severity,
    val message: String,
    val file: String? = null,
    val path: String? = null,
    val code: String? = null,
)

/**
 * What `awake validate` found in a project.
 *
 * @property projectId The manifest's id, or null when it couldn't be read.
 * @property sceneCount How many scene files the project has.
 * @property findings Everything found, in the order it was found.
 */
internal data class CheckReport(val projectId: String?, val sceneCount: Int, val findings: List<Finding>) {
    val errors: List<Finding> get() = findings.filter { it.severity == Severity.Error }
    val warnings: List<Finding> get() = findings.filter { it.severity == Severity.Warning }
}

/**
 * Checks a project the way a played project and Studio do: its manifest and the engine version it
 * needs, that the entry scene exists, its asset lock's pins, and that every scene decodes and validates.
 * A component nothing installed provides, and a tag the manifest doesn't list, are warnings.
 *
 * With [code], the class folders and jars of the project's own built code, it also opens the project
 * with that code, as an editor does: each plugin's `capabilityClass` loads from [code] in a class
 * loader of its own, and [checkProjectOpens] opens every scene with those capabilities. A component
 * no capability registers is then an error, and so is a capability that can't be loaded, a scene that
 * can't be opened, or one that reads back different after a save. Code that stays loaded after the
 * check, held by something outside the project, is a warning. A project whose manifest has an error,
 * an engine too old for it included, is not opened.
 */
internal class ProjectCheck(private val project: ProjectScenes, private val code: List<File>? = null) {
    fun run(): CheckReport {
        val findings = ArrayList<Finding>()
        val manifest = readManifest(findings)
        val manifestIssues = manifest?.let(::manifestFindings).orEmpty()
        findings += manifestIssues
        findings += AssetLockCheck(project.root).findings()
        val scenes = project.files()
        if (code == null || manifest == null || manifestIssues.any { it.severity == Severity.Error }) {
            scenes.forEach { file -> findings += sceneFindings(project, file, manifest, codeLoaded = false) }
        } else {
            findings += codeFindings(manifest, scenes, code)
        }
        return CheckReport(manifest?.id, scenes.size, findings)
    }

    /** [openWithCode], then whether the project's code was let go of once it was closed. */
    private fun codeFindings(manifest: AwakeProjectManifest, scenes: List<File>, classpath: List<File>): List<Finding> {
        val projectCode = ProjectCode(classpath)
        val findings = projectCode.use { openWithCode(it, manifest, scenes) }
        if (!projectCode.isCollected()) {
            findings += Finding(
                Severity.Warning,
                "the project's code stayed loaded after the check closed it: something outside the project, " +
                    "such as a process-wide registry, a cache or a thread, still holds one of its classes",
                PROJECT_MANIFEST,
                code = CODE_KEPT_LOADED,
            )
        }
        return findings
    }

    /** Every scene checked and opened with the capabilities [projectCode] holds; nothing of the project's code outlives it. */
    private fun openWithCode(projectCode: ProjectCode, manifest: AwakeProjectManifest, scenes: List<File>): MutableList<Finding> {
        val (capabilities, findings) = projectCode.capabilities(manifest)
        val withCode = ProjectScenes(project.root, capabilities)
        val all = ArrayList(findings)
        scenes.forEach { file -> all += sceneFindings(withCode, file, manifest, codeLoaded = true) }
        val files = FileSystemAssetSource(createPlatformFileSystem(project.root.absolutePath))
        val issues = runBlocking {
            checkProjectOpens(files, scenes.map(project::relative), capabilities, physicsWorld = { createJoltPhysicsWorld() })
        }
        issues.forEach { all += Finding(Severity.Error, it.message, it.file, it.node, it.kind.code) }
        return all
    }

    private fun readManifest(findings: MutableList<Finding>): AwakeProjectManifest? {
        val file = project.manifestFile
        if (!file.isFile) {
            findings += Finding(Severity.Error, "no $PROJECT_MANIFEST in ${project.root.path}", code = ProjectIssueCode.INVALID_MANIFEST.value)
            return null
        }
        return runCatching { AwakeProjectValidator.decodeManifest(file.readText()) }
            .onFailure { findings += Finding(Severity.Error, "can't be read: ${it.message}", PROJECT_MANIFEST, code = ProjectIssueCode.INVALID_MANIFEST.value) }
            .getOrNull()
    }

    private fun manifestFindings(manifest: AwakeProjectManifest): List<Finding> = buildList {
        ProjectContentValidator.manifestIssueDetails(manifest).forEach { add(it.toFinding(PROJECT_MANIFEST)) }
        val entry = manifest.entryScene
        if (AwakeProjectValidator.isSafeProjectPath(entry) && !project.root.resolve(entry).isFile) {
            add(Finding(Severity.Error, "the entry scene $entry is missing", PROJECT_MANIFEST, code = ProjectIssueCode.ENTRY_SCENE_MISSING.value))
        }
        val minimum = manifest.minEngineVersion
        val engine = AwakeEngineBuildInfo.VERSION
        // An unreadable minEngineVersion is already a manifest issue.
        if (minimum != null && runCatching { !AwakeProjectValidator.isCompatible(manifest, engine) }.getOrDefault(false)) {
            add(Finding(Severity.Error, "the project needs Awake $minimum or newer; this is $engine", PROJECT_MANIFEST, code = ENGINE_VERSION))
        }
    }

    /**
     * [scenes]' [scene] decoded and validated. With [codeLoaded], a component nothing provides is left to
     * [checkProjectOpens], which reports it as an error, rather than warned of here.
     */
    private fun sceneFindings(scenes: ProjectScenes, scene: File, manifest: AwakeProjectManifest?, codeLoaded: Boolean): List<Finding> {
        val file = scenes.relative(scene)
        val document = try {
            scenes.read(scene)
        } catch (failure: CommandFailure) {
            return listOf(Finding(Severity.Error, failure.message.orEmpty().removePrefix("$file "), file))
        }
        return buildList {
            SceneValidator.validate(document).forEach { add(Finding(Severity.Error, it.message, file, it.path)) }
            if (!codeLoaded) SceneValidator.unknownComponentIssues(document).forEach { add(Finding(Severity.Warning, it.message, file, it.path)) }
            if (manifest != null) {
                val tags = document.nodes.flatMap { it.tags() }
                ProjectContentValidator.unlistedTagIssues(manifest, tags, file).forEach { add(it.toFinding(file)) }
            }
        }
    }

    private fun SceneNode.tags(): List<String> = components.filterIsInstance<SceneTag>().flatMap { it.tags } + children.flatMap { it.tags() }

    private companion object {
        const val ENGINE_VERSION = "engine_version"
        const val CODE_KEPT_LOADED = "code_kept_loaded"
    }
}

/** This issue as a finding in [file]. */
internal fun ProjectContentIssue.toFinding(file: String) = Finding(
    severity = if (severity == ProjectIssueSeverity.WARNING) Severity.Warning else Severity.Error,
    message = message,
    file = file,
    path = path?.takeIf { it != file },
    code = codeValue,
)
