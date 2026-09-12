/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.testing.comparePixels
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Where a component baseline lives, relative to the owning module. */
private const val BASELINE_ROOT = "src/desktopTest/resources/baselines/components"

/**
 * Set to re-record every baseline this run touches.
 *
 * A system property rather than an environment variable, matching every other golden here --
 * `-DAWAKE_RECORD_SNAPSHOTS=true`, forwarded to the forked test JVM by the owning build file.
 */
private const val RECORD_PROPERTY = "AWAKE_RECORD_SNAPSHOTS"

/**
 * Fails unless this frame rasterizes to exactly the committed baseline named [name].
 *
 * **Exact, with no tolerance.** [comparePixels] defaults to allowing two channels of drift because
 * GPU blending differs between drivers; this path never touches a GPU. The software rasterizer is
 * deterministic, so any difference at all is a real difference and there is no threshold to argue
 * about. A backend capture still wants the default tolerance -- this is the CPU corpus.
 *
 * Baselines are PNG rather than the raw `.rgba` a backend capture writes: a component corpus is
 * hundreds of images, and PNG is both an order of magnitude smaller and reviewable in a diff.
 *
 * Re-recording is not a fix. Set [RECORD_PROPERTY] only after predicting which baselines should
 * move and why; the recorded set is printed so the prediction can be audited against it.
 */
fun ComposeComponentFrame.assertMatchesBaseline(
    name: String,
    width: Int = root.width.coerceAtLeast(1),
    height: Int = root.height.coerceAtLeast(1),
    background: Color = OpaqueBackground,
    font: UiFont? = null,
) {
    val actual = primitives.rasterizeToPixelMap(width, height, background, font)
    val baselineFile = File("$BASELINE_ROOT/$name.png")

    if (System.getProperty(RECORD_PROPERTY) != null) {
        baselineFile.parentFile?.mkdirs()
        ImageIO.write(actual.toBufferedImage(), "PNG", baselineFile)
        println("RECORDED baseline $name (${width}x$height)")
        return
    }

    val expected = readBaseline(name) ?: mismatch(
        "No baseline for \"$name\". Render it once with -D$RECORD_PROPERTY=true and commit " +
            "${baselineFile.path}, or add the component to the coverage debt list.",
    )
    if (expected.width != width || expected.height != height) {
        mismatch(
            "Baseline \"$name\" is ${expected.width}x${expected.height} but the frame is " +
                "${width}x$height. A size change is a real change: confirm it is intended before " +
                "re-recording.",
        )
    }

    val result = comparePixels(actual.pixels, expected.toRgbaBytes(), toleranceParChannel = 0)
    if (result.matches) return

    val reportDirectory = File("build/reports/visual-baselines/$name").apply { mkdirs() }
    ImageIO.write(actual.toBufferedImage(), "PNG", File(reportDirectory, "actual.png"))
    ImageIO.write(expected, "PNG", File(reportDirectory, "expected.png"))
    ImageIO.write(diffImage(actual.toBufferedImage(), expected), "PNG", File(reportDirectory, "diff.png"))
    mismatch(
        "\"$name\" no longer matches its baseline: ${result.diffPixelCount} of ${width * height} " +
            "pixels differ, largest channel change ${result.maxChannelDiff}. Compare " +
            "${reportDirectory.absolutePath}/{expected,actual,diff}.png.",
    )
}

private fun mismatch(message: String): Nothing = throw AssertionError(message)

/** Opaque so a PNG round-trip cannot turn an alpha convention into a false diff. */
private val OpaqueBackground = Color(1f, 1f, 1f, 1f)

/** Read from the classpath so the comparison survives being run from outside the module directory. */
private fun readBaseline(name: String): BufferedImage? =
    object {}.javaClass.getResourceAsStream("/baselines/components/$name.png")?.use(ImageIO::read)

/** RGBA8, the layout [com.awakekt.awake.render.capture.PixelMap] already packs. */
private fun BufferedImage.toRgbaBytes(): ByteArray {
    val bytes = ByteArray(width * height * 4)
    var offset = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)
            bytes[offset] = (argb ushr 16).toByte()
            bytes[offset + 1] = (argb ushr 8).toByte()
            bytes[offset + 2] = argb.toByte()
            bytes[offset + 3] = (argb ushr 24).toByte()
            offset += 4
        }
    }
    return bytes
}

/** Differences in red over a washed-out copy, so a handful of moved pixels is still findable. */
private fun diffImage(actual: BufferedImage, expected: BufferedImage): BufferedImage {
    val image = BufferedImage(actual.width, actual.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until actual.height) {
        for (x in 0 until actual.width) {
            val a = actual.getRGB(x, y)
            val b = expected.getRGB(x, y)
            image.setRGB(x, y, if (a == b) washOut(a) else DIFF_RED)
        }
    }
    return image
}

private const val DIFF_RED = 0xFFFF0000.toInt()

private fun washOut(argb: Int): Int {
    val gray = (((argb ushr 16 and 0xFF) + (argb ushr 8 and 0xFF) + (argb and 0xFF)) / 3) / 4 + 191
    return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}
