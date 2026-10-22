/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PacketFormatTest {
    enum class ChatMode {
        Say,
        Shout,
        Party,
    }

    @Serializable
    sealed interface SamplePacket

    @Serializable
    data class NaviMark(
        val from: NetId,
        @PacketMaxBytes(16, nonEmpty = true) val name: String,
        @PacketMillis val x: Float,
        @PacketMillis val z: Float,
    ) : SamplePacket

    @Serializable
    data class ActionUse(
        @PacketCollectionSize(min = 1, max = 5)
        val skills: List<String>,
        @PacketZeroNullNetId val target: NetId? = null,
        @PacketEmptyNull @PacketMaxBytes(32) val quest: String? = null,
        @PacketIntRange(min = 1, max = 99) val level: Int = 1,
        val mode: ChatMode = ChatMode.Say,
        val active: Boolean = true,
    ) : SamplePacket

    @Test
    fun roundTripsAnnotatedPacketWithIdenticalWireLayout() {
        val writer = PacketWriter()
        val mark = NaviMark(NetId(42L), "Hero", -12.345f, 67.89f)
        PacketFormat.Default.encodeToWriter(writer, NaviMark.serializer(), mark)
        val packet = writer.toPacket()

        val expected = PacketWriter().apply {
            writeVarLong(42L)
            val bytes = "Hero".encodeToByteArray()
            writeVarInt(bytes.size)
            bytes.forEach(::writeByte)
            writeZigZagInt(-12_345)
            writeZigZagInt(67_890)
        }.toPacket()
        assertEquals(expected.toList(), packet.toList())

        val decoded = PacketFormat.Default.decodeOrNull(NaviMark.serializer(), packet)
        assertEquals(mark, decoded)
    }

    @Test
    fun rejectsOversizedStringAndCollectionWithoutThrowing() {
        val writer = PacketWriter()
        writer.writeVarLong(1L)
        writer.writeVarInt(999_999)
        assertNull(PacketFormat.Default.decodeOrNull(NaviMark.serializer(), writer.toPacket()))

        val emptySkills = PacketWriter().apply {
            writeVarInt(0)
            writeVarLong(0L)
            writeVarInt(0)
            writeVarInt(1)
            writeByte(0)
            writeByte(1)
        }.toPacket()
        assertNull(PacketFormat.Default.decodeOrNull(ActionUse.serializer(), emptySkills))
    }

    @Test
    fun opcodeRegistryDispatchesAndValidatesMessages() {
        val registry = PacketOpcodeRegistry<SamplePacket>()
        registry.register<NaviMark>(1) { it.from.value > 0L }
        registry.register<ActionUse>(2)

        for (use in listOf(
            ActionUse(skills = listOf("Slash", "Dash"), target = null, quest = null, level = 15, mode = ChatMode.Party),
            ActionUse(skills = listOf("Slash"), target = NetId(99L), quest = "QUEST_1", level = 20, mode = ChatMode.Shout),
        )) {
            val writer = PacketWriter()
            registry.encode(writer, use)

            val reader = PacketReader()
            reader.reset(writer.toPacket())
            val opcode = reader.readVarInt()
            assertEquals(2, opcode)
            assertEquals(use, registry.decode(opcode, reader))
        }
    }
}
