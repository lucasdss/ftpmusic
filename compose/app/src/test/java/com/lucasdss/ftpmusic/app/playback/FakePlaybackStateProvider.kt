package com.lucasdss.ftpmusic.app.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of PlaybackStateProvider for testing.
 * Records method calls so tests can assert on delegation.
 */
class FakePlaybackStateProvider : PlaybackStateProvider {
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState = _playbackState
    private val _positionMs = MutableStateFlow(0L)
    override val positionMs = _positionMs

    var playPauseCalled = false
    var skipNextCalled = false
    var skipPrevCalled = false
    var lastSeekFraction: Float = -1f
    var toggleRepeatCalled = false
    var toggleShuffleCalled = false
    var toggleSpeedCalled = false
    var lastVolume: Float = -1f
    var lastArmedSleepEndMs: Long = -1L
    var sleepTimerCancelled = false

    fun emit(state: PlaybackState) {
        _playbackState.value = state
        _positionMs.value = state.position
    }

    fun emitPosition(positionMs: Long) {
        _positionMs.value = positionMs
        _playbackState.value = _playbackState.value.copy(position = positionMs)
    }

    fun reset() {
        _playbackState.value = PlaybackState()
    }

    override fun playPause() {
        playPauseCalled = true
    }
    override fun skipNext() {
        skipNextCalled = true
    }
    override fun skipPrev() {
        skipPrevCalled = true
    }
    override fun seekTo(fraction: Float) {
        lastSeekFraction = fraction
    }
    override fun toggleRepeat() {
        toggleRepeatCalled = true
    }
    override fun toggleShuffle() {
        toggleShuffleCalled = true
    }
    override fun toggleSpeed() {
        toggleSpeedCalled = true
    }
    override fun setVolume(volume: Float) {
        lastVolume = volume
    }
    override fun armSleepTimer(endMs: Long) {
        lastArmedSleepEndMs = endMs
    }
    override fun cancelSleepTimer() {
        sleepTimerCancelled = true
    }

    var updateExtraStateCalled = false
    override fun updateExtraState(
        isStarred: Boolean,
        sleepTimerEndMs: Long,
        downloadedTrackIds: Set<String>,
        isDisliked: Boolean,
        trackRating: Int,
    ) {
        updateExtraStateCalled = true
        _playbackState.value = _playbackState.value.copy(
            isStarred = isStarred,
            sleepTimerEndMs = sleepTimerEndMs,
            downloadedTrackIds = downloadedTrackIds,
            isDisliked = isDisliked,
            trackRating = trackRating,
        )
    }
}
