/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.director.ActionCueSpec
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.DirectorValidationIssue
import com.awakekt.awake.director.FramingSpec
import com.awakekt.awake.director.LightingCueSpec
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.director.TrailerReel
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Structured validation report returned by `awake director validate` and MCP `validate_director`.
 */
@Serializable
internal data class DirectorValidationReport(
    val file: String,
    val kind: String,
    val valid: Boolean,
    val durationSeconds: Float? = null,
    val shotCount: Int? = null,
    val errors: List<DirectorValidationIssue> = emptyList(),
    val warnings: List<DirectorValidationIssue> = emptyList(),
)

/**
 * Cross-checks a `*.director.json` script or `*.reel.json` trailer reel against a project's scenes,
 * named entities, rails, cameras, input takes, and audio assets before running GPU capture.
 */
internal class DirectorValidator(private val project: ProjectScenes) {

    fun validateScriptFile(scriptFile: File, sceneFile: File? = null): DirectorValidationReport {
        val script = try {
            DirectorScript.decode(scriptFile.readText())
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            return DirectorValidationReport(
                file = relativePath(scriptFile),
                kind = "director_script",
                valid = false,
                errors = listOf(DirectorValidationIssue("$", "Could not decode director script: ${error.message}")),
            )
        }
        return validateScript(relativePath(scriptFile), script, sceneFile)
    }

