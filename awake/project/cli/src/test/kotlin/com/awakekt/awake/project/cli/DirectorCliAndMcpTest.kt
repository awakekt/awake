/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.director.ActionCueSpec
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.FramingSpec
import com.awakekt.awake.director.ReelSegmentSpec
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.director.TimeRef
import com.awakekt.awake.director.TrailerReel
import com.awakekt.awake.director.directorScript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GPU-free tests for `awake stage`, `awake director validate`, MCP director tools (`inspect_stage`,
 * `validate_director`, tool discovery), and capture overlay/luminance helpers.
 */
class DirectorCliAndMcpTest {
    private val root: File = createTempDirectory("awake-director-cli").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun stageCommandAndMcpInspectStageReportWorldSpaceStageAndAssets() {
        createSampleProject()

        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(listOf("stage", "harbor", "--json"))
        assertEquals(0, code, "$out$err")

        val report = Json.decodeFromString(StageReport.serializer(), out.toString())
        assertEquals("scenes/harbor.scene.json", report.scene)
        assertTrue("Hero" in report.entities.map { it.name })
        assertTrue("PierRail" in report.rails)
        assertTrue("OverlookCam" in report.cameras)
        assertTrue("DuelMark" in report.markers)
        assertTrue("Sun" in report.lights)
        assertTrue("audio/theme.wav" in report.audioAssets)
        assertTrue("scripts/trailer.director.json" in report.directorScripts)
        assertTrue("takes/run.take.json" in report.inputTakes)

        // Child node position should reflect parent transform composition (Dock at x=10 + Lantern local x=2 => 12).
        val lantern = report.entities.single { it.name == "Lantern" }
        assertEquals(12f, lantern.position[0], 1e-2f)

        // Same inspection over MCP stdio.
        val mcpOut = StringBuilder()
        val mcpErr = StringBuilder()
        val calls = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""",
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"inspect_stage","arguments":{"scene":"harbor"}}}""",
        ).joinToString("\n")
        assertEquals(0, AwakeCli(mcpOut, mcpErr, root) { calls.reader().buffered() }.run(listOf("mcp")), "$mcpOut$mcpErr")

