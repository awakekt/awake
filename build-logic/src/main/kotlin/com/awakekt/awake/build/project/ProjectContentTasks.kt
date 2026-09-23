/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

abstract class ProjectContentExtension @Inject constructor(objects: ObjectFactory) {
    val manifestFile: RegularFileProperty = objects.fileProperty()
    val assetsLockFile: RegularFileProperty = objects.fileProperty()
    val indexOutputFile: RegularFileProperty = objects.fileProperty()
    val assetRoots: ListProperty<String> = objects.listProperty(String::class.java)
    val indexRoots: ListProperty<String> = objects.listProperty(String::class.java)
    val indexRootPath: Property<String> = objects.property(String::class.java)
    val indexBaseUrl: Property<String> = objects.property(String::class.java)
}

@DisableCachingByDefault(because = "Validates the mutable project directory and has no declared output")
abstract class ValidateProjectTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val assetRoots: ListProperty<String>

    @get:Input
    abstract val indexRoots: ListProperty<String>

    @TaskAction
    fun validate() {
        val root = project.projectDir
        val manifestFile = manifestFile.get().asFile
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val manifest = parseObject(manifestFile, errors, "awake.project.json")
        if (manifest == null) fail(errors, warnings)

        val allowed = setOf(
            "\$schema", "formatVersion", "id", "name", "version", "minEngineVersion",
            "entryScene", "author", "assetRoots", "plugins",
        )
        (manifest!!.keys - allowed).forEach { errors += "manifest has unsupported property: $it" }
        requireString(manifest, "id", errors)
        requireString(manifest, "name", errors)
        requireString(manifest, "version", errors)
        val formatVersion = manifest["formatVersion"]?.jsonPrimitive?.intOrNull
        if (formatVersion != 1) errors += "manifest.formatVersion must be integer 1"

        val entryScene = manifest["entryScene"]?.jsonPrimitive?.contentOrNull
        if (!safePath(entryScene)) {
            errors += "manifest.entryScene must be a safe project-relative path"
        } else if (!root.resolve(entryScene!!).isFile) {
            errors += "manifest.entryScene does not resolve to a project file: $entryScene"
        }

        val declaredRoots = stringArray(manifest["assetRoots"], "manifest.assetRoots", errors)
            .ifEmpty { assetRoots.get() }
        declaredRoots.forEach { path ->
            if (!safePath(path)) errors += "asset root must be a safe project-relative path: $path"
        }

        validateScenes(root, declaredRoots, errors)
        validatePlugins(root, manifest["plugins"], errors, warnings)
        validateAssetsLock(root, declaredRoots, errors, warnings)

        if (errors.isNotEmpty()) fail(errors, warnings)
        warnings.forEach { logger.warn(it) }
        logger.lifecycle("Project valid: ${manifest["id"]?.jsonPrimitive?.contentOrNull}")
    }

    private fun validateScenes(root: File, roots: List<String>, errors: MutableList<String>) {
        root.walkTopDown()
            .onEnter { directory -> directory == root || (!directory.name.startsWith(".") && directory.name != "build") }
            .filter { it.isFile && (it.name.endsWith(".scene.json") || it.name.endsWith(".awakescene")) }
            .forEach { sceneFile ->
                val relative = sceneFile.relativeTo(root).invariantSeparatorsPath
                val scene = parseObject(sceneFile, errors, relative) ?: return@forEach
                if (scene["version"]?.jsonPrimitive?.intOrNull != 1) errors += "$relative.version must be integer 1"
                if (scene["name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "$relative.name must not be blank"
                val nodes = scene["nodes"] as? JsonArray ?: run {
                    errors += "$relative.nodes must be an array"
                    return@forEach
                }
                nodes.forEachIndexed { nodeIndex, node ->
                    val components = (node as? JsonObject)?.get("components") as? JsonArray ?: return@forEachIndexed
                    components.forEachIndexed { componentIndex, component ->
                        val componentObject = component as? JsonObject ?: return@forEachIndexed
                        listOf("mesh", "manifest", "texture", "asset", "material").forEach { field ->
                            val value = componentObject[field]?.jsonPrimitive?.contentOrNull
                            if (value != null && value.contains('/') && safePath(value)) {
                                if (!underRoot(value, roots)) {
                                    errors += "$relative.nodes[$nodeIndex].components[$componentIndex].$field is outside asset roots: $value"
                                } else if (!root.resolve(value).isFile) {
                                    errors += "$relative.nodes[$nodeIndex].components[$componentIndex].$field is missing: $value"
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun validatePlugins(root: File, value: JsonElement?, errors: MutableList<String>, warnings: MutableList<String>) {
        val plugins = value as? JsonArray ?: return
        plugins.forEachIndexed { index, item ->
            val plugin = item as? JsonObject ?: run {
                errors += "manifest.plugins[$index] must be an object"
                return@forEachIndexed
            }
            val path = plugin["path"]?.jsonPrimitive?.contentOrNull
            if (!safePath(path)) {
                errors += "manifest.plugins[$index].path must be safe"
            } else if (!root.resolve(path!!).isFile) {
                val required = plugin["required"]?.jsonPrimitive?.booleanOrNull == true
                if (required) {
                    errors += "missing required plugin: $path"
                } else {
                    warnings += "missing optional plugin: $path"
                }
            }
        }
    }

    private fun validateAssetsLock(root: File, roots: List<String>, errors: MutableList<String>, warnings: MutableList<String>) {
        val lockFile = root.resolve("assets.lock.json")
        if (!lockFile.isFile) return
        val lock = parseObject(lockFile, errors, "assets.lock.json") ?: return
        if (lock["formatVersion"]?.jsonPrimitive?.intOrNull != 1) errors += "assets.lock.json.formatVersion must be integer 1"
        val assets = lock["assets"] as? JsonObject ?: run {
            errors += "assets.lock.json.assets must be an object"
            return
        }
        assets.forEach { (path, pinValue) ->
            if (!safePath(path) || !underRoot(path, roots)) errors += "asset lock path is outside asset roots: $path"
            if (!root.resolve(path).isFile) warnings += "asset lock entry is missing from the project: $path"
            val pin = pinValue as? JsonObject
            val sha = pin?.get("sha256")?.jsonPrimitive?.contentOrNull
            if (sha == null || !sha.matches(SHA256)) errors += "asset lock entry has invalid sha256: $path"
            val size = pin?.get("sizeBytes")?.jsonPrimitive?.longOrNull
            if (size != null && size < 0) errors += "asset lock entry has negative sizeBytes: $path"
        }
    }

    private fun requireString(jsonObject: JsonObject, name: String, errors: MutableList<String>) {
        if (jsonObject[name]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "manifest.$name must be a non-empty string"
    }

    private fun stringArray(value: JsonElement?, label: String, errors: MutableList<String>): List<String> {
        val array = value as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull ?: run {
                errors += "$label must contain only strings"
                null
            }
        }
    }

    private fun parseObject(file: File, errors: MutableList<String>, label: String): JsonObject? = runCatching {
        Json.parseToJsonElement(file.readText()).jsonObject
    }.getOrElse {
        errors += "cannot parse $label: ${it.message}"
        null
    }

    private fun fail(errors: List<String>, warnings: List<String>): Nothing {
        throw GradleException(
            buildString {
                appendLine("Awake project validation failed:")
                errors.forEach { appendLine("  ERROR: $it") }
                warnings.forEach { appendLine("  WARN: $it") }
            },
        )
    }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

@DisableCachingByDefault(because = "Reads and hashes the mutable project asset tree")
abstract class VerifyAssetsLockTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsLockFile: RegularFileProperty

    @get:Input
    abstract val assetRoots: ListProperty<String>

    @TaskAction
    fun verify() {
        val root = project.projectDir
        val lock = Json.parseToJsonElement(assetsLockFile.get().asFile.readText()).jsonObject
        val assets = lock["assets"]?.jsonObject ?: throw GradleException("assets.lock.json.assets must be an object")
        val failures = mutableListOf<String>()
        assets.forEach { (path, pinValue) ->
            if (!underRoot(path, assetRoots.get())) {
                failures += "$path is outside declared asset roots"
                return@forEach
            }
            val file = root.resolve(path)
            if (!file.isFile) {
                failures += "$path is missing"
                return@forEach
            }
            val pin = pinValue.jsonObject
            val expectedSize = pin["sizeBytes"]?.jsonPrimitive?.longOrNull
            val actualSize = file.length()
            if (expectedSize != null && expectedSize != actualSize) failures += "$path size differs: expected $expectedSize, actual $actualSize"
            val expectedHash = pin["sha256"]?.jsonPrimitive?.contentOrNull
            val actualHash = sha256(file)
            if (expectedHash != actualHash) failures += "$path hash differs: expected $expectedHash, actual $actualHash"
        }
        if (failures.isNotEmpty()) throw GradleException("Asset lock verification failed:\n" + failures.joinToString("\n") { "  $it" })
        logger.lifecycle("Asset lock verified: ${assets.size} files")
    }
}

@DisableCachingByDefault(because = "Scans the mutable project asset tree to generate a lock file")
abstract class GenerateAssetsLockTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val assetRoots: ListProperty<String>

    @TaskAction
    fun generate() {
        val root = project.projectDir
        val entries = root.walkTopDown()
            .filter { it.isFile && assetRoots.get().any { assetRoot -> it.relativeTo(root).invariantSeparatorsPath.startsWith("$assetRoot/") } }
            .filterNot { it.name == ".DS_Store" }
            .map { file ->
                file.relativeTo(root).invariantSeparatorsPath to buildJsonObject {
                    put("sha256", sha256(file))
                    put("sizeBytes", file.length())
                }
            }
            .toList()
            .sortedBy { it.first }
        val document = buildJsonObject {
            put("\$schema", "https://studio.awakekt.com/schemas/v1/assets.lock.schema.json")
            put("formatVersion", 1)
            put("assets", buildJsonObject { entries.forEach { (path, pin) -> put(path, pin) } })
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document) + "\n")
        }
        logger.lifecycle("Generated asset lock: ${entries.size} files")
    }
}

@DisableCachingByDefault(because = "Scans the mutable project tree to generate an index")
abstract class GenerateProjectIndexTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val indexRoots: ListProperty<String>

    @get:Input
    abstract val indexRootPath: Property<String>

    @get:Input
    abstract val baseUrl: Property<String>

    @TaskAction
    fun generate() {
        val root = project.projectDir
        val files = buildList {
            listOf("awake.project.json", "assets.lock.json").map(root::resolve).filter(File::isFile).forEach(::add)
            indexRoots.get().forEach { relativeRoot ->
                root.resolve(relativeRoot).walkTopDown()
                    .filter(File::isFile)
                    .filterNot { it.name == ".DS_Store" }
                    .forEach(::add)
            }
        }.distinctBy { it.relativeTo(root).invariantSeparatorsPath }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        val baseUrl = baseUrl.get().trimEnd('/')
        val document = buildJsonObject {
            put("formatVersion", 2)
            put("rootPath", indexRootPath.get())
            put("files", buildJsonArray {
                files.forEach { file ->
                    val path = file.relativeTo(root).invariantSeparatorsPath
                    add(buildJsonObject {
                        put("path", path)
                        put("sizeBytes", file.length())
                        put("sha256", sha256(file))
                        put("url", "$baseUrl/${path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, Charsets.UTF_8) }}")
                    })
                }
            })
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document) + "\n")
        }
        logger.lifecycle("Generated project index: ${files.size} files")
    }
}

private fun safePath(path: String?): Boolean = path != null && path.isNotBlank() &&
    !path.startsWith('/') && !path.matches(Regex("^[A-Za-z]:.*")) &&
    '\\' !in path && path.split('/').none { it.isBlank() || it == "." || it == ".." }

private fun underRoot(path: String, roots: List<String>): Boolean = roots.any { path == it || path.startsWith("$it/") }

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
