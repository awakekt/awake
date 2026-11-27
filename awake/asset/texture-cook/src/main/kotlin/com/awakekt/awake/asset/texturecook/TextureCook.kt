/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.MobileTextureFormat
import com.awakekt.awake.asset.ktx.ProjectTextureSettings
import com.awakekt.awake.asset.ktx.TextureCookSettings
import java.io.File

/**
 * Cooks a project's textures to KTX2 with the project's `awake.textures.json` settings (defaults when
 * it has none) and each texture's own override or opt-out.
 *
 * For a source `textures/rock.png` it writes, under the output directory:
 * - `textures/rock.ktx2`, Basis Universal, which desktop and web transcode to the GPU's format
 *   (omitted when `mobileOnly = true` so mobile builds do not bundle redundant Basis files);
 * - `textures/rock.astc.ktx2` and `textures/rock.etc2.ktx2`, the mobile formats the settings ask for.
 */
@Suppress("TooManyFunctions") // Single offline cooker covering discovery, incremental 2D cooking, and array cooking.
object TextureCook {
    private val SOURCE_EXTENSIONS = setOf("png", "jpg", "jpeg")
    private val IGNORED_DIRECTORIES = setOf("build", ".git", ".gradle", ".idea")

    /**
     * Expands [paths] (relative files or directories inside [project], or the whole [project] when
     * empty) into a sorted list of project-relative PNG/JPEG texture paths, skipping hidden and
     * build directories.
     */
    fun discover(project: File, paths: List<String> = emptyList()): List<String> {
        val root = project.canonicalFile
        val targets = if (paths.isEmpty()) {
            listOf(root)
        } else {
            paths.map { rel ->
                val relative = File(rel)
                val resolved = root.resolve(rel).canonicalFile
                require(!relative.isAbsolute && !relative.normalize().path.startsWith("..") && resolved.startsWith(root)) {
                    "$rel is not a path inside ${project.path}"
                }
                require(resolved.exists()) { "no texture or directory $rel in ${project.path}" }
                resolved
            }
        }
        val discovered = linkedSetOf<String>()
        for (entry in targets) {
            if (entry.isFile) {
                discovered += entry.relativeTo(root).path.replace('\\', '/')
            } else {
                entry.walkTopDown()
                    .onEnter { dir -> dir == entry || (!dir.name.startsWith('.') && dir.name !in IGNORED_DIRECTORIES) }
                    .filter { file -> file.isFile && file.extension.lowercase() in SOURCE_EXTENSIONS }
                    .forEach { file -> discovered += file.relativeTo(root).path.replace('\\', '/') }
            }
        }
        return discovered.sorted()
    }

    /**
     * Cooks each of [textures], paths relative to [project], into the same relative path under [output].
     * Returns one line per texture.
     *
     * Every path must stay inside [project], and no file the run writes may be a source or another
     * texture's output (`rock.png` and `rock.jpg` would both write `rock.ktx2`). A run that breaks
     * either is refused before anything is written.
     */
    fun run(project: File, textures: List<String>, output: File): List<String> =
        run(project, textures, output, incremental = false, mobileOnly = false)

    /**
     * Cooks each of [textures] (or directories discovered via [discover]) under [output], optionally
     * skipping textures whose outputs are already up to date ([incremental]) and/or writing only
     * mobile `.astc.ktx2`/`.etc2.ktx2` siblings ([mobileOnly]).
     */
    fun run(
        project: File,
        textures: List<String>,
        output: File,
        incremental: Boolean,
        mobileOnly: Boolean = false,
    ): List<String> {
        val settingsFile = project.resolve(ProjectTextureSettings.FILE_NAME)
        val settings = if (settingsFile.isFile) ProjectTextureSettings.decode(settingsFile.readText()) else ProjectTextureSettings()
        val settingsTimestamp = if (settingsFile.isFile) settingsFile.lastModified() else 0L
        val sources = textures.map { texture -> source(project, texture) }
        val planned = mutableMapOf<File, String>()
        textures.forEach { texture ->
            outputs(output.resolve(texture), settings.forAsset(texture), mobileOnly).forEach { file ->
                val written = file.canonicalFile
                require(written !in sources) { "$texture would overwrite the source ${written.path}" }
                val other = planned.put(written, texture)
                require(other == null) { "$texture and $other both write ${written.path}" }
            }
        }
        return textures.mapIndexed { i, texture ->
            cook(sources[i], texture, settings.forAsset(texture), output, incremental, settingsTimestamp, mobileOnly)
        }
    }