        val lines = mcpOut.lines().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject }
        val toolNames = lines[0]["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(
            toolNames.containsAll(listOf("inspect_stage", "validate_director", "preview_storyboard", "preview_shot", "capture_trailer")),
            "Expected director MCP tools in $toolNames",
        )
        val stageText = lines[1]["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val mcpStage = Json.decodeFromString(StageReport.serializer(), stageText)
        assertEquals(report.entities.size, mcpStage.entities.size)
        assertEquals(report.rails, mcpStage.rails)
    }

    @Suppress("LongMethod")
    @Test
    fun directorValidateChecksScriptsReelsReferencesAndMissingTargets() {
        createSampleProject()

        // Valid script referencing real entity, real rail, real audio file, and real take.
        val outValid = StringBuilder()
        val errValid = StringBuilder()
        val codeValid = AwakeCli(outValid, errValid, root).run(
            listOf("director", "validate", "scripts/trailer.director.json", "--scene", "harbor", "--json"),
        )
        assertEquals(0, codeValid, "$outValid$errValid")
        val validReport = Json.decodeFromString(DirectorValidationReport.serializer(), outValid.toString())
        assertTrue(validReport.valid)
        assertEquals(2, validReport.shotCount)
        assertTrue(validReport.errors.isEmpty())
        assertTrue(validReport.warnings.isEmpty())

        // Script referencing a missing rail (error) and missing subject (warning).
        val broken = directorScript("broken") {
            shot("bad_rail") {
                seconds(2f)
                rig = ShotRigSpec.SplineRail(railName = "MissingRail", framing = FramingSpec(subjects = listOf("Ghost")))
            }
            audio(AudioCueSpec.PlaySfx(at = TimeRef.seconds(0.5f), path = "audio/missing.wav"))
            action(ActionCueSpec.ActorMove(at = TimeRef.seconds(0.2f), entity = "MissingActor"))
        }
        root.resolve("scripts/broken.director.json").writeText(DirectorScript.encode(broken))

        val outBroken = StringBuilder()
        val errBroken = StringBuilder()
        val codeBroken = AwakeCli(outBroken, errBroken, root).run(
            listOf("director", "validate", "scripts/broken.director.json", "--scene", "harbor", "--json"),
        )
        assertEquals(1, codeBroken, "$outBroken$errBroken")
        val brokenReport = Json.decodeFromString(DirectorValidationReport.serializer(), outBroken.toString())
        assertFalse(brokenReport.valid)
        assertTrue(brokenReport.errors.any { "MissingRail" in it.message })
        assertTrue(brokenReport.warnings.any { "Ghost" in it.message })
        assertTrue(brokenReport.warnings.any { "missing.wav" in it.message })
        assertTrue(brokenReport.warnings.any { "MissingActor" in it.message })

        // Validate multi-scene trailer reel via MCP `validate_director`.
        val reel = TrailerReel(
            name = "harbor_reel",
            segments = listOf(ReelSegmentSpec(id = "seg_harbor", scene = "harbor", directorPath = "scripts/trailer.director.json")),
        )
        root.resolve("scripts/harbor.reel.json").writeText(TrailerReel.encode(reel))

        val mcpOut = StringBuilder()
        val mcpErr = StringBuilder()
        val call = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 7)
            put("method", "tools/call")
            put(
                "params",
                buildJsonObject {
                    put("name", "validate_director")
                    put("arguments", buildJsonObject { put("director", JsonPrimitive("scripts/harbor.reel.json")) })
                },
            )
        }.toString()
        assertEquals(0, AwakeCli(mcpOut, mcpErr, root) { call.reader().buffered() }.run(listOf("mcp")), "$mcpOut$mcpErr")
        val reply = Json.parseToJsonElement(mcpOut.toString().trim()).jsonObject["result"]!!.jsonObject
        assertFalse(reply["isError"]!!.jsonPrimitive.boolean)
        val reelReport = Json.decodeFromString(
            DirectorValidationReport.serializer(),
            reply["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertTrue(reelReport.valid)
        assertEquals("trailer_reel", reelReport.kind)
        assertEquals(2, reelReport.shotCount)
    }

    @Test
    fun frameOverlaysApplyFadeLetterboxAndLuminanceMeasurement() {
        val w = 16
        val h = 16
        val rgba = ByteArray(w * h * 4) { idx -> if (idx % 4 == 3) (-1).toByte() else 200.toByte() }
        val beforeLum = measureAverageLuminance(rgba, w, h)
        assertTrue(beforeLum > 0.75f, "expected bright initial frame, got $beforeLum")

        // Apply 50% fade + 2.39:1 cinemascope letterbox onto a 1:1 frame.
        applyFrameOverlays(rgba, w, h, fadeBlack = 0.5f, letterboxAspect = 2.39f)
        // Top row must be black letterbox bar.
        assertEquals(0, rgba[0].toInt() and 0xFF)
        assertEquals(0, rgba[1].toInt() and 0xFF)
        assertEquals(0, rgba[2].toInt() and 0xFF)
        assertEquals(255, rgba[3].toInt() and 0xFF)

        // Center pixel must be faded to ~100.
        val centerIdx = ((h / 2) * w + (w / 2)) * 4
        val centerR = rgba[centerIdx].toInt() and 0xFF
        assertTrue(centerR in 95..105, "expected center pixel faded to ~100, got $centerR")

        val afterLum = measureAverageLuminance(rgba, w, h)
        assertTrue(afterLum < beforeLum * 0.5f, "fade + letterbox must lower average luminance")
    }

    @Test
    fun presetsAndFormatsResolveCleanly() {
        assertEquals(1920, CapturePreset.named("youtube-1080p").width)
        assertEquals(1920, CapturePreset.named("shorts-1080p").height)
        assertEquals(24, CapturePreset.named("cinemascope-1080p").fps)
        assertEquals(CaptureFormat.Mp4, CaptureFormat.fromOutputOrLabel(File("out.mp4"), null))
        assertEquals(CaptureFormat.Webm, CaptureFormat.fromOutputOrLabel(File("out.webm"), null))
        assertEquals(CaptureFormat.Gif, CaptureFormat.fromOutputOrLabel(File("out.gif"), null))
        assertEquals(CaptureFormat.PngSequence, CaptureFormat.fromOutputOrLabel(File("frames_dir"), null))
    }

    @Suppress("LongMethod")
    private fun createSampleProject() {
        root.resolve("awake.project.json").writeText(
            """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/harbor.scene.json"}""",
        )
        root.resolve("scenes").mkdirs()
        root.resolve("audio").mkdirs()
        root.resolve("scripts").mkdirs()
        root.resolve("takes").mkdirs()
        root.resolve("audio/theme.wav").writeBytes(ByteArray(64))
        root.resolve("takes/run.take.json").writeText("""{"name":"run","fixedDelta":0.016666668,"frames":[]}""")
        root.resolve("scenes/harbor.scene.json").writeText(
            """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                {
                  "name": "Hero",
                  "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                  "components": [ { "component": "mesh_renderer", "mesh": "cube", "material": "lit-shadow" } ]
                },
                {
                  "name": "Dock",
                  "transform": { "position": { "x": 10.0, "y": 0.0, "z": 0.0 } },
                  "children": [
                    {
                      "name": "Lantern",
                      "transform": { "position": { "x": 2.0, "y": 3.0, "z": 0.0 } }
                    }
                  ]
                },
                {
                  "name": "Sun",
                  "components": [ { "component": "light", "type": "Directional", "intensity": 1.2 } ]
                },
                {
                  "name": "OverlookCam",
                  "components": [ { "component": "virtual_camera", "eye": { "x": 0.0, "y": 4.0, "z": 10.0 }, "center": { "x": 0.0, "y": 1.0, "z": 0.0 } } ]
                },
                {
                  "name": "PierRail",
                  "components": [ { "component": "camera_rail", "waypoints": [ { "x": -6.0, "y": 3.0, "z": 8.0 }, { "x": 6.0, "y": 3.0, "z": 8.0 } ] } ]
                },
                {
                  "name": "DuelMark",
                  "components": [ { "component": "stage_marker", "tags": ["duel"], "radius": 2.0 } ]
                }
              ]
            }
            """.trimIndent(),
        )
        val script = directorScript("harbor_trailer") {
            inputTake = "takes/run.take.json"
            music("audio/theme.wav", volume = 0.7f)
            shot("establish") {
                seconds(2f)
                orbit("Hero", startAzimuthDeg = -30f, endAzimuthDeg = 30f)
            }
            shot("rail_glide") {
                seconds(2f)
                rig = ShotRigSpec.SplineRail(railName = "PierRail", framing = FramingSpec(subjects = listOf("Hero")))
            }
        }
        root.resolve("scripts/trailer.director.json").writeText(DirectorScript.encode(script))
    }
}
