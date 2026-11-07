# Continuous Play — Behavior Report

Date: 2026-11-03
Related: ADR-0039, ADR-0052, ADR-0053, ADR-0093, ADR-0094, ADR-0095,
ADR-0111, `docs/SURPRISE_ME_BEHAVIOR_REPORT.md`

## Scope

Continuous Play (CP) appends a labeled **Autoplay** tail to **CONTEXT** when
the active queue nears its end. Every appended item is stamped
`is_autoplay=true` and stays CONTEXT — never PRIORITY. CP is separate from
Surprise Me (ADR-0052) and is a user-visible, toggleable queue section, not a
hidden append.

CP runs on the local player and while casting. While casting, Dual is the
timeline source of truth and `appendToContext` mutates Dual then emits one Cast
`AddAll` (ADR-0093, ADR-0111). When the same append also prunes the autoplay
tail (CP-7), it emits a batched `RemoveAll` **before** the `AddAll` so the
receiver cannot replay evicted rows on a jump-back.

## Settings + Queue UI

| Control | Storage | Runtime |
|---|---|---|
| Continuous Play (master) | `KEY_CONTINUOUS_PLAY_ENABLED` (default **ON**) | `PlaybackManager.continuousPlayEnabled` |
| Session Autoplay (this queue) | `KEY_SESSION_AUTOPLAY_ENABLED` | `PlaybackManager.sessionAutoplayEnabled` |
| Journal history size | `KEY_QUEUE_JOURNAL_CAP` | `PlaybackManager.setJournalCap` |
| Queue · Autoplay switch | session key only | `PlaybackViewModel.setSessionAutoplayEnabled` |
| Clear Autoplay | — | `clearAutoplayQueue()` removes `is_autoplay` rows only |

Hydrated at boot via `PreferenceBootstrap.hydrateJournalAndContinuousPlay()`.
New album/playlist/shuffle/mix calls `resetSessionAutoplayFromMaster()`.
Turning session OFF clears Autoplay and bumps `continuousPlayEpoch` so in-flight
CP appends are dropped (ADR-0156). The switch stays interactive while casting
(ADR-0093). `setJournalCap` clamps to 10..500 (default 100); the Settings slider
exposes 100..500 in steps of 50.

## Trigger

`ContinuousPlayGate.shouldLoadContinuation` fires when all of the following
hold:

- Master Continuous Play **and** session Autoplay are enabled.
- The once-per-stint latch (`hasLoadedContinuation`) is unspent.
- The timeline index is valid (`0 <= index < count`; a Cast/Dual `-1` or a
  windowed index fails closed).
- **First burst (user row):** playhead is on the last non-autoplay row and no
  Autoplay tail is already queued (`userRemaining == 0 && !autoplayAfter`).
- **Re-arm (Autoplay row):** at most **3** tracks remain after the current one
  (`LOW_WATER_MARK`) so radio can continue after the first similar burst.

Mid-album/playlist prefetch is intentionally **not** used for the first burst
(market Spotify/YTM timing; ADR-0156). `STATE_ENDED` remains the last-chance
trigger. If a `STATE_ENDED` retry arrives while a load is still in flight,
`ContinuousPlayRetry` remembers it and replays it once after the load finishes,
provided nothing was appended and the coroutine was not cancelled (CP-14). A
deferred retry is suppressed when the user has skipped to another track
(`lastTrackId != mediaId`).

## Candidate order (one burst per load)

`MediaService.maybeLoadContinuousPlay` tries these in order. The first
non-empty source is appended and the load returns; the sources are **not**
additive.

1. **Similar songs (online only).** When not in local-only mode and the media
   id is non-blank: `fetchSimilarSongs(mediaId, count = 20)`; exclude ids
   already in the active queue and apply the CP-2 dislike ledgers. If
   non-empty, append up to 20 and return.
2. **Queue journal.** Read recent entries, parse with
   `JournalTrackSelector.parse(entryCap = journalCap, poolCap = 800)`, score
   and select up to 10 with `JournalTrackSelector.select`, then resolve through
   `ContinuousPlayLoader.resolve`. The selector excludes the whole active
   queue and the track/album/artist dislike ledgers. If non-empty, append and
   return. Journal candidates are hydrated in 500-id chunks.
3. **Local fallback ladder (CP-6).** `SAME_GENRE` → `SAME_ARTIST` →
   `MOST_PLAYED` → `RANDOM`, skipping tiers whose context is unavailable.
   Each tier reads at most 50 rows; picks accumulate across tiers up to a
   target of 10. The whole active queue, the playing id, and the dislike
   ledgers are filtered before slicing. Online uses the uncached queries;
   offline uses the cached-only variants and the loader re-checks playability.

If all three miss, nothing is appended, the latch stays unspent, and the
diagnostic log records `continuous-play exhausted`. The next transition or
`STATE_ENDED` retries.