    /**
     * Cooks an ordered list of equally-sized [layers] (with optional per-layer [heights] packed into
     * each layer's alpha channel, matching `packLayerArray`) into a block-compressed KTX2
     * `Texture2DArray` written to [outputStem] under [output] (`.ktx2`, `.astc.ktx2`, `.etc2.ktx2`).
     */
    @Suppress("LongParameterList") // Offline array-cooking entry point matching 2D cook options plus heights.
    fun cookArray(
        project: File,
        layers: List<String>,
        outputStem: String,
        output: File,
        heights: List<String?> = List(layers.size) { null },
        settings: TextureCookSettings = TextureCookSettings(),
        mobileOnly: Boolean = false,
    ): String {
        require(layers.isNotEmpty()) { "texture array needs at least one layer" }
        require(heights.size == layers.size) { "heights size ${heights.size} must match layers size ${layers.size}" }
        val layerLevels = layers.indices.map { i ->
            val albedo = MipChain.decode(source(project, layers[i]).readBytes())
            val packed = heights[i]?.let { hPath ->
                packHeightInAlpha(albedo, MipChain.decode(source(project, hPath).readBytes()))
            } ?: albedo
            MipChain.levels(packed, settings.role, settings.mipmaps)
        }
        val normalizedStem = outputStem.removeSuffix(".ktx2")
        val target = output.resolve("$normalizedStem.png").also { it.parentFile.mkdirs() }
        val written = mutableListOf<Pair<String, Int>>()
        val formats = settings.mobile.distinct()
        val needsBasis = !mobileOnly || MobileTextureFormat.Etc2Rgba8 in formats
        val basis = if (needsBasis) KtxEncoder.basisArray(layerLevels, settings) else null
        if (!mobileOnly && basis != null) {
            written += write(target, MobileTextureFormat.BASIS_EXTENSION, basis)
        }
        formats.forEach { format ->
            val bytes = when (format) {
                MobileTextureFormat.Astc4x4 -> KtxEncoder.astcArray(layerLevels, settings)
                MobileTextureFormat.Etc2Rgba8 -> KtxEncoder.etc2(checkNotNull(basis))
            }
            written += write(target, format.extension, bytes)
        }
        val base = layerLevels.first().first()
        return "$normalizedStem: ${base.width}x${base.height}x${layers.size}, ${layerLevels.first().size} levels, " +
            "${settings.role}, " + written.joinToString(", ") { (name, size) -> "$name $size bytes" }
    }

    /** Packs [height]'s red channel into [albedo]'s alpha channel so a terrain layer array keeps height blend data. */
    private fun packHeightInAlpha(albedo: RgbaImage, height: RgbaImage): RgbaImage {
        require(albedo.width == height.width && albedo.height == height.height) {
            "layer height ${height.width}x${height.height} must match albedo ${albedo.width}x${albedo.height}"
        }
        val out = albedo.pixels.copyOf()
        var i = 0
        while (i < out.size) {
            out[i + 3] = height.pixels[i]
            i += CHANNELS
        }
        return RgbaImage(albedo.width, albedo.height, out)
    }

    /** The source [texture] names: a file inside [project]. */
    private fun source(project: File, texture: String): File {
        val root = project.canonicalFile
        val source = root.resolve(texture).canonicalFile
        val relative = File(texture)
        require(!relative.isAbsolute && !relative.normalize().path.startsWith("..") && source.startsWith(root)) { "$texture is not a path inside ${project.path}" }
        require(source.isFile) { "no texture $texture in ${project.path}" }
        return source
    }

    /** The files cooking to [target] with [settings] writes. */
    private fun outputs(target: File, settings: TextureCookSettings, mobileOnly: Boolean = false): List<File> =
        if (!settings.enabled) {
            listOf(target)
        } else {
            val extensions = buildList {
                if (!mobileOnly) add(MobileTextureFormat.BASIS_EXTENSION)
                settings.mobile.distinct().forEach { add(it.extension) }
            }
            extensions.map { sibling(target, it) }
        }

    private fun sibling(target: File, extension: String): File = target.resolveSibling("${target.nameWithoutExtension}.$extension")

    @Suppress("LongParameterList") // Internal cook step threading incremental timestamp and mobile-only flag.
    private fun cook(
        source: File,
        texture: String,
        settings: TextureCookSettings,
        output: File,
        incremental: Boolean,
        settingsTimestamp: Long,
        mobileOnly: Boolean,
    ): String {
        val target = output.resolve(texture).also { it.parentFile.mkdirs() }
        val expectedOutputs = outputs(target, settings, mobileOnly)
        return when {
            incremental && isUpToDate(source, settingsTimestamp, expectedOutputs) -> "$texture: up-to-date, skipped"
            !settings.enabled -> {
                source.copyTo(target, overwrite = true)
                "$texture: opted out, copied unchanged"
            }
            else -> cookEncoded(source, texture, settings, target, mobileOnly)
        }
    }

