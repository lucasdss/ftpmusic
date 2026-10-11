package com.lucasdss.ftpmusic.app.ui.issues

import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIssueAggregatorTest {

    private fun build(snap: AppIssueSnapshot) = buildAppIssues(
        snapshot = snap,
        noNetworkTitle = "No internet",
        noNetworkBody = "no net body",
        unreachableTitle = "Server unreachable",
        unreachableBody = "unreachable body",
        offlineTitle = "Offline mode",
        offlineBody = "offline body",
        configTitle = "Server URL looks local",
        configBody = "config body",
        cellularTitle = "Cellular · local only",
        cellularBody = "cellular body",
        playbackTitle = "Playback error",
        conflictTitle = "Playlist sync conflict",
        conflictBodyFor = { "conflict ${it.name}" },
    )

    @Test
    fun `no issues when healthy`() {
        assertTrue(build(AppIssueSnapshot()).isEmpty())
    }

    @Test
    fun `no os network alone`() {
        val issues = build(AppIssueSnapshot(hasOsNetwork = false))
        assertEquals(1, issues.size)
        assertEquals(AppIssueKind.NO_OS_NETWORK, issues.single().kind)
        assertEquals(AppIssueIconKind.WIFI_OFF, resolveHeaderIcon(issues))
    }

    @Test
    fun `unreachable suppressed while offline`() {
        val issues = build(
            AppIssueSnapshot(hasOsNetwork = true, isReachable = false, isOffline = true),
        )
        assertTrue(issues.any { it.kind == AppIssueKind.SIMULATE_OFFLINE })
        assertFalse(issues.any { it.kind == AppIssueKind.SERVER_UNREACHABLE })
        assertEquals(AppIssueIconKind.CLOUD_OFF_MUTED, resolveHeaderIcon(issues))
    }

    @Test
    fun `unreachable when os net ok and not offline`() {
        val issues = build(AppIssueSnapshot(isReachable = false))
        assertEquals(AppIssueKind.SERVER_UNREACHABLE, issues.single().kind)
        assertEquals(AppIssueIconKind.CLOUD_OFF, resolveHeaderIcon(issues))
    }

    @Test
    fun `multi issue uses warning icon and sorted priority`() {
        val issues = build(
            AppIssueSnapshot(
                hasOsNetwork = false,
                isReachable = false,
                isLoopback = true,
                playbackError = "boom",
            ),
        )
        assertTrue(issues.size >= 2)
        assertEquals(AppIssueIconKind.WARNING, resolveHeaderIcon(issues))
        assertEquals(AppIssueKind.NO_OS_NETWORK, issues.first().kind)
    }

    @Test
    fun `playlist conflict open action`() {
        val issues = build(
            AppIssueSnapshot(
                conflictedPlaylists = listOf(ConflictedPlaylist("pl1", "Mix")),
            ),
        )
        assertEquals(1, issues.size)
        assertEquals(AppIssueAction.OpenPlaylist("pl1"), issues.single().action)
        assertEquals(AppIssueIconKind.SYNC_PROBLEM, resolveHeaderIcon(issues))
    }

    @Test
    fun `playback error dismiss action`() {
        val issues = build(AppIssueSnapshot(playbackError = "io fail"))
        assertEquals(AppIssueAction.DismissPlaybackError, issues.single().action)
        assertEquals(AppIssueIconKind.ERROR_OUTLINE, resolveHeaderIcon(issues))
    }

    @Test
    fun `cellular local only when os net and not offline`() {
        val issues = build(AppIssueSnapshot(cellularHardLocal = true))
        assertEquals(AppIssueKind.CELLULAR_LOCAL_ONLY, issues.single().kind)
    }

    @Test
    fun `debounceAfterFirst emits first immediately then waits`() = runTest {
        val out = mutableListOf<Int>()
        // timeout 0 = passthrough
        flowOf(1, 2, 3).debounceAfterFirst(0).toList(out)
        assertEquals(listOf(1, 2, 3), out)
    }

    @Test
    fun `mapConflictedFailSoft emits empty when upstream throws`() = runTest {
        val upstream = flow<List<PlaylistEntity>> {
            throw IllegalStateException("db boom")
        }
        val out = mapConflictedFailSoft(upstream).toList()
        assertEquals(listOf(emptyList<ConflictedPlaylist>()), out)
    }

    @Test
    fun `mapConflictedFailSoft maps rows`() = runTest {
        val upstream = flowOf(
            listOf(
                PlaylistEntity(id = "pl1", name = "Mix", isConflicted = true, conflictMessage = "c"),
            ),
        )
        val out = mapConflictedFailSoft(upstream).toList()
        assertEquals(1, out.single().size)
        assertEquals("pl1", out.single().single().id)
        assertEquals("Mix", out.single().single().name)
    }
}
