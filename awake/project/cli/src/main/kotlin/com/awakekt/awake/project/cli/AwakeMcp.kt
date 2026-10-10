/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.File
import java.io.Flushable
import java.io.StringWriter
import java.util.Base64

/**
 * `awake mcp`: the command line's commands served to an AI agent over the Model Context Protocol's
 * stdio transport, one JSON-RPC message per line in and out. It works on [project]'s files, so an
 * agent can validate, read, edit and render a project with no Studio running, from a cloud agent or
 * a CI job as well as a desktop.
 *
 * Every edit goes through the command line's own path: the codec checks the value's type, a field the
 * component lacks is refused, an edit that adds a validation error is refused, and the file keeps
 * its layout. The tools take the names Studio's MCP server uses where the two overlap, so an agent
 * moving between them learns nothing new.
 *
 * @param project The project whose files the tools work on.
 * @param workingDir Where a relative render output path starts.
 */
internal class AwakeMcp(private val project: ProjectScenes, private val workingDir: File) {
    private class Tool(
        val name: String,
        val description: String,
        val readOnly: Boolean,
        val destructive: Boolean = false,
        val properties: Map<String, JsonObject> = emptyMap(),
        val required: List<String> = emptyList(),
        val run: (JsonObject) -> List<JsonObject>,
    ) {
        /** This tool as `tools/list` lists it. */
        fun json(): JsonObject = buildJsonObject {
            put("name", name)
            put("description", description)
            putJsonObject("inputSchema") {
                put("type", "object")
                put("properties", JsonObject(properties))
                if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
            putJsonObject("annotations") {
                put("readOnlyHint", readOnly)
                put("destructiveHint", destructive)
                put("idempotentHint", readOnly)
                put("openWorldHint", false)
            }
        }
    }

    /** A request answered with a JSON-RPC error rather than a result. */
    private class RequestError(val code: Int, override val message: String) : Exception(message)

    private val tools = listOf(
        Tool("validate", "Checks the manifest, the entry scene and every scene: errors, and warnings for unknown components and unlisted tags.", readOnly = true) {
            text(ProjectCheck(project).run().toJson())
        },
        Tool("list_scenes", "The project's scenes, with their names and node counts.", readOnly = true) {
            text(view { list() })
        },
        Tool(
            "show_scene",
            "A scene's nodes, their children and every component's fields, as the scene file holds them.",
            readOnly = true,
            properties = mapOf(SCENE to SCENE_PROPERTY),
            required = listOf(SCENE),
        ) { args -> text(view { show(scene(args)) }) },
        Tool(
            "set_field",
            "Sets a component's field on a node: component.field, such as spin_control.speed, or transform.position.x, or name. " +
                "The file changes only there.",
            readOnly = false,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                NODE to NODE_PROPERTY,
                "field" to string("component.field, transform.…, or name."),
                "value" to buildJsonObject { put("description", "The new value, as JSON: a number, true or false, text, or an object such as {\"x\": 1}.") },
                DRY_RUN to DRY_RUN_PROPERTY,
            ),
            required = listOf(SCENE, NODE, "field", "value"),
        ) { args -> edit(args) { set(args.string(NODE), args.string("field"), args.getValue("value")) } },
        Tool(
            "add_node",
            "Adds a node, last under its parent or at the scene's top level.",
            readOnly = false,
            properties = mapOf(SCENE to SCENE_PROPERTY, "name" to string("The new node's name."), "parent" to NODE_PROPERTY, DRY_RUN to DRY_RUN_PROPERTY),
            required = listOf(SCENE, "name"),
        ) { args -> edit(args) { addNode(args.string("name"), args.optionalString("parent")) } },
        Tool(
            "remove_node",
            "Removes a node and its children from the scene file.",
            readOnly = false,
            destructive = true,
            properties = mapOf(SCENE to SCENE_PROPERTY, NODE to NODE_PROPERTY, DRY_RUN to DRY_RUN_PROPERTY),
            required = listOf(SCENE, NODE),
        ) { args -> edit(args) { removeNode(args.string(NODE)) } },
        Tool(
            "add_component",
            "Adds a component to a node, with the fields given set on it.",
            readOnly = false,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                NODE to NODE_PROPERTY,
                COMPONENT to string("The component's type, such as spin_control."),
                "fields" to buildJsonObject {
                    put("type", "object")
                    put("description", "Fields to set on it; the rest keep their defaults.")
                },
                DRY_RUN to DRY_RUN_PROPERTY,
            ),
            required = listOf(SCENE, NODE, COMPONENT),
        ) { args -> edit(args) { addComponent(args.string(NODE), args.string(COMPONENT), args["fields"]?.let { it as? JsonObject ?: throw UsageException("fields must be an object") }?.toString()) } },
        Tool(
            "remove_component",
            "Removes a node's component of a type.",
            readOnly = false,
            destructive = true,
            properties = mapOf(SCENE to SCENE_PROPERTY, NODE to NODE_PROPERTY, COMPONENT to string("The component's type."), DRY_RUN to DRY_RUN_PROPERTY),
            required = listOf(SCENE, NODE, COMPONENT),
        ) { args -> edit(args) { removeComponent(args.string(NODE), args.string(COMPONENT)) } },
        Tool(
            "render",
            "Plays a scene headless and returns what its primary camera sees as a PNG, lit or in a debug view. Needs a GPU, or Mesa's lavapipe.",
            readOnly = true,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                "width" to integer("Pixels wide; $DEFAULT_WIDTH when omitted."),
                "height" to integer("Pixels high; $DEFAULT_HEIGHT when omitted."),
                "frames" to integer("Frames to play first; 1 when omitted."),
                "view" to string("The view.", RenderView.entries.map { it.label }),
                "camera" to string("The node whose camera takes the picture; the primary camera when omitted."),
                "backend" to string("The GPU backend.", RenderBackend.entries.map { it.label }),
                "output" to string("Also save the PNG here, a path from where awake runs."),
            ),
            required = listOf(SCENE),
        ) { args -> render(args) },
    )

    /** Answers each line of [input] on [out] until the input ends. */
    fun serve(input: BufferedReader, out: Appendable) {
        input.lineSequence().filter(String::isNotBlank).forEach { line ->
            handle(line)?.let { reply ->
                out.appendLine(reply)
                (out as? Flushable)?.flush()
            }
        }
    }

    /** The reply to one JSON-RPC [message], or null for a notification. */
    fun handle(message: String): String? {
        val parsed = runCatching { Json.parseToJsonElement(message) }.getOrNull()
        val request = parsed as? JsonObject
            ?: return mcpError(JsonNull, if (parsed == null) PARSE_ERROR else INVALID_REQUEST, "Send one JSON-RPC message per line")
        val id = request["id"]
        val method = (request["method"] as? JsonPrimitive)?.contentOrNull
        // A notification, or a reply to a request this server never sends: nothing to answer.
        return if (id == null || method == null) null else reply(id, method, request["params"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    private fun reply(id: JsonElement, method: String, params: JsonObject): String = try {
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result(method, params))
        }.toString()
    } catch (refused: RequestError) {
        mcpError(id, refused.code, refused.message)
    }

    private fun result(method: String, params: JsonObject): JsonObject = when (method) {
        "initialize" -> initialize(params)
        "ping" -> JsonObject(emptyMap())
        "tools/list" -> buildJsonObject { putJsonArray("tools") { tools.forEach { add(it.json()) } } }
        "tools/call" -> {
            val tool = tools.firstOrNull { it.name == params.text("name") }
                ?: throw RequestError(INVALID_PARAMS, "No tool '${params.text("name")}'")
            call(tool, params["arguments"] as? JsonObject ?: JsonObject(emptyMap()))
        }
        else -> throw RequestError(METHOD_NOT_FOUND, "No method '$method'")
    }

    private fun call(tool: Tool, arguments: JsonObject): JsonObject {
        val (content, isError) = try {
            tool.run(arguments) to false
        } catch (refused: UsageException) {
            listOf(textContent(refused.message.orEmpty())) to true
        } catch (failure: CommandFailure) {
            listOf(textContent(failure.message.orEmpty())) to true
        }
        return buildJsonObject {
            putJsonArray("content") { content.forEach { add(it) } }
            put("isError", isError)
        }
    }

    private fun initialize(params: JsonObject): JsonObject = buildJsonObject {
        val requested = params.text("protocolVersion")
        put("protocolVersion", requested?.takeIf { it in SUPPORTED_VERSIONS } ?: SUPPORTED_VERSIONS.first())
        putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
        putJsonObject("serverInfo") {
            put("name", "awake")
            put("title", "Awake command line")
            put("version", "1")
        }
        put("instructions", INSTRUCTIONS)
    }

    private fun edit(args: JsonObject, change: SceneEdits.() -> SceneEdit): List<JsonObject> {
        val dryRun = (args[DRY_RUN] as? JsonPrimitive)?.booleanOrNull ?: false
        val file = scene(args)
        val result = SceneEdits(project, file, dryRun).change()
        return text(JsonObject(mapOf(SCENE to JsonPrimitive(project.relative(file))) + result.toJson(dryRun)))
    }

    private fun render(args: JsonObject): List<JsonObject> {
        val request = RenderRequest(
            scene = scene(args),
            width = args.int("width", DEFAULT_WIDTH),
            height = args.int("height", DEFAULT_HEIGHT),
            frames = args.int("frames", 1),
            backend = args.optionalString("backend")?.let(RenderBackend::named) ?: RenderBackend.default(),
            view = args.optionalString("view")?.let(RenderView::named) ?: RenderView.Lit,
            camera = args.optionalString("camera"),
        )
        val png = ProjectRender(project).render(request).png()
        val saved = args.optionalString("output")?.let { path ->
            workingDir.resolve(path).also {
                it.absoluteFile.parentFile?.mkdirs()
                it.writeBytes(png)
            }
        }
        return listOfNotNull(
            buildJsonObject {
                put("type", "image")
                put("data", Base64.getEncoder().encodeToString(png))
                put("mimeType", "image/png")
            },
            saved?.let { textContent("Saved to ${it.path}") },
        )
    }

    private fun view(show: SceneViews.() -> Unit): String = StringWriter().also { SceneViews(it, project, json = true).show() }.toString().trim()

    private fun scene(args: JsonObject): File = project.resolve(args.string(SCENE))

    private companion object {
        /** Protocol revisions this server speaks, newest first; a client asking for another gets the newest. */
        val SUPPORTED_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

        const val SCENE = "scene"
        const val NODE = "node"
        const val COMPONENT = "component"
        const val DRY_RUN = "dry_run"
        const val DEFAULT_WIDTH = 960
        const val DEFAULT_HEIGHT = 540
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602

        val SCENE_PROPERTY = string("The scene: a path from the project root, or a name under scenes/.")
        val NODE_PROPERTY = string("A node: its path of names, such as Player/Camera, with #2 for an unnamed node's index.")
        val DRY_RUN_PROPERTY: JsonObject = buildJsonObject {
            put("type", "boolean")
            put("description", "Report what would change without writing it.")
        }

        const val INSTRUCTIONS =
            "An Awake project's files, with no Studio running. Start with validate and list_scenes. A node is its path of names, " +
                "such as Player/Camera. set_field takes component.field, such as spin_control.speed or transform.position.x. " +
                "Every edit is checked before it is written, and the file changes only where it was edited; dry_run reports an edit " +
                "without writing it. render shows a scene as a PNG, lit or in a debug view such as clay."

        fun string(description: String, choices: List<String> = emptyList()): JsonObject = buildJsonObject {
            put("type", "string")
            put("description", description)
            if (choices.isNotEmpty()) putJsonArray("enum") { choices.forEach { add(JsonPrimitive(it)) } }
        }

        fun integer(description: String): JsonObject = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
    }
}

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.optionalString(name: String): String? = text(name)?.takeIf(String::isNotEmpty)

private fun JsonObject.string(name: String): String = optionalString(name) ?: throw UsageException("$name is required")

private fun JsonObject.int(name: String, default: Int): Int {
    val value = this[name] ?: return default
    return (value as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: throw UsageException("$name takes a positive whole number")
}

private fun text(json: JsonElement): List<JsonObject> = listOf(textContent(json.toString()))

private fun text(json: String): List<JsonObject> = listOf(textContent(json))

private fun textContent(text: String): JsonObject = buildJsonObject {
    put("type", "text")
    put("text", text)
}

private fun mcpError(id: JsonElement, code: Int, message: String): String = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    putJsonObject("error") {
        put("code", code)
        put("message", message)
    }
}.toString()
