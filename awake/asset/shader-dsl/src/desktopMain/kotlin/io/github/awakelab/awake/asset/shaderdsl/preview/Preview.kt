/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl.preview

import io.github.awakelab.awake.asset.shaderdsl.AslEvaluator
import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.CheckerShader

private const val DEFAULT_WIDTH = 64
private const val DEFAULT_HEIGHT = 32
private const val ESC = '\u001B'

/**
 * Headless shader preview: CPU-evaluates the checker fragment per pixel and prints the image
 * as ANSI true-color half-blocks -- two pixel rows per character row. Terminal only; nothing
 * is written to disk and no UI or GPU dependency is involved.
 *
 * Args: `[width] [height] [--wgsl] [--probe <u> <v>]` -- `--wgsl` also prints the emitted
 * WGSL source; `--probe` dumps every intermediate `let` at one UV instead of an image.
 */
fun main(args: Array<String>) {
    val numbers = args.mapNotNull { it.toIntOrNull() }
    val width = numbers.getOrNull(0) ?: DEFAULT_WIDTH
    val height = numbers.getOrNull(1) ?: DEFAULT_HEIGHT
    if ("--wgsl" in args) {
        println(CheckerShader.emitWgsl())
    }
    val uniforms = mapOf(
        "uniforms.tiles" to floatArrayOf(8f, 0f, 0f, 0f),
        "uniforms.colorA" to floatArrayOf(0.9f, 0.2f, 0.3f, 1f),
        "uniforms.colorB" to floatArrayOf(0.15f, 0.15f, 0.2f, 1f),
    )
    val probeIndex = args.indexOf("--probe")
    if (probeIndex >= 0) {
        val u = args.getOrNull(probeIndex + 1)?.toFloatOrNull() ?: 0.5f
        val v = args.getOrNull(probeIndex + 2)?.toFloatOrNull() ?: 0.5f
        printProbe(CheckerShader, uniforms, u, v)
    } else {
        print(renderAnsi(CheckerShader, uniforms, width, height))
    }
}

/** One pixel's full variable view -- what a GPU debugger's pixel history shows, minus the GPU. */
private fun printProbe(
    definition: AslShaderDefinition,
    uniforms: Map<String, FloatArray>,
    u: Float,
    v: Float,
) {
    val trace = AslEvaluator.traceFragment(
        definition,
        uniforms + ("uv" to floatArrayOf(u, v)),
    )
    fun fmt(values: FloatArray) = values.joinToString(prefix = "[", postfix = "]")
    println("probe '${definition.name}' at uv=($u, $v)")
    trace.lets.forEach { (name, value) -> println("  let $name = ${fmt(value)}") }
    println("  color -> ${fmt(trace.color)}")
}

internal fun renderAnsi(
    definition: AslShaderDefinition,
    uniforms: Map<String, FloatArray>,
    width: Int,
    height: Int,
): String = buildString {
    val rows = height - height % 2
    fun pixel(x: Int, y: Int): String {
        val inputs = uniforms + ("uv" to floatArrayOf((x + 0.5f) / width, (y + 0.5f) / rows))
        val rgba = AslEvaluator.evalFragment(definition, inputs)
        fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f).toInt()
        return "${channel(rgba[0])};${channel(rgba[1])};${channel(rgba[2])}"
    }
    for (top in 0 until rows step 2) {
        for (x in 0 until width) {
            append("$ESC[38;2;${pixel(x, top)}m$ESC[48;2;${pixel(x, top + 1)}m▀")
        }
        append("$ESC[0m\n")
    }
}
