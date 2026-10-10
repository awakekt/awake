/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import com.awakekt.awake.project.runtime.registerProjectComponents
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A project's files and the scene codec for them: Core's scene components, registered as a played
 * project registers them, with a component nothing installed provides kept as data. A game's own
 * components are such data here, since `awake` doesn't link the game's code.
 *
 * @property root The project's root directory, where [PROJECT_MANIFEST] lives.
 */
internal class ProjectScenes(val root: File) {
    val json: Json by lazy {
        SceneComponentRegistry.scoped().registerProjectComponents().sceneJson(keepUnknownComponents = true)
    }

    /** The manifest's file, which may not exist. */
    val manifestFile: File get() = root.resolve(PROJECT_MANIFEST)

    /** Every scene file in the project, by path, skipping hidden and build directories. */
    fun files(): List<File> = root.walkTopDown()
        .onEnter { directory -> directory == root || directory.name !in SKIPPED && !directory.name.startsWith(".") }
        .filter { it.isFile && SCENE_EXTENSIONS.any(it.name::endsWith) }
        .sortedBy(::relative)
        .toList()

    /** [file]'s path from the project root, with forward slashes. */
    fun relative(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    /** The scene file [scene] names: a path from the project root, or a name under `scenes/`. */
    fun resolve(scene: String): File = listOf(root.resolve(scene), root.resolve("scenes/$scene.scene.json"))
        .firstOrNull(File::isFile)
        ?: throw UsageException("no scene '$scene' in ${root.path}")

    /** [file] decoded, or a failure naming what's wrong with it. */
    fun read(file: File): SceneDocument = try {
        SceneLoader.decode(file.readText(), json)
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        // Any decode failure is a scene this file doesn't hold: the codec throws several kinds.
        throw CommandFailure("${relative(file)} can't be read as a scene: ${error.message}", error)
    }

    /** [file]'s JSON tree as written, which a hand-written scene may spell other ways than the codec does. */
    fun source(file: File): JsonObject = try {
        Json.parseToJsonElement(file.readText()).jsonObject
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        // Not JSON, or JSON that isn't an object.
        throw CommandFailure("${relative(file)} can't be read as JSON: ${error.message}", error)
    }

    /** [document] as the JSON tree the codec writes. */
    fun tree(document: SceneDocument): JsonObject = json.encodeToJsonElement(SceneDocument.serializer(), document).jsonObject

    /**
     * [tree] decoded back into a scene, which checks every field's type. It goes through the loader, as
     * a file does, so a component's old name reads as its current one.
     */
    fun decode(tree: JsonObject): SceneDocument = SceneLoader.decode(tree.toString(), json)

    /**
     * Writes [tree] to [file] laid out as [file] already is: on one line as Studio saves a scene, or
     * indented as the file is, with its line endings and final newline. It goes through a temporary
     * file, so a failure leaves the scene as it was.
     */
    fun write(file: File, tree: JsonObject) {
        val original = file.readText()
        val lineEnd = if ("\r\n" in original) "\r\n" else "\n"
        val encoded = styledLike(original).encodeToString(JsonObject.serializer(), tree)
        val text = encoded.replace("\n", lineEnd) + if (original.endsWith("\n")) lineEnd else ""
        val temporary = File.createTempFile(".${file.name}", ".tmp", file.parentFile)
        try {
            temporary.writeText(text)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun styledLike(original: String): Json {
        if ('\n' !in original.trim()) return json
        val indent = original.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.takeWhile { it == ' ' }.orEmpty()
        return Json(json) {
            prettyPrint = true
            prettyPrintIndent = indent.ifEmpty { DEFAULT_INDENT }
        }
    }

    private companion object {
        val SCENE_EXTENSIONS = listOf(".scene.json", ".awakescene")
        val SKIPPED = setOf("build", "node_modules")
        const val DEFAULT_INDENT = "  "
    }
}
