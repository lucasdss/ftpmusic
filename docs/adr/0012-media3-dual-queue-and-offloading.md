# ADR 0012: Advanced Media3 Playback & Dual-Queue Architecture

## Status
Accepted

## Context
The current queue implementation uses a single `QueueManager` that maps 1:1 to the ExoPlayer/CastPlayer's internal list. This creates several problems: context switches (album→playlist) overwrite user-added tracks, "Play Next" inserts at `currentIndex+1` instead of a dedicated priority slot, and there's no separation between what the user chose to play (context) and what they added ad-hoc (priority).

Additionally, preloading is handled by a custom `PreBufferManager` that manually creates `ProgressiveMediaSource` instances without sharing ExoPlayer's `Looper` or `LoadControl`, risking memory leaks. The UI reads `PlayerHolder.player` directly instead of using `MediaController`, and there's no audio offloading for battery optimization.

## Decision
Implement five architectural changes:

### 1. Dual-Queue Architecture
Replace the single `QueueManager` with a `DualQueueManager` that maintains two lists:
- **Context Queue** (`List<MediaItem>`): Set by `playAlbum`, `playSingleTrack`, `shuffleAlbum`. Replaced entirely on context switch.
- **Priority Queue** (`List<MediaItem>`): User-added tracks via "Play Next" (index 0) and "Add to Queue" (append). NEVER overwritten by context switch.

The merged visual queue presented to ExoPlayer is `contextQueue + priorityQueue`. The `DualQueueManager` manages the merge.

### 2. Media3 MediaController Decoupling
Replace direct `PlayerHolder.player` reads in the UI layer with a `MediaController` that dispatches commands and maps state to `StateFlow`. The `PlaybackViewModel` will use `MediaController` instead of the raw Player reference.

### 3. DefaultPreloadManager Integration
Replace `PreBufferManager` with Media3's `DefaultPreloadManager`, sharing ExoPlayer's `Looper` and `LoadControl` to prevent leaks. Use a sliding window of 3 upcoming tracks.

### 4. Optimistic UI Updates
Queue mutations (`playNext`, `addToQueue`, `remove`) will apply instantly to a snapshot state in `PlaybackViewModel`. If the background persistence or Cast sync fails, the state is rolled back. This prevents UI jank during queue operations.

### 5. Audio Offloading
Configure `ExoPlayer` with `AudioOffloadModePreferences` to shift MP3/AAC decoding to hardware DSP. Use `AudioTrack.setOffloadDelayPadding()` to allow the main CPU to sleep during playback.

## Consequences
- Dual-queue adds architectural complexity but preserves user-added tracks across context switches.
- MediaController decoupling improves testability but requires refactoring the UI layer.
- PreloadManager integration removes the custom PreBufferManager and its memory leak risk.
- Optimistic UI provides instant feedback but requires rollback logic for failure cases.
- Audio offloading improves battery life but may not be supported on all devices.
