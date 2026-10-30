/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The bundle layout: a header and table of contents ahead of the members, written the same way every time. */
class ContentBundleFormatTest {
    private val scene = ContentBundleMember("scenes/main.scene.json", "{\"version\": 1}".encodeToByteArray())
    private val model = ContentBundleMember("assets/crate.glb", ByteArray(64) { it.toByte() })
    private val sound = ContentBundleMember("assets/hit.ogg", ByteArray(5) { 7 })

    @Test
    fun theTableOfContentsListsEveryMemberWhereItsBytesAre() {
        val bundle = ContentBundleFormat.encode(listOf(scene, model, sound))

        val toc = ContentBundleFormat.readTableOfContents(bundle)

        assertEquals(listOf("assets/crate.glb", "assets/hit.ogg", "scenes/main.scene.json"), toc.map { it.key }, "sorted by key")
        listOf(scene, model, sound).forEach { member ->
            val entry = toc.single { it.key == member.key }
            assertContentEquals(member.bytes, bundle.copyOfRange(entry.offset.toInt(), (entry.offset + entry.sizeBytes).toInt()))
            assertEquals(Sha256.digestHex(member.bytes), entry.sha256)
        }
    }

    @Test
    fun theSameMembersInAnyOrderWriteTheSameBytes() {
        val first = ContentBundleFormat.encode(listOf(scene, model, sound))
        val second = ContentBundleFormat.encode(listOf(sound, scene, model))

        assertContentEquals(first, second)
    }

    @Test
    fun theHeaderAndTableReadWithoutTheMembers() {
        val bundle = ContentBundleFormat.encode(listOf(scene, model, sound))

        val header = ContentBundleFormat.readHeader(bundle.copyOf(ContentBundleFormat.HEADER_SIZE))
        val toc = ContentBundleFormat.readTableOfContents(bundle.copyOf(header.dataOffset))

        assertEquals(ContentBundleHeader(ContentBundleFormat.VERSION, 3, header.tableOfContentsSize), header)
        assertEquals(header.dataOffset.toLong(), toc.minOf { it.offset }, "the first member starts right after the table")
        assertEquals(bundle.size.toLong(), toc.maxOf { it.offset + it.sizeBytes }, "the last member ends the bundle")
    }

    @Test
    fun theHeaderIsTheDocumentedLayout() {
        val bundle = ContentBundleFormat.encode(listOf(sound))

        assertEquals("AWKB", bundle.copyOf(4).decodeToString())
        assertContentEquals(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 1), bundle.copyOfRange(4, 12), "version 1, reserved, one member")
        val tableSize = "assets/hit.ogg".length + 2 + 8 + 8 + 32
        assertContentEquals(byteArrayOf(0, 0, 0, tableSize.toByte()), bundle.copyOfRange(12, 16))
        assertEquals(ContentBundleFormat.HEADER_SIZE + tableSize + sound.bytes.size, bundle.size)
    }

    @Test
    fun membersWithTheSameBytesShareOneCopy() {
        val copy = ContentBundleMember("assets/crate-copy.glb", model.bytes.copyOf())

        val bundle = ContentBundleFormat.encode(listOf(model, copy))
        val toc = ContentBundleFormat.readTableOfContents(bundle)

        assertEquals(1, toc.map { it.offset }.distinct().size)
        assertEquals(ContentBundleFormat.readHeader(bundle).dataOffset + model.bytes.size, bundle.size)
    }

    @Test
    fun anEmptyMemberAndAnEmptyBundleAreAllowed() {
        val empty = ContentBundleMember("assets/empty.txt", ByteArray(0))

        assertEquals(0L, ContentBundleFormat.readTableOfContents(ContentBundleFormat.encode(listOf(empty))).single().sizeBytes)
        assertTrue(ContentBundleFormat.readTableOfContents(ContentBundleFormat.encode(emptyList())).isEmpty())
    }

    @Test
    fun aRepeatedKeyIsRefused() {
        val failure = assertFailsWith<IllegalArgumentException> { ContentBundleFormat.encode(listOf(model, model)) }

        assertTrue(failure.message.orEmpty().contains("assets/crate.glb"))
    }

    @Test
    fun somethingElseOrANewerVersionIsRefused() {
        val bundle = ContentBundleFormat.encode(listOf(sound))

        assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader("PK\u0003\u0004 not a bundle".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader(bundle.copyOf().also { it[5] = 2 }) }
        assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader(bundle.copyOf(ContentBundleFormat.HEADER_SIZE - 1)) }
    }

    @Test
    fun aTruncatedTableIsRefused() {
        val bundle = ContentBundleFormat.encode(listOf(scene, model))
        val header = ContentBundleFormat.readHeader(bundle)

        assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readTableOfContents(bundle.copyOf(header.dataOffset - 1)) }
    }
}
