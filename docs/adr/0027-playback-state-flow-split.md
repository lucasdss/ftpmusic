# ADR-0027: Playback State Flow Split (metadata vs position)

## Status

Accepted (2026-08-27).

## Context

`MediaSessionPlaybackProvider` publishes a single `StateFlow<PlaybackState>`.
A 200 ms position poller patches `position`/`duration` into that flow
(`MediaSessionPlaybackProvider.kt:143-146`), so while playing the flow emits a
NEW `PlaybackState` instance 5×/sec. `PlaybackViewModel` forwards every
emission (`combine` → `_state`), and `NavHost` collected the whole flow at the
top of its composition scope (`collectAsStateWithLifecycle` at `NavHost.kt:99`).

Consequence: every 200 ms tick invalidated the entire `FtpmusicNavHost`
recomposition scope — bottom bar, navigation bar, every screen, and the player
re-executed 5×/sec. Freshly allocated callback lambdas at the `PlayerBar` call
sites made every subtree non-skippable, so the ~1600-line full player body
re-executed per tick even though only the seek region reads the position.

## Decision

Split the single flow into two:

- **`stateWithoutPosition: StateFlow<PlaybackState>`** — metadata + control
  state with `position` always 0, derived in the ViewModel:
  `state.map { it.copy(position = 0L) }.distinctUntilChanged()
  .stateIn(viewModelScope, WhileSubscribed(5_000), PlaybackState())`
  (`PlaybackViewModel.kt:53-56`). A position tick never re-emits. Hoisting the
  operators out of composition (rather than inlining them at the collection
  site) is required by lint `FlowOperatorInvokedInComposition` and keeps the
  flow identity stable across recompositions.
- **`positionMs: StateFlow<Long>`** — the high-frequency position mirror,
  updated in lockstep with every `PlaybackState` publish
  (`publishFromPlayer`).

`NavHost` collects `stateWithoutPosition` at its top scope and `positionMs`
**inside the mini-player (bottomBar) scope and the nowplaying composable scope
only** — never at the top scope — so the 5 Hz tick recomposes just those
subtrees.

Supporting rules:

1. **Stable callbacks.** `PlayerBar` callbacks are wrapped in `remember` at
   both call sites (keys = `PlaybackViewModel`/`NavController`/context).
   State-read callbacks (artist/album click) read `vm.state.value` live instead
   of capturing a composition-scope snapshot. This makes the player subtrees
   skippable: per tick, only the seek region recomposes.
2. **No IO hops in ViewModel init.** The sleep-timer restore reads the Room
   DAO directly (Room suspend calls already run on Room's executor); an
   explicit `withContext(Dispatchers.IO)` hop made the coroutine resume on a
   dead test scheduler after `runTest` completed (cross-test leak).
3. **Notification rebuilds are memoized** on a render key (title, artist,
   isPlaying, coverArtId, castDevice, volume, toggle) so the duplicated
   notify paths (app listener + media3 session events) post once
   (`PlaybackNotificationProvider`).

## Consequences

- NavHost and all screens recompose only on real metadata changes (track
  change, play/pause, volume, reactions) — position ticks stay inside the
  player subtrees.
- The full player body is skippable per tick except the seek region.
- `PlaybackStateProvider` gained a required `positionMs` member — every
  implementer (`MediaSessionPlaybackProvider`, `FakePlaybackStateProvider`)
  updated.
- Cover-art disk probing in `rememberPreferredCoverArt` is now remembered on
  (artist, album, coverArtId, cacheVersion) — no stat syscalls per tick.
- Lyric active-index lookup is binary search on time-sorted lines; waveform
  bar specs are memoized on the integer playhead.

## References

- `docs/NOWPLAYING_QS_LOCKSCREEN_DEEP_REVIEW_BEHAVIOR_REPORT.md`