    fun validateReelFile(reelFile: File): DirectorValidationReport {
        val reel = try {
            TrailerReel.decode(reelFile.readText())
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            return DirectorValidationReport(
                file = relativePath(reelFile),
                kind = "trailer_reel",
                valid = false,
                errors = listOf(DirectorValidationIssue("$", "Could not decode trailer reel: ${error.message}")),
            )
        }
        val errors = reel.validate().toMutableList()
        val warnings = mutableListOf<DirectorValidationIssue>()
        var totalDuration = 0f
        var totalShots = 0

        reel.music?.path?.let { musicPath ->
            if (!project.root.resolve(musicPath).isFile) {
                warnings += DirectorValidationIssue("music.path", "Music file '$musicPath' not found under project root")
            }
        }

        reel.segments.forEachIndexed { idx, seg ->
            val segScene = runCatching { project.resolve(seg.scene) }.getOrNull()
            if (segScene == null) {
                errors += DirectorValidationIssue("segments[$idx].scene", "Scene '${seg.scene}' not found")
                return@forEachIndexed
            }
            val script = seg.inlineDirector ?: seg.directorPath?.let { path ->
                val file = project.root.resolve(path)
                if (!file.isFile) {
                    errors += DirectorValidationIssue("segments[$idx].directorPath", "Director script '$path' not found")
                    null
                } else {
                    runCatching { DirectorScript.decode(file.readText()) }.getOrElse { err ->
                        errors += DirectorValidationIssue("segments[$idx].directorPath", "Invalid script '$path': ${err.message}")
                        null
                    }
                }
            }
            if (script != null) {
                val sub = validateScript("segments[$idx]", script, segScene)
                totalDuration += sub.durationSeconds ?: 0f
                totalShots += sub.shotCount ?: 0
                sub.errors.forEach { errors += DirectorValidationIssue("segments[$idx].${it.path}", it.message) }
                sub.warnings.forEach { warnings += DirectorValidationIssue("segments[$idx].${it.path}", it.message) }
            }
        }

        return DirectorValidationReport(
            file = relativePath(reelFile),
            kind = "trailer_reel",
            valid = errors.isEmpty(),
            durationSeconds = totalDuration,
            shotCount = totalShots,
            errors = errors,
            warnings = warnings,
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validateScript(label: String, script: DirectorScript, sceneFile: File?): DirectorValidationReport {
        val errors = script.validate().toMutableList()
        val warnings = mutableListOf<DirectorValidationIssue>()

        script.inputTake?.let { takePath ->
            if (!project.root.resolve(takePath).isFile) {
                errors += DirectorValidationIssue("inputTake", "Input take '$takePath' not found under project root")
            }
        }
        script.music?.path?.let { musicPath ->
            if (!project.root.resolve(musicPath).isFile) {
                warnings += DirectorValidationIssue("music.path", "Music clip '$musicPath' not found under project root")
            }
        }
        script.audioTrack.forEachIndexed { idx, cue ->
            if (cue is AudioCueSpec.PlaySfx && !project.root.resolve(cue.path).isFile) {
                warnings += DirectorValidationIssue("audioTrack[$idx].path", "SFX clip '${cue.path}' not found under project root")
            }
        }

        if (sceneFile != null) {
            val stage = StageInspector(project).inspect(sceneFile)
            val entityNames = stage.entities.map(StageEntitySummary::name).toSet()
            val cameraNames = stage.cameras.toSet()
            val railNames = stage.rails.toSet()

            fun checkFraming(prefix: String, framing: FramingSpec?) {
                framing?.subjects?.forEach { name ->
                    if (name !in entityNames) {
                        warnings += DirectorValidationIssue("$prefix.subjects", "Subject '$name' not found in ${stage.scene}")
                    }
                }
            }

            script.shots.forEachIndexed { idx, shot ->
                val p = "shots[$idx].rig"
                when (val rig = shot.rig) {
                    is ShotRigSpec.Static -> checkFraming(p, rig.framing)
                    is ShotRigSpec.Orbit -> checkFraming(p, rig.framing)
                    is ShotRigSpec.Dolly -> checkFraming(p, rig.framing)
                    is ShotRigSpec.Crane -> checkFraming(p, rig.framing)
                    is ShotRigSpec.ChaseFollow -> checkFraming(p, rig.framing)
                    is ShotRigSpec.DollyZoom -> checkFraming(p, rig.framing)
                    is ShotRigSpec.WhipPan -> {
                        checkFraming(p, rig.framing)
                        rig.toSubject?.let { target ->
                            if (target !in entityNames) {
                                warnings += DirectorValidationIssue("$p.toSubject", "WhipPan target '$target' not found in ${stage.scene}")
                            }
                        }
                    }
                    is ShotRigSpec.SplineRail -> {
                        checkFraming(p, rig.framing)
                        rig.railName?.let { rail ->
                            if (rail !in railNames) {
                                errors += DirectorValidationIssue("$p.railName", "Camera rail '$rail' not found in ${stage.scene}")
                            }
                        }
                    }
                    is ShotRigSpec.NamedCamera -> {
                        if (rig.cameraName !in cameraNames) {
                            errors += DirectorValidationIssue("$p.cameraName", "Camera '${rig.cameraName}' not found in ${stage.scene}")
                        }
                    }
                }
            }

            script.lightingTrack.forEachIndexed { idx, cue ->
                if (cue is LightingCueSpec.HeroRimLight && cue.subject !in entityNames) {
                    warnings += DirectorValidationIssue("lightingTrack[$idx].subject", "HeroRimLight subject '${cue.subject}' not found")
                }
                if (cue is LightingCueSpec.LightFlash && cue.subject != null && cue.subject !in entityNames) {
                    warnings += DirectorValidationIssue("lightingTrack[$idx].subject", "LightFlash subject '${cue.subject}' not found")
                }
            }
            script.actionTrack.forEachIndexed { idx, cue ->
                when (cue) {
                    is ActionCueSpec.ActorMove -> if (cue.entity !in entityNames) {
                        warnings += DirectorValidationIssue("actionTrack[$idx].entity", "Actor '${cue.entity}' not found")
                    }
                    is ActionCueSpec.PlayAnimation -> if (cue.entity !in entityNames) {
                        warnings += DirectorValidationIssue("actionTrack[$idx].entity", "Animated entity '${cue.entity}' not found")
                    }
                    is ActionCueSpec.ParticleBurst -> if (cue.entity !in entityNames) {
                        warnings += DirectorValidationIssue("actionTrack[$idx].entity", "Particle entity '${cue.entity}' not found")
                    }
                    is ActionCueSpec.InputAction,
                    is ActionCueSpec.TimeScaleRamp,
                    is ActionCueSpec.CameraTrauma,
                    -> Unit
                }
            }
        }

        return DirectorValidationReport(
            file = label,
            kind = "director_script",
            valid = errors.isEmpty(),
            durationSeconds = script.totalDurationSeconds(),
            shotCount = script.shots.size,
            errors = errors,
            warnings = warnings,
        )
    }

    fun toJson(report: DirectorValidationReport): String =
        PRETTY_JSON.encodeToString(DirectorValidationReport.serializer(), report)

    private fun relativePath(file: File): String = runCatching {
        project.relative(file)
    }.getOrElse { file.path }
}
