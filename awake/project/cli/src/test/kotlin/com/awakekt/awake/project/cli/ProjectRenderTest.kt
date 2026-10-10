/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `awake render` on a real GPU backend: a project's scene played headless and saved as a PNG.
 *
 * The same scene without its cube is the control. With the cube, the frame's centre differs from
 * the empty scene's by [CUBE_DIFFERENCE] or more, summed over the red, green and blue averages of
 * the centre block; without it, the two empty renders differ by at most [SAME_DIFFERENCE]. Measured
 * on WebGPU (Windows, a discrete GPU): the cube's centre differed by 82, and two empty renders by 0.
 *
 * The clay view is checked by colour spread at the cube, the gap between its highest and lowest
 * channel: 32 lit, where the cube's vertex colours show, and 0 in clay. Each run prints the numbers
 * for each backend; CI runs Vulkan and WebGPU on Mesa's lavapipe.
 */
class ProjectRenderTest {
    private val root: File = createTempDirectory("awake-render").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aSceneRendersWhatItsCameraSeesOnEachBackend() {
        root.resolve("awake.project.json").writeText(MANIFEST)
        root.resolve("scenes").mkdirs()
        root.resolve("scenes/harbor.scene.json").writeText(scene(withCube = true))
        root.resolve("scenes/empty.scene.json").writeText(scene(withCube = false))

        backends().forEach { backend ->
            val harbor = render("harbor", backend)
            val empty = render("empty", backend)
            val emptyAgain = render("empty", backend)

            assertEquals(WIDTH to HEIGHT, harbor.width to harbor.height, "$backend: the size asked for")
            val cube = difference(centre(harbor), centre(empty))
            val same = difference(centre(empty), centre(emptyAgain))
            println("$backend: centre with the cube differs from the empty scene by $cube; two empty renders by $same")
            assertTrue(cube >= CUBE_DIFFERENCE, "$backend: the cube in front of the camera shows at the centre, by $cube")
            assertTrue(same <= SAME_DIFFERENCE, "$backend: a render is repeatable, by $same")
            assertTrue(difference(corner(harbor), corner(empty)) <= SAME_DIFFERENCE, "$backend: the sky at the corner is the same in both")

            // Clay draws every surface one neutral grey, so the cube's vertex colours go.
            val clay = render("harbor", backend, "--view", "clay")
            println("$backend: centre colour spread lit ${spread(centre(harbor))}, clay ${spread(centre(clay))}")
            assertTrue(spread(centre(clay)) <= CLAY_SPREAD, "$backend: clay is grey at the cube: ${centre(clay)}")
            assertTrue(spread(centre(harbor)) > spread(centre(clay)) + CLAY_SPREAD, "$backend: lit shows the cube's colours: ${centre(harbor)}")

            // From the camera facing away, the cube is out of the picture.
            val away = render("harbor", backend, "--camera", "Away")
            assertTrue(difference(centre(away), centre(harbor)) >= CUBE_DIFFERENCE, "$backend: the Away camera doesn't see the cube")
        }
    }

    private fun render(scene: String, backend: String, vararg options: String): BufferedImage {
        val output = root.resolve("$scene-$backend-${options.joinToString("-")}.png")
        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(
            listOf("render", scene, "--output", output.path, "--backend", backend, "--width", "$WIDTH", "--height", "$HEIGHT") + options,
        )
        assertEquals(0, code, "awake render $scene on $backend: $out$err")
        return ImageIO.read(output)
    }

    /** The average red, green and blue of a block at the frame's centre. */
    private fun centre(image: BufferedImage) = average(image, image.width / 2, image.height / 2)

    private fun corner(image: BufferedImage) = average(image, BLOCK, BLOCK)

    private fun average(image: BufferedImage, centreX: Int, centreY: Int): Triple<Int, Int, Int> {
        var red = 0
        var green = 0
        var blue = 0
        val half = BLOCK / 2
        for (y in centreY - half until centreY + half) {
            for (x in centreX - half until centreX + half) {
                val rgb = image.getRGB(x, y)
                red += (rgb shr 16) and 0xFF
                green += (rgb shr 8) and 0xFF
                blue += rgb and 0xFF
            }
        }
        val count = BLOCK * BLOCK
        return Triple(red / count, green / count, blue / count)
    }

    /** How far apart a colour's channels are: 0 for a grey. */
    private fun spread(colour: Triple<Int, Int, Int>): Int =
        maxOf(colour.first, colour.second, colour.third) - minOf(colour.first, colour.second, colour.third)

    private fun difference(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
        abs(a.first - b.first) + abs(a.second - b.second) + abs(a.third - b.third)

    private fun backends(): List<String> = System.getProperty("awake.render.backends", "vulkan,webgpu").split(',').map(String::trim)

    private fun scene(withCube: Boolean): String {
        val cube = if (withCube) {
            """, { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 }, "scale": { "x": 3.0, "y": 3.0, "z": 3.0 } },
                "components": [ { "component": "mesh_renderer", "mesh": "cube", "material": "lit-shadow" } ] }"""
        } else {
            ""
        }
        return """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                { "name": "Camera", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 0.0, "z": 8.0 },
                  "center": { "x": 0.0, "y": 0.0, "z": 0.0 }, "fovYDegrees": 45.0, "primary": true } ] },
                { "name": "Away", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 0.0, "z": 8.0 },
                  "center": { "x": 0.0, "y": 0.0, "z": 16.0 }, "fovYDegrees": 45.0, "primary": false } ] },
                { "name": "Sun", "components": [ { "component": "light", "type": "Directional", "intensity": 1.2,
                  "direction": { "x": 0.5, "y": 0.7, "z": 0.4 } } ] },
                { "name": "Sky", "components": [ { "component": "skybox", "enabled": true, "type": "Procedural" } ] }
                $cube
              ]
            }
        """.trimIndent()
    }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 96
        const val BLOCK = 8
        const val CUBE_DIFFERENCE = 60
        const val SAME_DIFFERENCE = 6
        const val CLAY_SPREAD = 12

        val MANIFEST = """
            {
              "formatVersion": 1,
              "id": "com.example.harbor",
              "name": "Harbor Town",
              "version": "1.0.0",
              "entryScene": "scenes/harbor.scene.json"
            }
        """.trimIndent()
    }
}