    private fun cookEncoded(
        source: File,
        texture: String,
        settings: TextureCookSettings,
        target: File,
        mobileOnly: Boolean,
    ): String {
        val levels = MipChain.levels(MipChain.decode(source.readBytes()), settings.role, settings.mipmaps)
        val formats = settings.mobile.distinct()
        val needsBasis = !mobileOnly || MobileTextureFormat.Etc2Rgba8 in formats
        val basis = if (needsBasis) KtxEncoder.basis(levels, settings) else null
        val written = mutableListOf<Pair<String, Int>>()
        if (!mobileOnly && basis != null) {
            written += write(target, MobileTextureFormat.BASIS_EXTENSION, basis)
        }
        formats.forEach { format ->
            val bytes = when (format) {
                MobileTextureFormat.Astc4x4 -> KtxEncoder.astc(levels, settings)
                MobileTextureFormat.Etc2Rgba8 -> KtxEncoder.etc2(checkNotNull(basis))
            }
            written += write(target, format.extension, bytes)
        }
        val base = levels.first()
        return "$texture: ${base.width}x${base.height}, ${levels.size} levels, ${settings.role}, " +
            written.joinToString(", ") { (name, size) -> "$name $size bytes" }
    }

    private fun isUpToDate(source: File, settingsTimestamp: Long, outputs: List<File>): Boolean {
        if (outputs.isEmpty()) return false
        val newestInput = maxOf(source.lastModified(), settingsTimestamp)
        return outputs.all { it.isFile && it.lastModified() >= newestInput }
    }

    /** Writes [bytes] beside [source], named as it is with [extension] in place of its own. */
    private fun write(source: File, extension: String, bytes: ByteArray): Pair<String, Int> {
        val file = sibling(source, extension)
        file.writeBytes(bytes)
        return file.name to bytes.size
    }
}

/**
 * The `cookTextures` task's entry point:
 * `<project> [<texture-or-dir> ...] --output <dir> [--all] [--incremental] [--mobile-only] [--array <stem>]`.
 */
object TextureCookMain {
    /** Cooks the textures [args] names; see [TextureCook.run] and [TextureCook.cookArray]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val parsed = parseArgs(args)
        if (parsed.arrayStem != null) {
            val settingsFile = parsed.project.resolve(ProjectTextureSettings.FILE_NAME)
            val settings = if (settingsFile.isFile) ProjectTextureSettings.decode(settingsFile.readText()).defaults else TextureCookSettings()
            println(TextureCook.cookArray(parsed.project, parsed.inputs, parsed.arrayStem, parsed.output, settings = settings, mobileOnly = parsed.mobileOnly))
            return
        }
        val textures = if (parsed.all) {
            TextureCook.discover(parsed.project)
        } else {
            TextureCook.discover(parsed.project, parsed.inputs)
        }
        TextureCook.run(parsed.project, textures, parsed.output, parsed.incremental, parsed.mobileOnly).forEach(::println)
    }

    private class ParsedArgs(
        val project: File,
        val inputs: List<String>,
        val output: File,
        val all: Boolean,
        val incremental: Boolean,
        val mobileOnly: Boolean,
        val arrayStem: String?,
    )

    private fun parseArgs(args: Array<String>): ParsedArgs {
        var output: String? = null
        var arrayStem: String? = null
        var all = false
        var incremental = false
        var mobileOnly = false
        val positional = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--output" -> {
                    require(i + 1 < args.size) { "--output requires a directory" }
                    output = args[++i]
                }
                "--array" -> {
                    require(i + 1 < args.size) { "--array requires an output path stem" }
                    arrayStem = args[++i]
                }
                "--all" -> all = true
                "--incremental" -> incremental = true
                "--mobile-only" -> mobileOnly = true
                else -> positional += args[i]
            }
            i++
        }
        require(output != null && positional.isNotEmpty() && (all || positional.size >= 2)) {
            "Usage: <project> [<texture-or-dir> ...] --output <dir> [--all] [--incremental] [--mobile-only] [--array <stem>]"
        }
        return ParsedArgs(File(positional[0]), positional.drop(1), File(output), all, incremental, mobileOnly, arrayStem)
    }
}
