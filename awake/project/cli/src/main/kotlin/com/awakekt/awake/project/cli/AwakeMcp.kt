/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.ShotFramingMetrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
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
@Suppress("TooManyFunctions")
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
        Tool(
            "inspect_stage",
            "Inspects a scene's world-space entities, lights, cameras, rails, stage markers, audio clips, director scripts, and recorded input takes on CPU without a GPU.",
            readOnly = true,
            properties = mapOf(SCENE to SCENE_PROPERTY),
            required = listOf(SCENE),
        ) { args ->
            val inspector = StageInspector(project)
            text(inspector.toJson(inspector.inspect(scene(args))))
        },
        Tool(
            "validate_director",
            "Validates a .director.json script or .reel.json trailer reel against the project's scenes, entities, rails, cameras, takes, and audio files before GPU capture.",
            readOnly = true,
            properties = mapOf(
                "director" to string("Path to a .director.json script or .reel.json reel."),
                SCENE to SCENE_PROPERTY,
            ),
            required = listOf("director"),
        ) { args -> validateDirector(args) },
        Tool(
            "preview_storyboard",
            "Renders a multi-shot storyboard contact sheet PNG (start/mid/end per shot) with optional Rule-of-Thirds/safe-frame guides and structured framing/occlusion/luminance telemetry.",
            readOnly = true,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                "director" to string("Path to a .director.json script (either director or script is required)."),
                "script" to buildJsonObject { put("description", "Inline DirectorScript JSON object or JSON string.") },
                "take" to string("Optional path to a .take.json recorded gameplay input take."),
                "tile_width" to integer("Tile width in pixels; $DEFAULT_TILE_WIDTH when omitted."),
                "tile_height" to integer("Tile height in pixels; $DEFAULT_TILE_HEIGHT when omitted."),
                "guides" to boolean("Draw Rule-of-Thirds, action-safe margin, projected subject box, and telemetry banner (default true)."),
                "backend" to string("The GPU backend.", RenderBackend.entries.map { it.label }),
                "output" to string("Also save the contact sheet PNG here."),
            ),
            required = listOf(SCENE),
        ) { args -> previewStoryboard(args) },
        Tool(
            "preview_shot",
            "Renders a single directed shot at a normalized timestamp (0..1) and returns its PNG plus framing, occlusion, clearance, and luminance telemetry.",
            readOnly = true,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                "director" to string("Path to a .director.json script (either director or script is required)."),
                "script" to buildJsonObject { put("description", "Inline DirectorScript JSON object or JSON string.") },
                "shot" to string("The shot id inside the script."),
                "at" to buildJsonObject {
                    put("type", "number")
                    put("description", "Normalized timestamp inside the shot in 0..1 (default 0.5).")
                },
                "take" to string("Optional path to a .take.json recorded gameplay input take."),
                "width" to integer("Pixels wide; $DEFAULT_WIDTH when omitted."),
                "height" to integer("Pixels high; $DEFAULT_HEIGHT when omitted."),
                "guides" to boolean("Draw Rule-of-Thirds and telemetry banner (default true)."),
                "backend" to string("The GPU backend.", RenderBackend.entries.map { it.label }),
                "output" to string("Also save the PNG here."),
            ),
            required = listOf(SCENE, "shot"),
        ) { args -> previewShot(args) },
        Tool(
            "capture_trailer",
            "Records a deterministic headless gameplay trailer (single scene + director script or multi-scene .reel.json) to MP4, WebM, GIF, or PNG sequence + synced WAV.",
            readOnly = false,
            properties = mapOf(
                SCENE to SCENE_PROPERTY,
                "director" to string("Path to a .director.json script (required unless reel or script is given)."),
                "script" to buildJsonObject { put("description", "Inline DirectorScript JSON object or JSON string.") },
                "reel" to string("Path to a multi-scene .reel.json trailer reel."),
                "take" to string("Optional path to a .take.json recorded gameplay input take."),
                "output" to string("Output file path (.mp4, .webm, .gif) or directory for png-sequence."),
                "preset" to string("Capture preset.", CapturePreset.entries.map { it.label }),
                "width" to integer("Pixels wide; overrides preset width."),
                "height" to integer("Pixels high; overrides preset height."),
                "fps" to integer("Fixed frame rate; overrides preset fps (default 60)."),
                "format" to string("Output format.", CaptureFormat.entries.map { it.label }),
                "audio" to string("Optional WAV soundtrack file mixed into the capture."),
                "hud" to string("Force HUD visibility.", listOf("show", "hide")),
                "keep_frames" to boolean("Keep intermediate PNG frames alongside encoded video (default false)."),
                "backend" to string("The GPU backend.", RenderBackend.entries.map { it.label }),
            ),
            required = listOf("output"),
        ) { args -> captureTrailer(args) },
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

    private fun validateDirector(args: JsonObject): List<JsonObject> {
        val file = resolveFile(args.string("director"))
        val sceneFile = args.optionalString(SCENE)?.let(project::resolve)
        val validator = DirectorValidator(project)
        val report = if (file.name.endsWith(".reel.json", ignoreCase = true)) {
            validator.validateReelFile(file)
        } else {
            validator.validateScriptFile(file, sceneFile)
        }
        return text(validator.toJson(report))
    }

    private fun previewStoryboard(args: JsonObject): List<JsonObject> {
        val sceneFile = scene(args)
        val script = resolveScript(args)
        val take = loadDirectorInputTake(project.root, workingDir, script, args.optionalString("take"))
        val request = StoryboardRequest(
            scene = sceneFile,
            script = script,
            inputTake = take,
            tileWidth = args.int("tile_width", DEFAULT_TILE_WIDTH),
            tileHeight = args.int("tile_height", DEFAULT_TILE_HEIGHT),
            drawGuides = args.bool("guides", true),
            backend = args.optionalString("backend")?.let(RenderBackend::named) ?: RenderBackend.default(),
        )
        val result = StoryboardRenderer(project).render(request)
        args.optionalString("output")?.let { path ->
            workingDir.resolve(path).also {
                it.absoluteFile.parentFile?.mkdirs()
                it.writeBytes(result.pngBytes)
            }
        }
        return listOf(
            imageContent(result.pngBytes),
            textContent(PRETTY_JSON.encodeToString(StoryboardReport.serializer(), result.report)),
        )
    }

    private fun previewShot(args: JsonObject): List<JsonObject> {
        val sceneFile = scene(args)
        val script = resolveScript(args)
        val shotId = args.string("shot")
        val at = args.float("at", DEFAULT_SHOT_AT).coerceIn(0f, 1f)
        val take = loadDirectorInputTake(project.root, workingDir, script, args.optionalString("take"))
        val request = StoryboardRequest(
            scene = sceneFile,
            script = script,
            inputTake = take,
            tileWidth = args.int("width", DEFAULT_WIDTH),
            tileHeight = args.int("height", DEFAULT_HEIGHT),
            samples = listOf(at),
            drawGuides = args.bool("guides", true),
            backend = args.optionalString("backend")?.let(RenderBackend::named) ?: RenderBackend.default(),
            shotFilterId = shotId,
        )
        val result = StoryboardRenderer(project).render(request)
        args.optionalString("output")?.let { path ->
            workingDir.resolve(path).also {
                it.absoluteFile.parentFile?.mkdirs()
                it.writeBytes(result.pngBytes)
            }
        }
        val metric = result.report.samples.firstOrNull()
        return listOf(
            imageContent(result.pngBytes),
            textContent(
                if (metric != null) {
                    PRETTY_JSON.encodeToString(ShotFramingMetrics.serializer(), metric)
                } else {
                    PRETTY_JSON.encodeToString(StoryboardReport.serializer(), result.report)
                },
            ),
        )
    }

    private fun captureTrailer(args: JsonObject): List<JsonObject> {
        val preset = args.optionalString("preset")?.let(CapturePreset::named)
        val width = args.int("width", preset?.width ?: DEFAULT_CAPTURE_WIDTH)
        val height = args.int("height", preset?.height ?: DEFAULT_CAPTURE_HEIGHT)
        val fps = args.int("fps", preset?.fps ?: DEFAULT_CAPTURE_FPS)
        val reelPath = args.optionalString("reel")
        val segments = if (reelPath != null) {
            resolveReelSegments(project, workingDir, resolveFile(reelPath))
        } else {
            val sceneFile = scene(args)
            val script = resolveScript(args)
            val take = loadDirectorInputTake(project.root, workingDir, script, args.optionalString("take"))
            listOf(CaptureSegment(sceneFile, script, take))
        }
        val output = workingDir.resolve(args.string("output"))
        val format = CaptureFormat.fromOutputOrLabel(output, args.optionalString("format"))
        val hudOverride = when (val hud = args.optionalString("hud")?.lowercase()) {
            null -> null
            "show" -> true
            "hide" -> false
            else -> throw UsageException("hud must be 'show' or 'hide'")
        }
        val audioOverride = args.optionalString("audio")?.let(::resolveFile)
        val report = TrailerCapture(project).capture(
            TrailerCaptureRequest(
                segments = segments,
                output = output,
                format = format,
                width = width,
                height = height,
                fps = fps,
                backend = args.optionalString("backend")?.let(RenderBackend::named) ?: RenderBackend.default(),
                audioOverride = audioOverride,
                hudOverride = hudOverride,
                keepFrames = args.bool("keep_frames", false),
            ),
        )
        return text(PRETTY_JSON.encodeToString(TrailerCaptureReport.serializer(), report))
    }

    private fun resolveFile(path: String): File =
        workingDir.resolve(path).takeIf(File::isFile)
            ?: project.root.resolve(path).takeIf(File::isFile)
            ?: throw UsageException("no file '$path'")

    private fun resolveScript(args: JsonObject): DirectorScript {
        val inline = args["script"]
        if (inline != null && inline !is JsonNull) {
            val jsonText = if (inline is JsonPrimitive && inline.isString) inline.content else inline.toString()
            return decodeScriptOrFail("inline script", jsonText)
        }
        val path = args.optionalString("director") ?: throw UsageException("either 'director' file path or inline 'script' is required")
        val file = resolveFile(path)
        return decodeScriptOrFail(file.path, file.readText())
    }

    private fun decodeScriptOrFail(label: String, text: String): DirectorScript = try {
        DirectorScript.decode(text)
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw CommandFailure("could not decode director script $label: ${error.message}", error)
    }

    private fun imageContent(png: ByteArray): JsonObject = buildJsonObject {
        put("type", "image")
        put("data", Base64.getEncoder().encodeToString(png))
        put("mimeType", "image/png")
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
        const val DEFAULT_TILE_WIDTH = 480
        const val DEFAULT_TILE_HEIGHT = 270
        const val DEFAULT_CAPTURE_WIDTH = 1280
        const val DEFAULT_CAPTURE_HEIGHT = 720
        const val DEFAULT_CAPTURE_FPS = 60
        const val DEFAULT_SHOT_AT = 0.5f
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
                "without writing it. render shows a scene as a PNG, lit or in a debug view such as clay. For vibe-directing trailers, " +
                "call inspect_stage to discover world-space entities, lights, rails, and audio assets, validate_director to cross-check " +
                "a .director.json script or .reel.json reel, preview_storyboard or preview_shot to inspect contact sheets and framing/occlusion " +
                "telemetry, and capture_trailer to record MP4/WebM/GIF/PNG+WAV output."

        fun string(description: String, choices: List<String> = emptyList()): JsonObject = buildJsonObject {
            put("type", "string")
            put("description", description)
            if (choices.isNotEmpty()) putJsonArray("enum") { choices.forEach { add(JsonPrimitive(it)) } }
        }

        fun integer(description: String): JsonObject = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }

        fun boolean(description: String): JsonObject = buildJsonObject {
            put("type", "boolean")
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

private fun JsonObject.float(name: String, default: Float): Float {
    val value = this[name] ?: return default
    return (value as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite) ?: throw UsageException("$name takes a finite number")
}

private fun JsonObject.bool(name: String, default: Boolean): Boolean {
    val value = this[name] ?: return default
    return (value as? JsonPrimitive)?.booleanOrNull ?: throw UsageException("$name takes true or false")
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
