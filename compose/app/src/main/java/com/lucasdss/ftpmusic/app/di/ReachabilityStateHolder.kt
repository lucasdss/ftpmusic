package com.lucasdss.ftpmusic.app.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the Navidrome server is reachable.
 * Updated by BaseUrlInterceptor on every API call via onSuccess/onFailure callbacks.
 */
object ReachabilityStateHolder {
    private val _isReachable = MutableStateFlow(true)
    val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()

    internal fun onApiSuccess() {
        if (!_isReachable.value) {
            _isReachable.value = true
        }
    }

    internal fun onApiFailure() {
        if (_isReachable.value) {
            _isReachable.value = false
        }
    }
}
