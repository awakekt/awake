/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.MobileTextureFormat
import com.awakekt.awake.asset.ktx.ProjectTextureSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO
import kotlin.math.log10

/**
 * What cooking a project's textures saves, measured (#1211): download sizes against the source and
 * against a lossless PNG of the same pixels, the GPU memory each upload takes, and how far what the GPU
 * samples is from the source. Each texture is cooked with the project's `awake.textures.json`.
 */
object TextureSizeReport {

    /** One texture's measurements; byte counts with gzip applied, as a web server sends them. */
    @Suppress("LongParameterList") // One parameter per value measured; grouping them hides which.
    class Row(
        val texture: String,
        val width: Int,
        val height: Int,
        val source: Long,
        val png: Long,
        val basis: Long,
        val mobile: Map<MobileTextureFormat, Long>,
        val psnr: Double,
    ) {
        /** How many times smaller the Basis Universal download is than the lossless PNG. */
        val pngRatio: Double get() = png.toDouble() / basis
    }

    /** Cooks [textures], paths in [project], into [output] and measures each. */
    fun measure(project: File, textures: List<String>, output: File): List<Row> {
        TextureCook.run(project, textures, output)
        val settings = project.resolve(ProjectTextureSettings.FILE_NAME).takeIf(File::isFile)
            ?.let { ProjectTextureSettings.decode(it.readText()) } ?: ProjectTextureSettings()
        return textures.map { texture ->
            val sourceBytes = project.resolve(texture).readBytes()
            val image = MipChain.decode(sourceBytes)
            val cooked = output.resolve(texture)
            val basis = cooked.resolveSibling("${cooked.nameWithoutExtension}.${MobileTextureFormat.BASIS_EXTENSION}").readBytes()
            Row(
                texture = texture,
                width = image.width,
                height = image.height,
                source = gzipped(sourceBytes),
                png = gzipped(losslessPng(project.resolve(texture))),
                basis = gzipped(basis),
                mobile = settings.forAsset(texture).mobile.distinct().associateWith { format ->
                    gzipped(cooked.resolveSibling("${cooked.nameWithoutExtension}.${format.extension}").readBytes())
                },
                psnr = psnr(image, KtxEncoder.decodeBase(basis)),
            )
        }
    }

    /**
     * GPU memory for a [width] x [height] texture with its full mip chain, down to 1 x 1: in RGBA8 when
     * [blockBytes] is null, or in a 4 x 4 block format whose blocks are [blockBytes] each (16 for BC7,
     * BC3, ETC2 and ASTC 4x4; 8 for BC1), a level smaller than a block taking one.
     */
    fun gpuBytes(width: Int, height: Int, blockBytes: Int? = null): Long {
        var total = 0L
        var w = width
        var h = height
        while (true) {
            total += if (blockBytes == null) w.toLong() * h * RGBA8 else ((w + BLOCK - 1) / BLOCK).toLong() * ((h + BLOCK - 1) / BLOCK) * blockBytes
            if (w == 1 && h == 1) return total
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
        }
    }

    /** [rows] as a Markdown table, with totals. */
    fun markdown(rows: List<Row>): String = buildString {
        val formats = rows.flatMap { it.mobile.keys }.distinct()
        append("| Texture | Size | Source | Lossless PNG | KTX2 (Basis) | vs PNG | ")
        formats.forEach { append("${it.extension} | ") }
        append("RGBA8 in VRAM | BC7/ETC2/ASTC in VRAM | PSNR |\n|")
        repeat(COLUMNS + formats.size) { append(" --- |") }
        append('\n')
        rows.forEach { row ->
            append("| `${row.texture}` | ${row.width}x${row.height} | ${kib(row.source)} | ${kib(row.png)} | ${kib(row.basis)} | ")
            append("%.1fx | ".format(row.pngRatio))
            formats.forEach { append("${row.mobile[it]?.let(::kib) ?: "-"} | ") }
            append("${kib(gpuBytes(row.width, row.height))} | ${kib(gpuBytes(row.width, row.height, BLOCK_BYTES))} | ")
            append("%.1f dB |\n".format(row.psnr))
        }
        val png = rows.sumOf { it.png }
        val basis = rows.sumOf { it.basis }
        append("| **All** | | ${kib(rows.sumOf { it.source })} | ${kib(png)} | ${kib(basis)} | ")
        append("%.1fx | ".format(png.toDouble() / basis))
        formats.forEach { format -> append("${kib(rows.sumOf { it.mobile[format] ?: 0 })} | ") }
        append("${kib(rows.sumOf { gpuBytes(it.width, it.height) })} | ${kib(rows.sumOf { gpuBytes(it.width, it.height, BLOCK_BYTES) })} | |\n")
    }

    private fun losslessPng(source: File): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(ImageIO.read(source), "png", it) }.toByteArray()

    private fun gzipped(bytes: ByteArray): Long =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.size().toLong()

    /** Peak signal-to-noise ratio of [decoded] against [source] over red, green and blue, in dB. */
    private fun psnr(source: RgbaImage, decoded: RgbaImage): Double {
        var squared = 0.0
        for (i in source.pixels.indices) {
            if (i % CHANNELS == CHANNELS - 1) continue
            val error = (source.pixels[i].toInt() and BYTE) - (decoded.pixels[i].toInt() and BYTE)
            squared += error * error
        }
        return TEN * log10(PEAK * PEAK / (squared / (source.width * source.height * (CHANNELS - 1))))
    }

    private fun kib(bytes: Long): String = "%.0f KiB".format(bytes / KIB)

    private const val RGBA8 = 4
    private const val BLOCK = 4
    private const val BLOCK_BYTES = 16
    private const val COLUMNS = 9
    private const val BYTE = 0xFF
    private const val PEAK = 255.0
    private const val TEN = 10
    private const val KIB = 1024.0
}

/** The `textureSizeReport` task's entry point: `<project> <texture> [<texture> ...] --output <dir>`. */
object TextureSizeReportMain {
    /** Prints the report for the textures [args] names; see [TextureSizeReport.measure]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val outputFlag = args.indexOf("--output")
        require(outputFlag >= 0 && outputFlag + 1 < args.size && args.size >= MIN_ARGS) {
            "Usage: <project> <texture> [<texture> ...] --output <dir>"
        }
        val positional = args.filterIndexed { i, _ -> i != outputFlag && i != outputFlag + 1 }
        print(TextureSizeReport.markdown(TextureSizeReport.measure(File(positional[0]), positional.drop(1), File(args[outputFlag + 1]))))
    }

    private const val MIN_ARGS = 4
}
