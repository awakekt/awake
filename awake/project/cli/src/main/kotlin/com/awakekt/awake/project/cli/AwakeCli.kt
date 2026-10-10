/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedReader
import java.io.File

/**
 * `awake`, the command line for an Awake project's files. It validates a project and reads and edits
 * its scenes, with no Studio and no GPU, through the scene codec and validation a played project uses.
 * Every command takes `--json` for output a program reads.
 *
 * Exits 0 when a command succeeds, 1 when it finds errors or refuses an edit, and 2 when the command
 * line itself is wrong.
 */
class AwakeCli(
    private val out: Appendable,
    private val err: Appendable,
    private val workingDir: File,
    private val input: () -> BufferedReader = { System.`in`.bufferedReader() },
) {

    /** Runs the command [args] and returns its exit code. */
    fun run(args: List<String>): Int = try {
        val arguments = CliArguments.parse(args)
        if (arguments.flag("help")) help() else dispatch(arguments)
    } catch (usage: UsageException) {
        err.appendLine("awake: ${usage.message}")
        err.appendLine("Run 'awake help' for the commands.")
        USAGE
    } catch (failure: CommandFailure) {
        err.appendLine("awake: ${failure.message}")
        FAILED
    }

    private fun dispatch(arguments: CliArguments): Int = when (val command = arguments.words.firstOrNull()) {
        null, "help" -> help()
        "validate" -> validate(arguments)
        "scene" -> scene(arguments)
        "render" -> render(arguments)
        "mcp" -> OK.also { AwakeMcp(ProjectScenes(projectRoot(arguments.value("project"))), workingDir).serve(input(), out) }
        else -> throw UsageException("unknown command '$command'")
    }

    private fun scene(arguments: CliArguments): Int {
        val project = ProjectScenes(projectRoot(arguments.value("project")))
        val json = arguments.flag("json")
        return when (val command = arguments.words.getOrNull(1)) {
            "list" -> OK.also { SceneViews(out, project, json).list() }
            "show" -> OK.also { SceneViews(out, project, json).show(project.resolve(arguments.required(2, "scene"))) }
            "set", "add-node", "remove-node", "add-component", "remove-component" -> edit(project, command, arguments)
            null -> throw UsageException("missing scene command")
            else -> throw UsageException("unknown scene command '$command'")
        }
    }

    private fun validate(arguments: CliArguments): Int {
        val project = ProjectScenes(projectRoot(arguments.words.getOrNull(1) ?: arguments.value("project")))
        val report = ProjectCheck(project).run()
        if (arguments.flag("json")) {
            out.appendLine(PRETTY_JSON.encodeToString(JsonObject.serializer(), report.toJson()))
        } else {
            report.findings.forEach { out.appendLine(it.line()) }
            out.appendLine(report.summary())
        }
        return if (report.errors.isEmpty()) OK else FAILED
    }

    private fun render(arguments: CliArguments): Int {
        val project = ProjectScenes(projectRoot(arguments.value("project")))
        val scene = project.resolve(arguments.required(1, "scene"))
        val request = RenderRequest(
            scene = scene,
            width = arguments.int("width", DEFAULT_WIDTH, PIXELS),
            height = arguments.int("height", DEFAULT_HEIGHT, PIXELS),
            frames = arguments.int("frames", 1, FRAMES),
            backend = arguments.value("backend")?.let(RenderBackend::named) ?: RenderBackend.default(),
            view = arguments.value("view")?.let(RenderView::named) ?: RenderView.Lit,
            camera = arguments.value("camera"),
        )
        val output = workingDir.resolve(arguments.value("output") ?: "${scene.name.substringBefore('.')}.png")
        ProjectRender(project).render(request).writePng(output)
        if (arguments.flag("json")) {
            val result = buildJsonObject {
                put("written", output.path)
                put("scene", project.relative(scene))
                put("width", request.width)
                put("height", request.height)
                put("frames", request.frames)
                put("backend", request.backend.label)
                put("view", request.view.label)
            }
            out.appendLine(PRETTY_JSON.encodeToString(JsonObject.serializer(), result))
        } else {
            out.appendLine(
                "rendered ${project.relative(scene)} to ${output.path}: ${request.width}x${request.height}, " +
                    "${request.view.label} on ${request.backend.label}, frame ${request.frames}",
            )
        }
        return OK
    }

    private fun edit(project: ProjectScenes, command: String, arguments: CliArguments): Int {
        val edits = SceneEdits(project, project.resolve(arguments.required(2, "scene")), arguments.flag("dry-run"))
        val edit = when (command) {
            "set" -> edits.set(arguments.required(3, "node"), arguments.required(4, "component.field=value"))
            "add-node" -> edits.addNode(arguments.required(3, "node name"), arguments.value("parent"))
            "remove-node" -> edits.removeNode(arguments.required(3, "node"))
            "add-component" -> edits.addComponent(arguments.required(3, "node"), arguments.required(4, "component type"), arguments.words.getOrNull(5))
            else -> edits.removeComponent(arguments.required(3, "node"), arguments.required(4, "component type"))
        }
        val changes = edit.changes
        val verb = if (arguments.flag("dry-run")) "would change" else "changed"
        if (arguments.flag("json")) {
            out.appendLine(PRETTY_JSON.encodeToString(JsonObject.serializer(), edit.toJson(arguments.flag("dry-run"))))
        } else if (changes.isEmpty()) {
            out.appendLine("no change")
        } else {
            out.appendLine("$verb ${arguments.words[2]}:")
            changes.forEach { out.appendLine("$INDENT$it") }
            if (edit.rewritten) out.appendLine("the file spells that differently, so it is written whole, as Studio saves a scene")
        }
        return OK
    }

    private fun help(): Int {
        out.append(HELP)
        return OK
    }

    /** [given], or the nearest directory up from where `awake` runs that holds a project manifest. */
    private fun projectRoot(given: String?): File {
        if (given != null) {
            val root = workingDir.resolve(given)
            if (!root.isDirectory) throw UsageException("no directory $given")
            return root
        }
        return generateSequence(workingDir) { it.parentFile }.firstOrNull { it.resolve(PROJECT_MANIFEST).isFile } ?: workingDir
    }

    private companion object {
        const val OK = 0
        const val FAILED = 1
        const val USAGE = 2
        const val INDENT = "  "
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        val PIXELS = 16..8192
        val FRAMES = 1..36_000

        val HELP = """
            |awake: validate an Awake project, and read and edit its scenes.
            |
            |  awake validate [project]                       Check the manifest and every scene.
            |  awake scene list                               List the project's scenes.
            |  awake scene show <scene>                       Print a scene's nodes and components.
            |  awake scene set <scene> <node> <c.field=value> Set a component field, or transform.… or name.
            |  awake scene add-node <scene> <name> [--parent <node>]
            |  awake scene remove-node <scene> <node>
            |  awake scene add-component <scene> <node> <type> [fields-json]
            |  awake scene remove-component <scene> <node> <type>
            |  awake render <scene> [--output <png>]          Play a scene headless and save what its camera sees.
            |  awake mcp                                      Serve these commands to an AI agent over MCP (stdio).
            |
            |  <scene> is a path from the project root, or a name under scenes/. <node> is a path of node
            |  names, such as Player/Camera, with #2 for an unnamed node's index.
            |
            |  --project <dir>  The project (default: the nearest folder up holding awake.project.json).
            |  --json           Output for a program to read.
            |  --dry-run        Report what an edit would change without writing it.
            |  --width, --height <pixels>  The render's size (default 1280 by 720).
            |  --frames <n>     How many frames to play before the picture (default 1).
            |  --backend vulkan|webgpu     The GPU backend (default Vulkan; WebGPU on Windows).
            |  --view <view>    lit, clay, normals, depth, albedo, shadows, joint-weights or wireframe.
            |  --camera <name>  Render from the camera on that node instead of the primary one.
            |
            |Exits 0 on success, 1 when it finds errors or refuses an edit, and 2 for a wrong command line.
            |
        """.trimMargin()
    }
}

