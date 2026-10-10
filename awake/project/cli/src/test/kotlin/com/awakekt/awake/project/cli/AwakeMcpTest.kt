/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `awake mcp` driven as an agent drives it: one JSON-RPC message per line on stdin, a reply per line on stdout. */
class AwakeMcpTest {
    private val root: File = createTempDirectory("awake-mcp").toFile()
    private val scene: File get() = root.resolve("scenes/harbor.scene.json")
    private var nextId = 0

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun anAgentConnectsListsTheToolsAndValidates() {
        project()

        val replies = serve(
            request("initialize", buildJsonObject { put("protocolVersion", "2025-06-18") }),
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
            request("tools/list"),
            call("validate"),
        )

        assertEquals(3, replies.size, "a reply to each request, none to the notification")
        assertEquals("2025-06-18", replies[0]["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        val tools = replies[1]["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
        assertTrue(tools.map { it["name"]!!.jsonPrimitive.content }.containsAll(listOf("validate", "set_field", "remove_node", "render")))
        assertTrue(tools.single { it["name"]!!.jsonPrimitive.content == "remove_node" }["annotations"]!!.jsonObject["destructiveHint"]!!.jsonPrimitive.boolean)
        assertTrue(Json.parseToJsonElement(replies[2].text()).jsonObject["valid"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun setFieldChangesOnlyThatValueInTheFile() {
        project()
        val before = scene.readText()

        val set = serve(call("set_field", "scene" to "harbor", "node" to "Mill", "field" to "spin_control.speed", "value" to JsonPrimitive(3.5))).single()

        assertFalse(set.isError(), set.text())
        assertContains(set.text(), "Mill:spin_control.speed: 1.0 -> 3.5")
        assertEquals(before.replace("\"speed\": 1.0", "\"speed\": 3.5"), scene.readText())
    }

    @Test
    fun nodesAreAddedRemovedAndADryRunWritesNothing() {
        project()

        val replies = serve(
            call("add_node", "scene" to "harbor", "name" to "Lighthouse", "parent" to "Dock"),
            call("add_component", "scene" to "harbor", "node" to "Dock/Lighthouse", "component" to "spin_control", "fields" to buildJsonObject { put("speed", 0.5) }),
            call("remove_node", "scene" to "harbor", "node" to "Mill", "dry_run" to JsonPrimitive(true)),
            call("show_scene", "scene" to "harbor"),
        )

        assertTrue(replies.none { it.isError() }, replies.joinToString { it.text() })
        assertContains(replies[2].text(), "\"written\":false")
        assertContains(scene.readText(), "Mill", message = "a dry run leaves the file")
        assertContains(replies[3].text(), "Lighthouse")
    }

    @Test
    fun aRefusedEditOrAMissingToolIsAnsweredPlainly() {
        project()
        val before = scene.readText()

        val replies = serve(
            call("set_field", "scene" to "harbor", "node" to "Mill", "field" to "spin_control.sped", "value" to JsonPrimitive(3)),
            call("set_field", "scene" to "harbor", "node" to "Ferry", "field" to "name", "value" to JsonPrimitive("Boat")),
            call("teleport"),
            "not json",
        )

        assertTrue(replies[0].isError())
        assertContains(replies[0].text(), "Mill has no field spin_control.sped to set")
        assertContains(replies[1].text(), "the scene has no node 'Ferry'; it has Dock, Mill, Sign")
        assertEquals(-32602, replies[2]["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(-32700, replies[3]["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(before, scene.readText(), "nothing refused was written")
    }

    private fun project() {
        root.resolve("awake.project.json").writeText(
            """{ "formatVersion": 1, "id": "com.example.harbor", "name": "Harbor Town", "version": "1.0.0", "entryScene": "scenes/harbor.scene.json" }""",
        )
        scene.parentFile.mkdirs()
        scene.writeText(
            """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                {
                  "name": "Dock"
                },
                {
                  "name": "Mill",
                  "components": [
                    {
                      "component": "spin_control",
                      "speed": 1.0
                    }
                  ]
                },
                {
                  "name": "Sign",
                  "components": [
                    {
                      "component": "canvas_element",
                      "text": "Harbor"
                    }
                  ]
                }
              ]
            }
            """.trimIndent() + "\n",
        )
    }

    /** The replies to [lines], fed to `awake mcp` as its stdin. */
    private fun serve(vararg lines: String): List<JsonObject> {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root) { lines.joinToString("\n").reader().buffered() }.run(listOf("mcp"))
        assertEquals(0, code, "$out$err")
        return out.lines().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject }
    }

    private fun request(method: String, params: JsonObject = JsonObject(emptyMap())): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", ++nextId)
        put("method", method)
        put("params", params)
    }.toString()

    private fun call(tool: String, vararg arguments: Pair<String, Any>): String = request(
        "tools/call",
        buildJsonObject {
            put("name", tool)
            put("arguments", JsonObject(arguments.associate { (name, value) -> name to (value as? JsonElement ?: JsonPrimitive(value.toString())) }))
        },
    )

    private fun JsonObject.result(): JsonObject = this["result"]!!.jsonObject

    private fun JsonObject.text(): String = result()["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

    private fun JsonObject.isError(): Boolean = result()["isError"]!!.jsonPrimitive.boolean
}
