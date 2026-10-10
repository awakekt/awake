/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import kotlin.test.assertFalse

/** `awake` against a small project on disk: what it reports, what it writes, and what it refuses. */
class AwakeCliTest {
    private val root: File = createTempDirectory("awake-cli").toFile()
    private val scene: File get() = root.resolve(SCENE)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aValidProjectValidates() {
        project()

        val run = awake("validate")

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "com.example.harbor: valid, 1 scene")
    }

    @Test
    fun aBrokenSceneFailsNamingItsFileAndNode() {
        project(scene = HARBOR.replace("\"name\": \"Sign\"", "\"name\": \"Dock\""))

        val run = awake("validate")
        val json = Json.parseToJsonElement(awake("validate", "--json").out).jsonObject
        val finding = json["findings"]!!.jsonArray.single().jsonObject

        assertEquals(1, run.code, run.all)
        assertContains(run.out, "error    $SCENE  Dock:  duplicate node name 'Dock'")
        assertFalse(json["valid"]!!.jsonPrimitive.boolean)
        assertEquals(SCENE, finding["file"]!!.jsonPrimitive.content)
        assertEquals("Dock", finding["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun aBadManifestAndAMissingEntrySceneAreErrors() {
        project(manifest = MANIFEST.replace("\"1.0.0\"", "\"one\"").replace(SCENE, "scenes/missing.scene.json"))

        val codes = Json.parseToJsonElement(awake("validate", "--json").out).jsonObject["findings"]!!.jsonArray
            .map { it.jsonObject["code"]!!.jsonPrimitive.content }

        assertEquals(listOf("invalid_manifest", "entry_scene_missing"), codes)
        assertEquals(1, awake("validate").code)
    }

    @Test
    fun aComponentNothingProvidesAndAnUnlistedTagAreWarnings() {
        project(
            manifest = MANIFEST.replace("\"entryScene\"", "\"tags\": [\"harbor\"], \"entryScene\""),
            scene = HARBOR.replace("{ \"name\": \"Dock\" }", DOCK_WITH_CRANE_AND_TAG),
        )

        val run = awake("validate")

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "warning  $SCENE  Dock:  harbor_crane is a component nothing installed provides")
        assertContains(run.out, "tag \"ferry\" is not in the project's tags list")
        assertContains(run.out, "0 errors, 2 warnings")
    }

    @Test
    fun setChangesThatFieldAndNothingElse() {
        project()
        val before = tree()

        val run = awake("scene", "set", "harbor", "Mill", "spin_control.speed=3")
        val after = tree()

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "Mill:spin_control.speed: 1.0 -> 3.0")
        assertEquals(3.0, millSpeed(after))
        assertEquals(before, after.withMillSpeed(1.0), "only the speed changed")
        assertEquals("3.0", Json.parseToJsonElement(awake("scene", "show", "harbor", "--json").out).let(::millSpeed).toString())
    }

    @Test
    fun setRefusesAFieldTheComponentLacksAndLeavesTheFile() {
        project()

        val run = awake("scene", "set", "harbor", "Mill", "spin_control.sped=3")

        assertEquals(1, run.code, run.all)
        assertContains(run.err, "Mill has no field spin_control.sped to set")
        assertEquals(HARBOR, scene.readText())
    }

    @Test
    fun setRefusesAValueOfTheWrongType() {
        project()

        val run = awake("scene", "set", "harbor", "Mill", "spin_control.speed=fast")

        assertEquals(1, run.code, run.all)
        assertContains(run.err, "$SCENE would not be a valid scene")
        assertEquals(HARBOR, scene.readText())
    }

    @Test
    fun anEditThatFailsValidationIsRefused() {
        project()

        val run = awake("scene", "set", "harbor", "Sign", "canvas_element.value=2")

        assertEquals(1, run.code, run.all)
        assertContains(run.err, "canvas_element.value must be between 0 and 1")
        assertEquals(HARBOR, scene.readText())
    }

    @Test
    fun aDryRunReportsTheChangeWithoutWritingIt() {
        project()

        val run = awake("scene", "set", "harbor", "Mill", "transform.position.x=4", "--dry-run")

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "would change harbor:")
        assertContains(run.out, "Mill:transform.position.x: 0.0 -> 4.0")
        assertEquals(HARBOR, scene.readText())
    }

    @Test
    fun nodesAndComponentsAreAddedAndRemoved() {
        project()

        assertEquals(0, awake("scene", "add-node", "harbor", "Lighthouse", "--parent", "Dock").code)
        assertEquals(0, awake("scene", "add-component", "harbor", "Dock/Lighthouse", "spin_control", "{\"speed\": 0.5}").code)
        assertEquals(listOf("spin_control"), tree().nodeAt(listOf(0, 0)).componentTypes())

        assertEquals(0, awake("scene", "remove-component", "harbor", "Dock/Lighthouse", "spin_control").code)
        assertEquals(emptyList(), tree().nodeAt(listOf(0, 0)).componentTypes())
        assertEquals(0, awake("scene", "remove-node", "harbor", "Dock/Lighthouse").code)
        assertEquals(JsonArray(emptyList()), tree().nodeAt(listOf(0))["children"])
    }

    @Test
    fun anEditKeepsTheFilesLayout() {
        project(scene = Json.parseToJsonElement(HARBOR).toString())

        awake("scene", "set", "harbor", "Mill", "spin_control.speed=3")

        assertFalse('\n' in scene.readText().trim(), "a one-line scene, as Studio saves it, stays on one line")
    }

    @Test
    fun anEditToAHandWrittenSceneChangesOnlyThatValue() {
        val written = laidOut(HARBOR.replace("spin_control", "spinControl"))
        project(scene = written)

        awake("scene", "set", "harbor", "Mill", "spin_control.speed=3.5")
        awake("scene", "add-node", "harbor", "Lighthouse")

        val expected = laidOut(
            HARBOR.replace("spin_control", "spinControl").replace("\"speed\": 1.0", "\"speed\": 3.5")
                .replace("\"nodes\": [", "\"nodes\": [ { \"name\": \"Lighthouse\" },").let(::lighthouseLast),
        )
        assertEquals(expected, scene.readText(), "the old component name, the missing defaults and the final newline stay")
    }

    @Test
    fun aFieldTheFileSpellsAnotherWayIsSetWhereItIsSpelled() {
        project(scene = laidOut(HARBOR.replace("{ \"name\": \"Dock\" }", DOCK_WITH_LAMP)))

        val run = awake("scene", "set", "harbor", "Dock", "light.color.r=0.5")
        val color = Json.parseToJsonElement(scene.readText()).jsonObject["nodes"]!!.jsonArray[0].jsonObject["components"]!!
            .jsonArray[0].jsonObject["color"]!!.jsonObject

        assertEquals(0, run.code, run.all)
        assertFalse("x" in color, "the x the file spelled red as is gone, so nothing reads otherwise")
        assertEquals(listOf(0.5, 0.9), listOf("r", "g").map { color[it]!!.jsonPrimitive.content.toDouble() })
        assertFalse("transform" in scene.readText(), "only the colour was written as the codec spells it")
    }

    @Test
    fun listNamesEachScene() {
        project()

        assertContains(awake("scene", "list").out, "$SCENE  harbor  3 nodes")
    }

    @Test
    fun aWrongCommandLineExitsTwo() {
        project()

        assertEquals(2, awake("frobnicate").code)
        assertEquals(2, awake("scene", "set", "harbor").code)
        assertEquals(2, awake("validate", "--colour").code)
        assertEquals(2, awake("scene", "show", "lighthouse").code)
    }

    @Test
    fun aMissingNodeIsNamedWithWhatIsThere() {
        project()

        val run = awake("scene", "remove-node", "harbor", "Ferry")

        assertEquals(1, run.code)
        assertContains(run.err, "the scene has no node 'Ferry'; it has Dock, Mill, Sign")
    }

    private fun project(manifest: String = MANIFEST, scene: String = HARBOR) {
        root.resolve("awake.project.json").writeText(manifest)
        this.scene.parentFile.mkdirs()
        this.scene.writeText(scene)
    }

    /** [scene] laid out as a formatter lays it out, two spaces deep with a final newline. */
    private fun laidOut(scene: String): String =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }.encodeToString(JsonObject.serializer(), Json.parseToJsonElement(scene).jsonObject) + "\n"

    /** [scene] with its first node moved last. */
    private fun lighthouseLast(scene: String): String {
        val tree = Json.parseToJsonElement(scene).jsonObject
        val nodes = tree["nodes"]!!.jsonArray
        return JsonObject(tree + ("nodes" to JsonArray(nodes.drop(1) + nodes.first()))).toString()
    }

    private fun tree(): JsonObject = ProjectScenes(root).let { it.tree(it.read(scene)) }

    private fun millSpeed(tree: kotlinx.serialization.json.JsonElement): Double =
        tree.jsonObject["nodes"]!!.jsonArray[1].jsonObject["components"]!!.jsonArray[0].jsonObject["speed"]!!.jsonPrimitive.content.toDouble()

    private fun JsonObject.withMillSpeed(speed: Double): JsonObject = updateNode(listOf(1)) { node ->
        node.withComponents { list -> list.map { (it as JsonObject).withField(listOf("speed"), JsonPrimitive(speed)) } }
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

    private companion object {
        const val SCENE = "scenes/harbor.scene.json"

        val MANIFEST = """
            {
              "formatVersion": 1,
              "id": "com.example.harbor",
              "name": "Harbor Town",
              "version": "1.0.0",
              "entryScene": "$SCENE"
            }
        """.trimIndent()

        val HARBOR = """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                { "name": "Dock" },
                { "name": "Mill", "components": [ { "component": "spin_control", "speed": 1.0 } ] },
                { "name": "Sign", "components": [ { "component": "canvas_element", "text": "Harbor" } ] }
              ]
            }
        """.trimIndent()

        const val DOCK_WITH_LAMP =
            "{ \"name\": \"Dock\", \"components\": [ { \"component\": \"light\", \"color\": { \"x\": 1.0, \"y\": 0.9, \"z\": 0.8 } } ] }"

        const val DOCK_WITH_CRANE_AND_TAG =
            "{ \"name\": \"Dock\", \"components\": [ { \"component\": \"harbor_crane\", \"reach\": 4 }, " +
                "{ \"component\": \"tag\", \"tags\": [\"ferry\"] } ] }"
    }
}
