/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `awake validate --code` against a small project with a capability of its own, built apart from these
 * tests: it loads the project's code as an editor does and opens every scene with it.
 */
class ValidateWithCodeTest {
    private val root: File = createTempDirectory("awake-code").toFile().also { File(System.getProperty("awake.fixture.project")).copyRecursively(it) }
    private val code: String = System.getProperty("awake.fixture.code")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun withoutItsCodeTheProjectsComponentIsAWarning() {
        val run = awake("validate")

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "warning  scenes/harbor.awakescene  Lamp:  harbor_signal is a component nothing installed provides")
    }

    @Test
    fun withItsCodeEveryScenePrefabAndPinOpens() {
        val run = awake("validate", "--code", code)

        assertEquals(0, run.code, run.all)
        assertEquals("com.example.harbor-town: valid, 2 scenes", run.out.trim(), "nothing to report, not even code that stayed loaded")
    }

    @Test
    fun aCapabilityClassThatIsMissingIsNotACapabilityOrHasAnotherIdIsAnError() {
        for ((manifestEdit, expected) in listOf(
            ("com.example.harbortown.HarborSignalCapability" to "com.example.harbortown.Missing") to "is not in the project's code",
            ("com.example.harbortown.HarborSignalCapability" to "com.example.harbortown.HarborTownNotes") to "is not a SceneCapability",
            ("\"com.example.harbor-town.signal\"" to "\"com.example.harbor-town.lamp\"") to
                "has the id 'com.example.harbor-town.signal', not 'com.example.harbor-town.lamp'",
        )) {
            edit("awake.project.json") { it.replace(manifestEdit.first, manifestEdit.second) }

            val findings = findings("--code", code)

            val capability = findings.single { it.code == "capability_class" }
            assertTrue(expected in capability.message, capability.message)
            assertEquals("awake.project.json", capability.file)
            assertTrue(findings.any { it.code == "project_load" && "requires" in it.message }, "the required plugin then refuses the load: $findings")
            edit("awake.project.json") { it.replace(manifestEdit.second, manifestEdit.first) }
        }
    }

    @Test
    fun aPrefabComponentNoCapabilityRegistersIsAnErrorAtItsNode() {
        edit("prefabs/buoy.prefab.json") { it.replace("\"harbor_signal\", \"signal\": \"data/harbor.signal\"", "\"harbor_bell\"") }

        val run = awake("validate", "--code", code)
        val finding = findings("--code", code).single()

        assertEquals(1, run.code, run.all)
        assertEquals(Seen("unknown_component", "scenes/pier.awakescene", "Mooring/Buoy", finding.message), finding)
        assertContains(finding.message, "harbor_bell")
    }

    @Test
    fun aSceneWhoseCapabilityCannotLoadItsContentIsAnError() {
        edit("prefabs/buoy.prefab.json") { it.replace("data/harbor.signal", "data/missing.signal") }

        val finding = findings("--code", code).single()

        assertEquals("scene_load" to "scenes/pier.awakescene", finding.code to finding.file)
        assertContains(finding.message, "Can't read the signal data/missing.signal")
    }

    @Test
    fun anAssetThatNoLongerMatchesItsPinAndATooOldEngineAreErrors() {
        root.resolve("data/harbor.signal").writeText("pong")
        edit("awake.project.json") { it.replace("\"0.0.1\"", "\"999.0.0\"") }

        // The project's component, warned of as one nothing installed provides, has no code.
        val findings = findings().filter { it.code != null }

        assertEquals(listOf("engine_version", "asset_hash_mismatch"), findings.map { it.code })
        assertEquals(Seen("asset_hash_mismatch", "assets.lock.json", "data/harbor.signal", findings[1].message), findings[1])
        assertContains(findings[0].message, "needs Awake 999.0.0 or newer")
    }

    @Test
    fun codeThatIsNotThereIsAUsageError() {
        val run = awake("validate", "--code", "build/no-such-classes")

        assertEquals(2, run.code, run.all)
        assertContains(run.err, "--code names no class folder or jar that exists")
    }

    private data class Seen(val code: String?, val file: String?, val path: String?, val message: String)

    private fun findings(vararg args: String): List<Seen> {
        val report = Json.parseToJsonElement(awake("validate", "--json", *args).out).jsonObject
        assertEquals(report["valid"]!!.jsonPrimitive.boolean, !report.hasErrors(), "valid when there are no errors")
        return report["findings"]!!.jsonArray.map { it.jsonObject }.map { finding ->
            Seen(finding.text("code"), finding.text("file"), finding.text("path"), finding.text("message").orEmpty())
        }
    }

    private fun JsonObject.hasErrors() = this["findings"]!!.jsonArray.any { it.jsonObject.text("severity") == "error" }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun edit(file: String, change: (String) -> String) {
        val target = root.resolve(file)
        target.writeText(change(target.readText()))
    }

    private class Run(val code: Int, val out: String, val err: String) {
        val all get() = "exit $code\n$out$err"
    }

    private fun awake(vararg args: String): Run {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(args.toList())
        return Run(code, out.toString(), err.toString())
    }
}