**Offline / local-only.** `LocalOnlyPolicy.isLocalOnly(offlineMode)` skips the
similar-songs API. A journal or fallback candidate is playable only when its
`cachedFilePath` is non-blank (`ContinuousPlayLoader.isPlayableOffline`).
`isDownloaded = 1` alone is not sufficient (ADR-0095).

**Whole-queue exclusion (CP-3).** The journal excludes every id already in the
active queue, not just the played prefix. CP appends to that same context, so
prefix-only exclusion would re-append upcoming or already-played rows depending
on the cursor rule. A cursor-based prefix policy is deferred; when the whole
pool is excluded the CP-6 ladder still uses the current track's genre/artist.

## Latch and reset

`hasLoadedContinuation` is set only after at least one track is appended. It is
reset when the media id changes and on a forced `STATE_ENDED` retry. An empty
result must not spend the latch, otherwise a temporarily empty journal/offline
pool would permanently disable CP for that queue.

## Counts and caps

| Item | Value |
|---|---|
| Similar-songs API | up to 20 per burst |
| Journal entry cap (`journalCap`) | 10..500, default 100 |
| Journal pool cap | 800 de-duplicated ids |
| Journal hydration chunk | 500 ids |
| Journal select | up to 10 |
| Fallback rows per tier | up to 50 |
| Fallback burst target | up to 10 |
| Autoplay tail cap | 50 autoplay rows (CP-7) |

**Autoplay tail cap (CP-7).** `appendToContext(asAutoplay=true)` evicts the
oldest autoplay rows before appending so the merged queue never holds more than
50 autoplay rows; a burst of 10–20 therefore keeps 2–3 bursts of headroom. The
currently playing row is never evicted (matched by `queueEntryId`, falling back
to `mediaId` for legacy rows). Non-autoplay CONTEXT, PRIORITY, and LATE rows are
untouched, and the capped state is what `persistenceSave` writes. `restoreQueue`
applies the same cap to legacy oversized snapshots, protects/remaps the playing
row, and writes the trimmed queue back with `src=restoreTrim`. Clear Autoplay
remains a manual, uncapped removal of every autoplay row.

`JournalTrackSelector.parse` sets `truncated` when the 800-id pool cap cuts
ids; callers surface it in diagnostics. `getAllRecent()` itself is not row
bounded by those caps: the journal table is evicted to `journalCap`, so the read
returns at most that many entries, and the 800-id pool cap bounds parsed
candidates rather than the row read. The older review wording
"50-entry/5-track selector caps" is stale.

## Journal write

`PlaybackManager.journalQueue` upserts a `QueueJournalEntity` when both a
source type and id are non-blank, then evicts to `journalCap`. An explicit
`journalSource` wins over the legacy `sourceType`/`sourceId` pair. Current
call sites:

- `playAlbum` / `shuffleAlbum`: album, artist, playlist, Downloads.
- `playSingleTrack`: search and profile track taps.
- `pushContext`: only when both legacy source args are supplied.
- Genre mixes: `genremix` with the mix id.
- Surprise Me: `tryStartContext` with `random` / `surprise-me`.
- Favorites: `favorites` / `liked` in LIKED mode only.

**Not journaled:** add-to-queue, play-next, radio, Continuous Play appends, and
Favorites DISLIKED mode.

**CP-1/CP-12 caveat.** An explicit `journalSource` journals without clearing
Priority, which is why tap paths use it. The legacy `sourceType` +
`sourceId` path still clears Priority in `playAlbum`/`shuffleAlbum`; do not use
it for a track tap that must preserve Priority.

## Queue sections

1. **Queue** — PRIORITY (Play Next / Add to Queue at the manual-end position).
2. **Continue Playing** — CONTEXT rows that are not autoplay.
3. **Autoplay** — CONTEXT rows with `is_autoplay=true`, plus the CP toggle and
   Clear Autoplay.
4. **Queued later** — LATE rows (Add to Queue with session-end placement),
   after CONTEXT (ADR-0135).

Clear Autoplay removes only autoplay rows. While casting it re-syncs the
remaining Dual queue to the receiver; non-autoplay rows are preserved. Unlike
the CP-7 cap, Clear Autoplay may remove the currently playing autoplay row
because it is an explicit user action.

## Tests

`ContinuousPlayGateTest`, `ContinuousPlayRetryTest`,
`ContinuousPlayFallbackTest`, `JournalTrackSelectorTest`,
`ContinuousPlayLoaderTest`, `ContinuousPlayTest`, `QueueJournalTest`,
`AutoplayTailCapTest`, `RestoreQueueTest`, `DaosRoomTest` fallback queries,
QueueProjection autoplay flags, and the Cast queue tests. There is no service-level harness that exercises
`maybeLoadContinuousPlay` end to end; the pure gate, selector, fallback, and
retry holders carry that coverage.
