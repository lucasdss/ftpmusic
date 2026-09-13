package com.lucasdss.ftpmusic.app.playback

/**
 * User-selected behavior when starting a new context (Play All / Shuffle)
 * while the Priority Queue (user-added "Play Next" / "Add to Queue" tracks)
 * is non-empty.
 *
 * - ASK   (default): show a modal — user picks "Keep Queue" or "Clear & Play".
 * - CLEAN: always clear the priority queue and play the new context.
 * - PUSH : the new context is played FIRST; the previously queued tracks
 *          (old context + priority) are preserved and play AFTER it.
 */
enum class OverwriteBehavior(val key: String, val label: String) {
    ASK("ask", "Ask"),
    CLEAN("clean", "Clean"),
    PUSH("push", "Push"),
    ;

    companion object {
        fun fromKey(key: String?): OverwriteBehavior = entries.firstOrNull { it.key == key } ?: ASK
    }
}
