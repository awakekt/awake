/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.ProjectContentIssue
import com.awakekt.awake.project.ProjectContentValidator
import com.awakekt.awake.project.ProjectIssueCode
import com.awakekt.awake.project.ProjectIssueSeverity
import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import com.awakekt.awake.scene.core.SceneTag
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator

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
 * Checks a project the way a played project and Studio do: its manifest, that the entry scene exists,
 * and that every scene decodes and validates. A component nothing installed provides, and a tag the
 * manifest doesn't list, are warnings.
 */
internal class ProjectCheck(private val project: ProjectScenes) {
    fun run(): CheckReport {
        val findings = ArrayList<Finding>()
        val manifest = readManifest(findings)
        manifest?.let { findings += manifestFindings(it) }
        val scenes = project.files()
        scenes.forEach { file -> findings += sceneFindings(project.relative(file), manifest) { project.read(file) } }
        return CheckReport(manifest?.id, scenes.size, findings)
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
    }

    private fun sceneFindings(file: String, manifest: AwakeProjectManifest?, read: () -> SceneDocument): List<Finding> {
        val document = try {
            read()
        } catch (failure: CommandFailure) {
            return listOf(Finding(Severity.Error, failure.message.orEmpty().removePrefix("$file "), file))
        }
        return buildList {
            SceneValidator.validate(document).forEach { add(Finding(Severity.Error, it.message, file, it.path)) }
            SceneValidator.unknownComponentIssues(document).forEach { add(Finding(Severity.Warning, it.message, file, it.path)) }
            if (manifest != null) {
                val tags = document.nodes.flatMap { it.tags() }
                ProjectContentValidator.unlistedTagIssues(manifest, tags, file).forEach { add(it.toFinding(file)) }
            }
        }
    }

    private fun SceneNode.tags(): List<String> = components.filterIsInstance<SceneTag>().flatMap { it.tags } + children.flatMap { it.tags() }

    private fun ProjectContentIssue.toFinding(file: String) = Finding(
        severity = if (severity == ProjectIssueSeverity.WARNING) Severity.Warning else Severity.Error,
        message = message,
        file = file,
        path = path?.takeIf { it != file },
        code = codeValue,
    )
}
