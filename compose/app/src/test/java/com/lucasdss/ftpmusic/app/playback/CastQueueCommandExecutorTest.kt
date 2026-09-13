package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastQueueCommandExecutorTest {
    private val converted = mockk<MediaQueueItem>()
    private val executor = CastQueueCommandExecutor { converted }

    @Test
    fun `add inserts before known receiver item`() {
        val receiver = FakeReceiver(setOf(10))

        executor.execute(CastQueueAction.Add(item("added"), 10), receiver)

        assertEquals(listOf("insert:10"), receiver.calls)
    }

    @Test
    fun `missing add destination appends`() {
        val receiver = FakeReceiver(emptySet())

        executor.execute(CastQueueAction.Add(item("added"), 99), receiver)

        assertEquals(listOf("insert:${MediaQueueItem.INVALID_ITEM_ID}"), receiver.calls)
    }

    @Test
    fun `remove and jump ignore items absent from receiver`() {
        val receiver = FakeReceiver(setOf(7))

        executor.execute(CastQueueAction.Remove(99), receiver)
        executor.execute(CastQueueAction.JumpTo(99), receiver)
        executor.execute(CastQueueAction.Remove(7), receiver)
        executor.execute(CastQueueAction.JumpTo(7), receiver)

        assertEquals(listOf("remove:7", "jump:7"), receiver.calls)
    }

    @Test
    fun `move uses receiver insert-before ID and end sentinel`() {
        val receiver = FakeReceiver(setOf(3, 5))

        executor.execute(CastQueueAction.Move(99, 5), receiver)
        executor.execute(CastQueueAction.Move(3, 5), receiver)
        executor.execute(CastQueueAction.Move(3, null), receiver)

        assertEquals(
            listOf("move:3:5", "move:3:${MediaQueueItem.INVALID_ITEM_ID}"),
            receiver.calls,
        )
    }

    @Test
    fun `dup track ids are distinct entry ids`() {
        val receiver = FakeReceiver(setOf(1, 2))
        executor.execute(CastQueueAction.Remove(2), receiver)
        assertEquals(listOf("remove:2"), receiver.calls)
    }

    @Test
    fun `replacement prefers CastPlayer and falls back to queue load`() {
        val items = listOf(item("a"), item("b"))
        val playerReceiver = FakeReceiver(emptySet(), replaceThroughPlayer = true)
        val fallbackReceiver = FakeReceiver(emptySet(), replaceThroughPlayer = false)

        executor.execute(CastQueueAction.ClearAndPlay(emptyList()), playerReceiver)
        executor.execute(CastQueueAction.ClearAndPlay(items, 1, 500L), playerReceiver)
        executor.execute(CastQueueAction.ClearAndPlay(items, 1, 500L), fallbackReceiver)

        assertEquals(listOf("player:2:1:500"), playerReceiver.calls)
        assertEquals(listOf("player:2:1:500", "load:2:1:500"), fallbackReceiver.calls)
        assertTrue(playerReceiver.convertedItems.isEmpty())
        assertEquals(2, fallbackReceiver.convertedItems.size)
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    private class FakeReceiver(override val itemIds: Set<Int>, private val replaceThroughPlayer: Boolean = false) :
        CastQueueReceiver {
        val calls = mutableListOf<String>()
        val convertedItems = mutableListOf<MediaQueueItem>()

        override fun insert(item: MediaQueueItem, beforeItemId: Int) {
            calls += "insert:$beforeItemId"
        }

        override fun remove(itemId: Int) {
            calls += "remove:$itemId"
        }

        override fun move(itemId: Int, beforeItemId: Int) {
            calls += "move:$itemId:$beforeItemId"
        }

        override fun jumpTo(itemId: Int) {
            calls += "jump:$itemId"
        }

        override fun replaceThroughPlayer(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): Boolean {
            calls += "player:${items.size}:$startIndex:$startPositionMs"
            return replaceThroughPlayer
        }

        override fun load(items: List<MediaQueueItem>, startIndex: Int, startPositionMs: Long) {
            convertedItems += items
            calls += "load:${items.size}:$startIndex:$startPositionMs"
        }
    }
}