/** What an edit changed, as `--json` and `awake mcp` report it. */
internal fun SceneEdit.toJson(dryRun: Boolean): JsonObject = buildJsonObject {
    put("written", !dryRun && changes.isNotEmpty())
    put("rewritten", rewritten)
    putJsonArray("changes") { changes.forEach { add(it) } }
}

/** A validation's findings, as `--json` and `awake mcp` report them. */
internal fun CheckReport.toJson(): JsonObject = buildJsonObject {
    put("valid", errors.isEmpty())
    put("project", projectId)
    put("scenes", sceneCount)
    putJsonArray("findings") {
        findings.forEach { finding ->
            add(
                buildJsonObject {
                    put("severity", finding.severity.name.lowercase())
                    put("message", finding.message)
                    put("file", finding.file)
                    put("path", finding.path)
                    put("code", finding.code)
                },
            )
        }
    }
}

private fun Finding.line(): String =
    listOfNotNull(severity.name.lowercase().padEnd(SEVERITY_WIDTH), file, path?.let { "$it:" }, message).joinToString("  ")

private fun CheckReport.summary(): String {
    val name = projectId ?: "project"
    if (findings.isEmpty()) return "$name: valid, $sceneCount scene${if (sceneCount == 1) "" else "s"}"
    return "$name: ${errors.size} error${plural(errors.size)}, ${warnings.size} warning${plural(warnings.size)} in $sceneCount scenes"
}

private fun plural(count: Int) = if (count == 1) "" else "s"

private const val SEVERITY_WIDTH = 7

/** JSON output, indented for a reader. */
internal val PRETTY_JSON = Json { prettyPrint = true }
