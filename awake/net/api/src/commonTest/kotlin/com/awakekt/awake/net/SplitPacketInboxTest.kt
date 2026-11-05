/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitPacketInboxTest {
    @Test
    fun rejectsNonPositiveCapacities() {
        assertFailsWith<IllegalArgumentException> {
            SplitPacketInbox<String, Int>(messageCapacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SplitPacketInbox<String, Int>(snapshotCapacity = 0)
        }
    }

    @Test
    fun snapshotFloodEvictsOldestWithoutDroppingDiscreteMessages() {
        val inbox = SplitPacketInbox<String, Int>(messageCapacity = 8, snapshotCapacity = 4)
        assertTrue(inbox.offerMessage("spawn-1"))
        for (tick in 1..SNAPSHOT_FLOOD_COUNT) {
            inbox.offerSnapshot(tick)
        }
        assertTrue(inbox.offerMessage("chat-2"))

        assertEquals(0L, inbox.droppedMessages)
        assertEquals((SNAPSHOT_FLOOD_COUNT - 4).toLong(), inbox.droppedSnapshots)

        val messages = mutableListOf<String>()
        assertEquals(2, inbox.drainMessages { messages += it })
        assertEquals(listOf("spawn-1", "chat-2"), messages)

        val snapshots = mutableListOf<Int>()
        assertEquals(4, inbox.drainSnapshots { snapshots += it })
        assertEquals(
            listOf(
                SNAPSHOT_FLOOD_COUNT - 3,
                SNAPSHOT_FLOOD_COUNT - 2,
                SNAPSHOT_FLOOD_COUNT - 1,
                SNAPSHOT_FLOOD_COUNT,
            ),
            snapshots,
        )
        assertNull(inbox.pollMessage())
        assertNull(inbox.pollSnapshot())
    }

    @Test
    fun latestSnapshotDrainsIntermediateSnapshotsAndReturnsNewest() {
        val inbox = SplitPacketInbox<String, Int>(snapshotCapacity = 4)
        assertNull(inbox.latestSnapshot())

        for (tick in 1..10) {
            inbox.offerSnapshot(tick)
        }
        assertEquals(6L, inbox.droppedSnapshots)
        assertEquals(10, inbox.latestSnapshot())
        assertNull(inbox.pollSnapshot())
        assertEquals(6L, inbox.droppedSnapshots, "explicit consumer drain is not an overflow drop")
    }

    @Test
    fun messageOverflowReturnsFalseWithoutBlockingAndCountsDrops() {
        val inbox = SplitPacketInbox<Int, Int>(messageCapacity = 3, snapshotCapacity = 2)
        assertTrue(inbox.offerMessage(1))
        assertTrue(inbox.offerMessage(2))
        assertTrue(inbox.offerMessage(3))
        assertFalse(inbox.offerMessage(4))
        assertFalse(inbox.offerMessage(5))

        assertEquals(2L, inbox.droppedMessages)
        assertEquals(1, inbox.pollMessage())
        assertEquals(2, inbox.pollMessage())
        assertEquals(3, inbox.pollMessage())
        assertNull(inbox.pollMessage())
    }

    @Test
    fun closePreservesBufferedItemsUntilDrainedAndRejectsFurtherOffers() {
        val inbox = SplitPacketInbox<String, Int>(messageCapacity = 4, snapshotCapacity = 4)
        assertTrue(inbox.offerMessage("reliable"))
        inbox.offerSnapshot(42)
        assertFalse(inbox.isClosedForReceive)

        inbox.close()
        assertFalse(inbox.isClosedForReceive, "buffered items are still readable after close")
        assertFalse(inbox.offerMessage("after-close"))
        inbox.offerSnapshot(99)
        assertEquals(1L, inbox.droppedMessages)
        assertEquals(1L, inbox.droppedSnapshots)

        assertEquals("reliable", inbox.pollMessage())
        assertFalse(inbox.isClosedForReceive, "snapshot lane still holds one item")
        assertEquals(42, inbox.pollSnapshot())
        assertTrue(inbox.isClosedForReceive)
    }

    private companion object {
        const val SNAPSHOT_FLOOD_COUNT = 500
    }
}
