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
     */
    fun run(project: File, textures: List<String>, output: File): List<String> {
        val settingsFile = project.resolve(ProjectTextureSettings.FILE_NAME)
        val settings = if (settingsFile.isFile) ProjectTextureSettings.decode(settingsFile.readText()) else ProjectTextureSettings()
        return textures.map { texture ->
            val source = project.resolve(texture)
            require(source.isFile) { "no texture $texture in ${project.path}" }
            cook(source, texture, settings.forAsset(texture), output)
        }
    }

    private fun cook(source: File, texture: String, settings: TextureCookSettings, output: File): String {
        val target = output.resolve(texture).also { it.parentFile.mkdirs() }
        if (!settings.enabled) {
            source.copyTo(target, overwrite = true)
            return "$texture: opted out, copied unchanged"
        }
        val levels = MipChain.levels(MipChain.decode(source.readBytes()), settings.role, settings.mipmaps)
        val basis = KtxEncoder.basis(levels, settings)
        val written = mutableListOf(write(target, "ktx2", basis))
        settings.mobile.distinct().forEach { format ->
            val bytes = when (format) {
                MobileTextureFormat.Astc4x4 -> KtxEncoder.astc(levels, settings)
                MobileTextureFormat.Etc2Rgba8 -> KtxEncoder.etc2(basis)
            }
            written += write(target, suffix(format), bytes)
        }
        val base = levels.first()
        return "$texture: ${base.width}x${base.height}, ${levels.size} levels, ${settings.role}, " +
            written.joinToString(", ") { (name, size) -> "$name $size bytes" }
    }

    /** Writes [bytes] beside [source], named as it is with [extension] in place of its own. */
    private fun write(source: File, extension: String, bytes: ByteArray): Pair<String, Int> {
        val file = source.resolveSibling("${source.nameWithoutExtension}.$extension")
        file.writeBytes(bytes)
        return file.name to bytes.size
    }

    /** The extension a cooked mobile file in [format] takes. */
    private fun suffix(format: MobileTextureFormat): String = when (format) {
        MobileTextureFormat.Astc4x4 -> "astc.ktx2"
        MobileTextureFormat.Etc2Rgba8 -> "etc2.ktx2"
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
