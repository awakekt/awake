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
 * - `textures/rock.ktx2`, Basis Universal, which desktop and web transcode to the GPU's format;
 * - `textures/rock.astc.ktx2` and `textures/rock.etc2.ktx2`, the mobile formats the settings ask for.
 */
object TextureCook {
    /**
     * Cooks each of [textures], paths relative to [project], into the same relative path under [output].
     * Returns one line per texture.
     *
     * Every path must stay inside [project], and no file the run writes may be a source or another
     * texture's output (`rock.png` and `rock.jpg` would both write `rock.ktx2`). A run that breaks
     * either is refused before anything is written.
     */
    fun run(project: File, textures: List<String>, output: File): List<String> {
        val settingsFile = project.resolve(ProjectTextureSettings.FILE_NAME)
        val settings = if (settingsFile.isFile) ProjectTextureSettings.decode(settingsFile.readText()) else ProjectTextureSettings()
        val sources = textures.map { texture -> source(project, texture) }
        val planned = mutableMapOf<File, String>()
        textures.forEach { texture ->
            outputs(output.resolve(texture), settings.forAsset(texture)).forEach { file ->
                val written = file.canonicalFile
                require(written !in sources) { "$texture would overwrite the source ${written.path}" }
                val other = planned.put(written, texture)
                require(other == null) { "$texture and $other both write ${written.path}" }
            }
        }
        return textures.mapIndexed { i, texture -> cook(sources[i], texture, settings.forAsset(texture), output) }
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
    private fun outputs(target: File, settings: TextureCookSettings): List<File> =
        if (!settings.enabled) {
            listOf(target)
        } else {
            (listOf(MobileTextureFormat.BASIS_EXTENSION) + settings.mobile.distinct().map { it.extension }).map { sibling(target, it) }
        }

    private fun sibling(target: File, extension: String): File = target.resolveSibling("${target.nameWithoutExtension}.$extension")

    private fun cook(source: File, texture: String, settings: TextureCookSettings, output: File): String {
        val target = output.resolve(texture).also { it.parentFile.mkdirs() }
        if (!settings.enabled) {
            source.copyTo(target, overwrite = true)
            return "$texture: opted out, copied unchanged"
        }
        val levels = MipChain.levels(MipChain.decode(source.readBytes()), settings.role, settings.mipmaps)
        val basis = KtxEncoder.basis(levels, settings)
        val written = mutableListOf(write(target, MobileTextureFormat.BASIS_EXTENSION, basis))
        settings.mobile.distinct().forEach { format ->
            val bytes = when (format) {
                MobileTextureFormat.Astc4x4 -> KtxEncoder.astc(levels, settings)
                MobileTextureFormat.Etc2Rgba8 -> KtxEncoder.etc2(basis)
            }
            written += write(target, format.extension, bytes)
        }
        val base = levels.first()
        return "$texture: ${base.width}x${base.height}, ${levels.size} levels, ${settings.role}, " +
            written.joinToString(", ") { (name, size) -> "$name $size bytes" }
    }

    /** Writes [bytes] beside [source], named as it is with [extension] in place of its own. */
    private fun write(source: File, extension: String, bytes: ByteArray): Pair<String, Int> {
        val file = sibling(source, extension)
        file.writeBytes(bytes)
        return file.name to bytes.size
    }
}

/** The `cookTextures` task's entry point: `<project> <texture> [<texture> ...] --output <dir>`. */
object TextureCookMain {
    /** Cooks the textures [args] names; see [TextureCook.run]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val outputFlag = args.indexOf("--output")
        require(outputFlag >= 0 && outputFlag + 1 < args.size && args.size >= MIN_ARGS) {
            "Usage: <project> <texture> [<texture> ...] --output <dir>"
        }
        val positional = args.filterIndexed { i, _ -> i != outputFlag && i != outputFlag + 1 }
        TextureCook.run(File(positional[0]), positional.drop(1), File(args[outputFlag + 1])).forEach(::println)
    }

    private const val MIN_ARGS = 4
}
