/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bundled Linux arm64 Jolt native must run on any ARMv8-A CPU, not only on CPUs with SVE.
 *
 * jolt-jni 5.1.0 and 5.2.0 shipped one compiled for SVE2: `CollisionDispatch::sInit` ran SVE
 * instructions unconditionally, so physics init died with SIGILL on Apple silicon (Docker
 * Desktop), Graviton2 and Graviton3, and Ampere Altra. An arm64 CI runner cannot catch that, since
 * its CPU has SVE2, so this reads the native's code instead.
 */
class JoltLinuxArm64NativeTest {
    @Test
    fun theLinuxArm64NativeHasNoSveInstruction() {
        val elf = checkNotNull(javaClass.classLoader.getResourceAsStream(LINUX_ARM64_NATIVE)) {
            "$LINUX_ARM64_NATIVE is not on the desktop runtime classpath"
        }.use { it.readBytes() }
        val code = textSection(elf)

        assertTrue(code.remaining() > MIN_CODE_WORDS, "read ${code.remaining()} words, not the native's code")
        var sve = 0
        while (code.hasRemaining()) if (isSve(code.get())) sve++
        assertEquals(0, sve, "the Linux arm64 Jolt native needs SVE, so it dies with SIGILL on CPUs without it")
    }

    @Test
    fun theScanTellsAnSveInstructionFromABaseOne() {
        assertTrue(isSve(0x05E03923), "mov z3.d, x9")
        assertTrue(isSve(0x25E23040.toInt()), "whilewr p0.d, x2, x2")
        assertFalse(isSve(0xD65F03C0.toInt()), "ret")
        assertFalse(isSve(0x91044042.toInt()), "add x2, x2, #0x110")
    }

    /** An A64 instruction word in the SVE encoding space: bits 28 to 25 are `0010`. */
    private fun isSve(word: Int): Boolean = (word ushr 25) and 0xF == 0b0010

    /** The `.text` section of a little-endian ELF64 file, as instruction words. */
    private fun textSection(elf: ByteArray): java.nio.IntBuffer {
        val file = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
        val headers = file.getLong(0x28).toInt()
        val headerSize = file.getShort(0x3A).toInt()
        val count = file.getShort(0x3C).toInt()
        val names = file.getLong(headers + file.getShort(0x3E) * headerSize + 0x18).toInt()
        for (i in 0 until count) {
            val header = headers + i * headerSize
            val nameAt = names + file.getInt(header)
            val name = String(elf, nameAt, elf.indexOfFirstZeroFrom(nameAt) - nameAt)
            if (name == ".text") {
                val offset = file.getLong(header + 0x18).toInt()
                val size = file.getLong(header + 0x20).toInt()
                return file.slice(offset, size).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            }
        }
        error("no .text section")
    }

    private fun ByteArray.indexOfFirstZeroFrom(start: Int): Int = (start until size).first { this[it] == 0.toByte() }

    private companion object {
        const val LINUX_ARM64_NATIVE = "linux/aarch64/com/github/stephengold/libjoltjni.so"
        const val MIN_CODE_WORDS = 100_000
    }
}
