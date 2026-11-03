# Daily Mix, Listening History, Metrics & Continuous Play — Deep Review

Date: 2026-11-02
Status: Review only — no production code changed. Amended by **§10** source
re-validation (docs-only). Original §9 dual-OK is an audit of the first draft;
§10 supersedes severities, corrections, and P0 order for implementation-spec use.
Scope: Daily Mix generation/regeneration, `listen_events` and play-count persistence,
Profile metrics, queue journal, and Continuous Play (CP).

Method: static trace of the current `main` tree plus targeted unit tests. Every
finding below cites the current source. The baseline test command was:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lucasdss.ftpmusic.app.playback.DailyMixGeneratorTest" \
  --tests "com.lucasdss.ftpmusic.app.data.repository.DailyMixRepositoryTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.ContinuousPlayTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.JournalTrackSelectorTest" \
  --tests "com.lucasdss.ftpmusic.app.data.repository.ScrobbleServiceTest"
```

Result: **BUILD SUCCESSFUL**. The existing tests pass while several of the
edge cases below are uncovered; passing tests are therefore not evidence that
the behaviors are correct.

---

## 1. Executive summary

> **Amended severities and corrections:** treat the table below as the original
> draft snapshot. Final validated severities, rejected claims, and new findings
> are in **§10**. Revised P0: MET-1 → DM-1 (artist-cap/adjacency) → CP-2.

| ID | Area | Severity | Finding |
|---|---|---|---|
| DM-1 | Daily Mix | **Critical** | A single-artist pool produces exactly **one** track from the primary pool (supplemental can add more only when cross-mix fill is enabled), not 30–120. The same failure mode under-fills any low-diversity pool. |
| DM-2 | Daily Mix | **High** | The 30-track unlistened threshold replaces the entire pool, so a 500-track pool with 31 unlistened tracks yields at most 31 tracks. |
| DM-3 | Daily Mix | **High** | Phase-3 supplemental fill has four independent defects: static `lastArtist`, missing supplemental weights, no listened-track exclusion, no album diversity. |
| DM-4 | Daily Mix | **High** | The generator reads lifetime `tracks.play_count` + rating/star only. `listen_events`, recency, skips, and actual yesterday listens are not integrated. |
| DM-5 | Daily Mix | **High** | Regeneration counts lifetime plays, and Home only evaluates regeneration when a card is missing. For Home-driven users with a yesterday fallback card, Home regenerates every **other** day. |
| DM-6 | Daily Mix | **Medium** | “Weight” is a deterministic sort order, not weighted sampling; the test names/comments overstate what it does. |
| DM-7 | Daily Mix | **Medium** | Tests assert only “non-empty” for the single-artist/low-diversity cases, masking DM-1/DM-2. |
| MET-1 | Metrics | **Critical** | `MediaService.onDestroy()` calls `scrobbleService.dispose()`, permanently cancelling the singleton's scope. A same-process service restart stops all local scrobbles/listen events. |
| MET-2 | Metrics | **High** | `ScrobbleService.scrobble()` performs the network call **before** the local write, with no durable retry. A hang/process death loses the event. |
| MET-3 | Metrics | **High** | `ensureTrackRow()` does read → `copy()` → `INSERT OR REPLACE`. A concurrent write can clobber `play_count`/`last_played_at`. |
| MET-4 | Metrics | **Medium** | Play-count increment and listen-event insert are not one transaction. |
| MET-5 | Metrics | **Medium** | `listen_events` cannot represent skips, completion, or start time; only completed-enough listens are written. |
| MET-6 | Metrics | **Low** | The 52→53 migration expands every `play_count` with an unbounded recursive CTE. |
| CP-1 | Continuous Play | **High** | Most track-tap call sites do not pass `sourceType`/`sourceId`, so they never journal and cannot seed CP. |
| CP-2 | Continuous Play | **High** | Neither the similar-songs path nor the journal path filters disliked tracks/albums/artists. |
| CP-3 | Continuous Play | **High** | `JournalTrackSelector` is random recency-weighted; it ignores ratings, play counts, position, genre affinity, and recently played tracks. |
| CP-4 | Continuous Play | **Medium** | The similar-songs chain re-seeds from the last autoplay track (drift), and `hasLoadedContinuation` is set without verifying the append added anything. |
| CP-5 | Continuous Play | **Medium** | CP loads only when the last item is reached/ended; no “N tracks remaining” prefetch or buffer maintenance. |
| CP-6 | Continuous Play | **Medium** | Empty journal + offline/no cached candidates yields nothing; there is no local library fallback. |
| CP-7 | Continuous Play | **Medium** | The autoplay tail grows without a cap and is persisted. |
| CP-8 | Continuous Play | **Medium** | `docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md` is stale (journal-only, 10 tracks, “skipped while casting”). |
| CP-9 | Continuous Play | **Low** | `QueueJournalEntity.position` is written but never read. |

Severity meaning:

- **Critical**: a normal user-visible path produces a broken result or silently
  stops recording data.
- **High**: common conditions produce wrong/stale content or lose data.
- **Medium**: degraded quality, maintainability, or documentation risk.
- **Low**: edge-case or hygiene issue.

The short answers to the three original questions:

1. **Is history correctly integrated in the algorithm?** No. It is integrated
   only as a lifetime `play_count` ranking plus a one-step “yesterday” penalty.
   The richer `listen_events` history is written but never read by Daily Mix or
   Continuous Play.
2. **Are metrics correctly persisted?** Mostly at the row level, but the write
   path has a critical lifecycle cancellation bug, network-before-local ordering,
   no durable retry, and a read-modify-write race.
3. **Can Continuous Play be improved?** Yes, substantially: better candidate
   scoring, dislike filtering, journal coverage, prefetch, offline fallback,
   tail pruning, and documentation.

---

## 2. Data-flow map

```
Daily Mix recipe (custom_mixes)
  └─ resolvePool() ── Room queries ── playable, non-disliked tracks
       └─ DailyMixGenerator.generate()
            inputs: songs, context.weightMap, supplemental,
                    yesterdayTrackIds, previouslyListenedTrackIds
            reads (via buildWeightMap): tracks.play_count, user_rating, starred_at
       └─ replaceDailyMix(date, mixId, tracks)
       └─ MixCacheCoordinator.onGenerated() (auto-cache)

Home / sync screen / mix detail
  └─ generateAll()/generateOne()
       └─ shouldRegenerate(createdAt, totalTracks, listenedTracks)
            listenedTracks = count of existing mix tracks with lifetime play_count > 0

Playback
  └─ position poller accumulates content-heard ms
       └─ threshold (Last.fm: >30s and >=50% or >=4min)
            └─ ScrobbleService.scrobble()
                 ├─ api.scrobble(submission=true)          (network first)
                 ├─ tracks.play_count++                     (local)
                 └─ listen_events.insert(...)               (local)

Context start
  └─ PlaybackManager.playAlbum/shuffleAlbum/playSingleTrack/pushContext
       └─ journalQueue(sourceType, sourceId, trackIds, position)
            └─ queue_journal (one row per source, unique index)

Last queue item / STATE_ENDED
  └─ ContinuousPlayGate.shouldLoadContinuation()
       └─ similar-songs API (online, primary)
            else queue_journal → JournalTrackSelector → ContinuousPlayLoader
                 └─ appendToContext(asAutoplay = true)
```

Two structural observations:

- **The feedback loop is broken.** `listen_events` feeds Profile, but neither
  `DailyMixGenerator` nor `JournalTrackSelector`/CP reads it.
- **`play_count` is doing double duty.** It is both a Profile/legacy metric and
  the main Daily Mix affinity signal. Any bug in its persistence silently
  changes what the algorithm recommends.

---

## 3. Daily Mix findings

### DM-1 — Single-artist and low-diversity pools collapse to a tiny mix

**Severity: Critical. Confidence: High.**

Evidence:

- `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/DailyMixGenerator.kt:95-106`
- Same file, `artistTracks` grouping: `DailyMixGenerator.kt:77-84`
- Same file, `dynamicMaxPerArtist`: `DailyMixGenerator.kt:66-67`
- Existing test: `compose/app/src/test/java/com/lucasdss/ftpmusic/app/playback/DailyMixGeneratorTest.kt` (“handles single artist gracefully”)

The loop is:

```kotlin
while (result.size < targetSize && queue.isNotEmpty() &&
       consecutiveSkips < queue.size * 2) {
    val artist = queue.removeFirst()
    ...
    val lastArtist = result.lastOrNull()?.artist
    if (artist == lastArtist) {
        queue.addLast(artist)
        consecutiveSkips++
        continue
    }
    consecutiveSkips = 0
    ...
}
```

For a pool containing only `ArtistA`:

1. First iteration: `result` is empty, `lastArtist == null`, one track is picked,
   `queue.addLast("ArtistA")`, `consecutiveSkips = 0`.
2. Second iteration: `artist == lastArtist`, requeue, `consecutiveSkips = 1`.
3. Third iteration: same, `consecutiveSkips = 2`.
4. Loop condition: `consecutiveSkips < queue.size * 2` → `2 < 2` is false → exit.

Result size from the primary pool is exactly **1**. The existing test only
asserts `isNotEmpty()`, so it passes. All tracks with a null artist are grouped
under the single key `"unknown"` (`DailyMixGenerator.kt:79`), so a library with
many untagged tracks can hit the same path.

Phase 3 can append tracks from `supplementalTracks` (other mixes' pools), so
the final result is 1 **plus** supplemental only when cross-mix fill is
enabled. Cross-mix fill is opt-in and may be empty; it does not fix the
single-artist primary collapse.

Two artists are also under-filled:

- `dynamicMaxPerArtist = maxOf(2, targetSize / 10)`.
- A 30-track target gives cap 3, so at most 6 primary tracks can be emitted.
- A 120-track target gives cap 12, so at most 24 primary tracks can be emitted.
- Once both artists hit the cap, the loop exhausts its skip budget and exits.
- Supplemental can add up to `targetSize - result.size`, but it is a separate
  pool and does not make the primary selection diverse.

Impact:

- A “Daily Mix” can contain one track (or six, or twenty-four) while the card
  and Settings claim a 30–120-track mix.
- Manual refresh cannot fix it; the same inputs produce the same bottleneck.
- CP then extends from a one-track context, making the whole playback session
  depend on CP rather than the mix.
- Auto-cache/cache ownership and cover art are built from a tiny list.

A separate doc/code divergence in the same file: the KDoc at
`DailyMixGenerator.kt:16` says “Max 4 tracks per artist (maxPerArtist)”, but no
`maxPerArtist` constant exists. The actual cap is dynamic
(`maxOf(2, targetSize / 10)`, line 67). Do not use the KDoc as a specification;
fix the comment when fixing the loop.

Why the current design exists:

- The rule “no two consecutive tracks from the same artist” is reasonable for
  diverse libraries.
- The implementation treats “cannot satisfy the rule” as “stop”, instead of
  “relax the rule and fill with a diversity penalty”.

Recommended fix (choose one, not both):

1. **Relax only when necessary.** Track the set of candidates that can be
   picked. If every remaining candidate belongs to `lastArtist`, allow a
   same-artist append but apply a penalty to the diversity score; do not stop
   the loop. Keep the hard `dynamicMaxPerArtist` only while alternatives exist.
2. **Explicit degraded mode.** If the pool cannot satisfy the diversity rule,
   generate `min(target, availableTracks)` in a deterministic/weighted order and
   mark the mix as “single-artist” in diagnostics. Do not silently return one
   track.

A small pseudo-code shape:

```kotlin
while (result.size < targetSize && remaining.isNotEmpty()) {
    val last = result.lastOrNull()?.artist
    val pick = chooseCandidate(
        remaining = remaining,
        lastArtist = last,
        allowSameArtist = remaining.none { it.artist != last },
        weightMap = weightMap,
        albumUsage = albumUsage,
    ) ?: break
    result += pick
    remaining -= pick
    // update artistCounts/albumUsage
}
```

Test requirements:

- `100 tracks, 1 artist` → result size `>= min(30, 100)` (or a documented
  single-artist behavior; never 1).
- `100 tracks, 2 artists` → result size `>= 30` with a seeded RNG.
- `100 tracks, 1 artist + 90 other artists` → result alternates, and size is
  in the target range.
- Assert exact bounds, not merely `isNotEmpty()`.

### DM-2 — The unlistened threshold replaces the whole pool and shrinks the mix

**Severity: High. Confidence: High.**

Evidence: `DailyMixGenerator.kt:60-65`

```kotlin
val unlistenedSongs = songs.filter { it.id !in previouslyListenedTrackIds }
val primaryPool = if (unlistenedSongs.size >= 30) unlistenedSongs else songs
val targetSize = random.nextInt(30, 121)
    .coerceAtMost(primaryPool.size + supplementalTracks.size)
```

Trace for a 500-track pool with 31 unlistened tracks:

- `unlistenedSongs.size == 31 >= 30` → `primaryPool = 31 tracks`.
- `targetSize` is capped at `31 + 0 = 31`.
- Even with perfect artist diversity, the result cannot exceed 31.
- The other 469 familiar tracks are never eligible, although the doc/ADR says
  “exclude previously listened unless necessary” — here it is not necessary,
  because there are 500 candidates available.

Trace for 29 unlistened tracks:

- `primaryPool = all 500`; the anti-repeat filter disappears entirely in one
  step. This is a binary cliff, not a gradual preference.

Impact:

- Daily Mix silently shrinks to the unlistened subset whenever a user has
  listened to most of a genre.
- The shrink bounds the candidate set to at most 31; within that set, DM-1's
  adjacency rule can still collapse it further (e.g., 31 unlistened tracks from
  one artist → 1 track). The two bugs are related but not multiplicative: the
  candidate set is already capped before diversity is applied.
- `shouldRegenerate` then sees a small mix and can regenerate more often,
  creating churn.

Recommended fix:

- Keep `primaryPool = songs` (the full eligible pool).
- Prefer unlistened tracks during selection, but allow familiar tracks as
  fillers. Example:

```kotlin
val targetSize = random.nextInt(30, 121).coerceAtMost(songs.size + supplemental.size)
val unlistened = songs.filter { it.id !in previouslyListenedTrackIds }
val familiar = songs.filter { it.id in previouslyListenedTrackIds }
// Round-robin: take from unlistened while it can satisfy diversity and the
// desired fresh ratio; then fall back to familiar.
```

- If a hard “fresh mix” mode is desired, make it an explicit setting, not a
  hidden 30-track threshold.
- Make the threshold proportional to `targetSize` and the pool size, e.g.
  `unlistened.size >= targetSize * 0.5`, rather than a fixed 30.

Test requirements:

- 500 tracks / 31 unlistened / target 60 → size 60, with a high unlistened
  share but not a hard cap at 31.
- 500 tracks / 29 unlistened → size 60; the anti-repeat preference still
  applies.
- 31 unlistened / 1 artist + familiar tracks from other artists → size 60.

### DM-3 — Phase-3 fill has four independent defects

**Severity: High. Confidence: High.**

Evidence: `DailyMixGenerator.kt:132-154`

```kotlin
if (result.size < targetSize) {
    val lastArtist = result.lastOrNull()?.artist
    supplementalTracks
        .filter { it.artist != lastArtist && it.id !in resultIds }
        .sortedByDescending { weightMap[it.id] ?: 1 }
        .forEach { s -> ... }
}
```

Defects:

1. **Static `lastArtist`.** It is captured once before `forEach`. After the
   first supplemental track is appended, the next supplemental track is still
   compared to the original last artist, so two or more consecutive
   supplemental tracks can share an artist.
2. **Supplemental weights are always 1.** `weightMap` is built at
   `DailyMixGenerator.kt:70-74` from `primaryPool` only. Supplemental IDs are
   not in that map, so the `?: 1` fallback always applies, even though the
   caller's `context.weightMap` covers all songs.
3. **No `previouslyListenedTrackIds` exclusion.** The filter only checks the
   static last artist and `resultIds`. A supplemental track that the user
   already listened to can be inserted.
4. **No album diversity.** `albumUsage` is not consulted in Phase 3; consecutive
   tracks from the same album are possible, and the album-diversity guarantee
   from Phase 1 is lost.

Two more secondary issues:

- The `artistCounts` cap is applied, but if the cap is reached for every
  supplemental artist, the loop simply ends with an under-filled result.
- `sortedByDescending` is deterministic for ties (stable sort), so the same
  supplemental tracks tend to win every regeneration.

Impact:

- Cross-mix fill can reintroduce the exact “same artist / same album” runs the
  main loop was designed to prevent.
- Familiar/starred supplemental tracks are not preferred because their weight
  is always 1.
- A user who enables “Fill from other mixes” gets lower-quality diversity than
  the Phase-1 path suggests.

Recommended fix:

- Use the caller-provided `ratings` map (the full `context.weightMap`) for all
  weight lookups.
- Maintain `var lastArtist` and update it after every append.
- Filter `it.id !in previouslyListenedTrackIds` when unlistened alternatives
  exist; if none exist, allow them only after the fresh pool is exhausted.
- Track `albumUsage` and prefer unused albums, exactly as Phase 1 does.
- Shuffle ties or use a seeded weighted sample to avoid deterministic repeats.
- If the cap prevents filling, relax it only after all artists are exhausted,
  and log the degraded fill.

Test requirements:

- Supplemental list ordered `A, A, B` with primary ending in `C` → no adjacent
  `A, A` in the result.
- Supplemental track `sup-high` with weight 5 vs `sup-low` weight 1 → high is
  chosen first.
- Supplemental contains a previously listened track and an unlistened
  alternative → listened track is not chosen.
- Supplemental tracks from the same album are separated when alternatives
  exist.

### DM-4 — History is persisted but not integrated into the algorithm

**Severity: High. Confidence: High.**

Evidence:

- `DailyMixGenerator.buildWeightMap()`: `DailyMixGenerator.kt:169-195` reads
  `trackDao.getTrackPlayCounts`, `getRatedTracksByIds`, `getStarredIdsByIds`.
- DAO queries: `Daos.kt:40-55` read `tracks.user_rating`, `tracks.starred_at`,
  and `tracks.play_count` only.
- Repository anti-repeat set: `DailyMixRepository.kt:400`
  `previouslyListenedIds = existingIds.filter { playCounts[it] > 0 }`.
- “Yesterday” set: `DailyMixRepository.kt:390-399` reads yesterday's
  **generated** track list, not tracks actually listened to yesterday.

What is missing:

- No `listen_events` query in the Daily Mix path. A repo-wide grep finds
  `listen_events` only in DAO/Profile/migration code.
- No recency decay: a track played 200 times two years ago and a track played
  5 times this week can receive the same weight (both saturate at weight 5).
- No skip/partial-listen signal. `ScrobbleService` only writes an event after
  the Last.fm threshold, so a track skipped before 50% leaves no trace.
- No artist/genre affinity learned from history. Diversity is enforced
  structurally by artist counts, not by learned taste.
- `previouslyListenedTrackIds` is narrow: only tracks in the current mix's
  today/yesterday list with `play_count > 0`. A track played heavily in another
  context can still appear.
- `yesterdayTrackIds` is generated-list based, so “yesterday” can include tracks
  the user never played and exclude tracks they did play from elsewhere.

Impact:

- The algorithm cannot distinguish “familiar” from “stale”.
- A user's recent taste changes are invisible until lifetime play counts
  dominate.
- “Don't repeat yesterday” is not actually what the name implies.
- The Profile metrics are accurate but disconnected from recommendations.

What is intentional:

- ADR-0046 explicitly says “`play_count` on `tracks` still feeds Daily Mix
  weighting — must keep”. So lifetime play count is not an accidental input.
  The finding is not “play_count should be deleted”; it is “the newer, richer
  history is available and unused, and the anti-repeat set is narrower than its
  name suggests”.

Recommended design:

1. Add a batched DAO query for candidate IDs over a recent window, e.g. last
   30/90 days:

```sql
SELECT track_id,
       COUNT(*) AS plays,
       SUM(listened_seconds) AS seconds,
       MAX(listened_at) AS last_listened
FROM listen_events
WHERE track_id IN (:ids)
  AND is_backfill = 0
  AND listened_at >= :since
GROUP BY track_id
```

2. Compute a score, not just an ordinal:

```text
score =
  w1 * log2(1 + lifetimePlays) +
  w2 * ratingBonus +
  w3 * recentPlays30d +
  w4 * exp(-daysSinceLastListen / 30) -
  w5 * skipPenalty
```

3. Use `listen_events` for the anti-repeat set:
   - “Heard in the last N days” from `listened_at`, not from the previous mix.
   - “Yesterday” from actual events on the previous local day, if the product
     wants that semantics.
4. Add a start/abandon event or a `play_sessions` table before claiming to learn
   skips. `listen_events` is completion-biased by construction.
5. Keep lifetime `play_count` as a familiarity prior, but cap its influence so a
   decade-old favorite does not outrank this week's favorite forever.

Test requirements:

- Track A: 100 plays 2 years ago; Track B: 5 plays this week. With recent-window
  scoring, B ranks above A.
- A track played in another context yesterday is excluded from today's mix when
  the anti-repeat rule is enabled.
- A skipped track (if session events exist) is down-ranked, not treated as a
  full play.

### DM-5 — Regeneration policy is lifetime-based and Home skips the check

**Severity: High. Confidence: High.**

Evidence:

- `DailyMixRepository.kt:355-374`: `listenedCount` is computed from
  `trackDao.getTrackPlayCounts(existingIds)` and counts `playCount > 0`.
- `DailyMixGenerator.kt:33-48`: policy is 48h hard max, or 24h + 10% listens.
- `LibraryViewModel.kt:835-858` (gate at 846-854): Home only calls
  `maybeGenerateMixes()` when `cards.size < mixes.size`.
- `LibraryViewModel.kt:861-897`: `readMixCards()` falls back to yesterday's row.

Trace:

1. Day 1: no rows → Home generates a mix. `createdAt = Day 1`.
2. Day 2: `readMixCards()` finds Day 1's row as “yesterday”; `cards.size ==
   mixes.size` → Home does **not** call `generateAll`, so `generateForMix` and
   `shouldRegenerate` are never evaluated. No Day 2 row is persisted.
3. Day 3: no Day 2 row exists; `readMixCards()` returns no card for that mix →
   Home calls `generateAll`. In `generateForMix`, `getDailyMix(today)` and
   `getDailyMix(yesterday)` both return null, so `existingMix == null`,
   `shouldRegen = true`, and Day 3 is regenerated **unconditionally** — the
   24h/48h predicate is bypassed, not applied.
4. Repeat: Home-driven users alternate “fallback card” and “unconditional
   regeneration” days, regardless of the 24h usage rule.

The subtle point is that there are two doors, not one:

- The **Home gate** (`cards.size < mixes.size`) decides whether generation is
  consulted at all.
- The **repository predicate** (`existingMix != null` + `shouldRegenerate`)
  decides whether an existing row is kept.

Fixing only the Home gate is not enough. If Day 2 calls `generateAll` and the
predicate correctly returns false, `generateForMix` still persists nothing for
Day 2; Day 3 then sees no “yesterday” row and takes the `existingMix == null`
unconditional path. A complete fix must either (a) evaluate the age/listen
predicate against the most recent row for the mix, even if it is older than
yesterday, or (b) carry a row forward for “today” without changing the
generation timestamp used for policy. Option (a) is simpler and keeps the data
model honest.

Two independent bugs:

- **Lifetime count.** A track with `play_count = 20` from before the mix was
  created counts as “listened in this mix”. A mix containing long-owned
  favorites can show a 100% listen ratio immediately after creation, so the 24h
  gate always fires when it is evaluated.
- **Skipped evaluation + lookback too narrow.** The Home path does not ask “is
  this existing mix stale?” when a yesterday fallback exists, and
  `generateForMix` only looks at today/yesterday. An older row is invisible to
  the predicate, so the next generation is unconditional. The sync screen
  (`SyncingScreen.kt:308-326`) calls `generateAll` daily, so behavior differs by
  entry point.

Impact:

- The documented usage-based policy is mostly unreachable from Home.
- A mix can be up to ~48h old even though a cheap age check could have refreshed
  it.
- Conversely, a 48h-old mix can be regenerated immediately after creation if
  lifetime counts look high, resetting `createdAt` and masking the true age.
- Library/filter changes are not reflected until the missing-card path or a
  manual refresh.

Recommended fix:

1. On Home, always run a cheap freshness check per existing mix, even when
   `cards.size == mixes.size`:

```kotlin
// existingRows is a joined projection carrying createdAt, trackCount, and
// trackIds; see §7.4 for the concrete DAO queries.
val stale = existingRows.any { row ->
    DailyMixGenerator.shouldRegenerate(
        createdAtMs = row.createdAt,
        totalTracks = row.trackCount,
        listenedTracks = countListensSince(row.createdAt, row.trackIds),
    )
}
if (stale || cards.size < mixes.size) maybeGenerateMixes()
```

2. Make `generateForMix` look up the most recent row for the mix, not only
   today/yesterday, so a 48h-old row still participates in the age predicate.
   Keep `readMixCards`'s yesterday fallback for display only.
3. Replace lifetime counting with listens since the row was created:
   - `tracks.last_played_at >= createdAt`, or
   - `COUNT(listen_events WHERE listened_at >= createdAt AND track_id IN (...))`.
4. Make the empty-attempt suppression per mix, not global, so one empty pool
   does not block other mixes for the day.
5. Add an explicit `lastGenerationCheck`/diagnostic log so the policy is
   observable.

Test requirements:

- Yesterday card + 25h + 10% listens since creation → Home triggers one
  regeneration.
- Yesterday card + 25h + 0 listens since creation → no regeneration.
- Today card + 2h + 100% lifetime listens → no regeneration.
- 49h card → regeneration regardless of listens.

### DM-6 — “Weight” is a deterministic sort order, not weighted sampling

**Severity: Medium. Confidence: High.**

Evidence:

- `DailyMixGenerator.kt:69-84`: each artist's tracks are sorted by descending
  weight.
- `DailyMixGenerator.kt:114-121`: `minByOrNull` picks the unused album with the
  highest weight; album count dominates (`albumCount * 1000 - weight`).
- Test names/comments claim “higher rated tracks appear more frequently” and
  “rating 5 gives roughly 5x weight” (`DailyMixGeneratorTest.kt`).

What actually happens:

- Every artist is visited round-robin. Within an artist, the highest-weight
  tracks are chosen first. Weight determines **order**, not probability.
- Once per-artist caps are hit, a high-weight track does not get extra picks.
- The test named `rating 5 gives roughly 5x weight over unrated` asserts only
  `starCount >= 10`; it does not measure a 5× distribution. It passes because
  the ten rated tracks have unique artists and therefore enter the round-robin,
  not because they are sampled 5× more often.

Impact:

- The algorithm's “familiarity” is deterministic top-N within each artist, which
  reduces day-to-day variety.
- The yesterday penalty (-1) is not enough to demote a weight-5 track below
  weight-1 candidates, so the same favorites can lead every regeneration.
- The comments and test names give future maintainers a false model.

Recommended fix:

- Either document the behavior as “deterministic priority ranking” and rename
  the tests, or implement actual weighted sampling without replacement
  (e.g., Efraimidis–Spirakis: key `random^(1/weight)`, take the top keys) while
  preserving the diversity constraints.
- If weighted sampling is adopted, keep a small deterministic seed per day so
  the mix is stable for that day.

Test requirements:

- Statistical test with a fixed RNG: weight 5 tracks appear more often than
  weight 1 tracks across many generated mixes.
- Determinism test: same seed + same inputs → same output.
- Diversity test: weighted sampling still respects no-consecutive-artist and
  album caps.

### DM-7 — Tests mask the main failures

**Severity: Medium. Confidence: High.**

Evidence:

- `DailyMixGeneratorTest.kt` “handles single artist gracefully” asserts only
  `isNotEmpty()`.
- “two artists with many tracks are interleaved” asserts alternation but not
  size.
- “retries exhaust gracefully without supplemental tracks” asserts only
  `isNotEmpty()` and an upper bound.
- No test drives `previouslyListenedTrackIds` with a large familiar pool.
- No test covers Phase-3 same-artist runs or supplemental weights.
- No test covers the Home every-other-day path; `DailyMixRepositoryTest`
  exercises `generateAll` directly.

Impact:

- The exact bugs above can ship while the suite is green.
- Reviewers see “single artist gracefully” and assume the case is handled.

Recommended fix:

- Convert “non-empty” assertions into invariant assertions:
  - size bounds for a given pool and seeded RNG;
  - artist adjacency;
  - album adjacency where promised;
  - anti-repeat exclusion;
  - fresh/familiar ratio.
- Add a `LibraryViewModel`/Home freshness test at the repository boundary.
- Add a migration/DAO test for listen-event aggregates.

### DM-8 — Smaller Daily Mix observations

- **`"unknown"` artist grouping** (`DailyMixGenerator.kt:79`): all null-artist
  tracks are one artist. This is reasonable for diversity, but it makes DM-1
  more likely in libraries with poor tags. Consider a separate “unknown artist”
  bucket that is not subject to the same adjacency rule, or log a diagnostic
  when it dominates.
- **Target size vs cap:** the UI/docs say 30–120, but `targetSize` is capped by
  pool size and further limited by `dynamicMaxPerArtist`; for two artists the
  effective maximum is 6–24. This should be documented or fixed with DM-1.
- **`prepare()` cost:** every generation run calls
  `populateAllTrackGenres()` and `populateGenresFromCachedGenreSongs()`
  (`DailyMixRepository.kt:302-305`). On a large library this is a full-table
  write pass; with Home regeneration every other day it is noticeable. Consider
  a dirty flag or running it after sync only.
- **Dislike is filter-on-read, not filter-on-generate:** `filterPlayableMixTrackIds`
  protects Mix Detail/play (`DailyMixRepository.kt:458-474`), and pool SQL
  filters parents. But a persisted mix can still contain a track that becomes
  disliked later; CP journal entries can also point at it. This is an accepted
  trade-off in ADR
  `0037-daily-mix-parent-dislike-and-fill-mode.md`, but it should be part of
  the CP filter (CP-2). Note the duplicate ADR number: `0037-library-browse-
  search-state-slices.md` is unrelated.
- **`replaceDailyMix` resets `createdAt`:** `DailyMixEntity` uses the default
  `System.currentTimeMillis()` (`Entities.kt:432-437`). This is correct for
  “age since generation”, but it means a manual refresh restarts the 24h clock.
  That is probably intended; document it.
- **Cross-mix fill:** `supplemental` is the union of other mixes' pools
  (`DailyMixRepository.kt:385-389`), so the same track can appear in several
  mixes. This is the documented legacy behavior and is opt-in; no action beyond
  DM-3.

---

## 4. Metrics persistence findings

### MET-1 — `ScrobbleService.dispose()` kills a singleton for the rest of the process

**Severity: Critical. Confidence: High.**

Evidence:

- `ScrobbleService` is `@Singleton` and owns a plain
  `CoroutineScope(Dispatchers.IO + SupervisorJob())`:
  `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/repository/ScrobbleService.kt:24-33`.
- `MediaService.performDestroyCleanup()` calls `scrobbleService.dispose()`:
  `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/MediaService.kt:2507`.
- `dispose()` is `scope.cancel()`: `ScrobbleService.kt:225-227`.
- `MediaService.onDestroy()` invokes that cleanup:
  `MediaService.kt:2430-2432`.
- A same-process restart path exists: `onCreate` failure calls `stopSelf()`
  (`MediaService.kt:2388-2396`), and the app can start the service again in the
  same process (activity resume/self-heal, media button, BT).

Failure sequence:

1. Service is destroyed for any reason (error path, system idle stop, explicit
   stop).
2. `scrobbleService.dispose()` cancels the singleton's `scope`.
3. Service is recreated in the same process. Hilt returns the same
   `ScrobbleService` instance, with a cancelled scope.
4. `nowPlaying()` and `scrobble()` call `scope.launch { ... }`; the launch is
   immediately cancelled/no-op.
5. `tracks.play_count`, `listen_events`, and server scrobbles stop updating.
   `fetchSimilarSongs()` still works because it is a direct `suspend` call, so
   CP appears healthy while metrics silently freeze.

Impact:

- Silent, permanent metric loss for the rest of the process lifetime.
- Daily Mix loses its main input (`play_count`) and stops adapting.
- Profile shows stale data; the user has no error surface.
- The bug can persist across many service restarts until the process dies.

Recommended fix:

- Do **not** cancel an application-scoped singleton from a service lifecycle
  callback. Options:
  1. Remove `scrobbleService.dispose()` from `performDestroyCleanup()` and let
     the application scope live for the process.
  2. Inject an `@ApplicationScope CoroutineScope` (or a small
     `AppCoroutineScope` wrapper) into `ScrobbleService`, and never cancel it
     from a feature/service.
  3. If `dispose()` is needed for tests, make it re-creatable:
     `scope = CoroutineScope(...)` on a subsequent `ensureScope()` call, or
     expose a `resetForTest()` instead of production cancellation.
- Add a lifecycle test: create service → destroy → recreate with the same
  singleton → `scrobble()` → verify `listenEventDao.insert` and
  `incrementPlayCount`.
- Rewrite `ScrobbleServiceTest.kt:100-104`
  (`dispose cancels scope`). It currently has no assertion and effectively
  encodes the production behavior as intended; replace it with the recreate-
  then-write test above.

### MET-2 — Network-before-local ordering and no durable retry

**Severity: High. Confidence: High.**

Evidence: `ScrobbleService.kt:76-134`

```kotlin
scope.launch {
    if (!offlineModeManager.isQueueEnabled()) {
        try { api.scrobble(...) } catch (...) { ... }
    }
    ensureTrackRow(...)
    trackDao.incrementPlayCount(trackId)
    ...
    listenEventDao.insert(...)
}
```

Problems:

- The KDoc says “Local-first: play count + listen event ALWAYS written”, but the
  actual order is network first. A slow/hanging API call delays the local write;
  process death during that window loses the listen.
- The catch only logs. There is no pending queue, WorkManager job, or retry.
- `lastScrobbledTrackId` is set in `MediaService.scrobbleTrackIfNeeded()`
  (`MediaService.kt:1880`) before the async write. If the write is lost, the
  dedupe guard can prevent a retry until the same track is replayed.
- `offlineModeManager.isQueueEnabled()` gates the API, but the local write is
  unconditional. That part is correct; the ordering is the issue.
- **Offline-gate mismatch:** CP uses
  `LocalOnlyPolicy.isLocalOnly(...)` (`MediaService.kt:3113-3115`), which
  includes “software offline”, “no OS network”, and “cellular hard-local”
  (`LocalOnlyPolicy.kt:10-15`). `ScrobbleService` gates only the software
  toggle (`ScrobbleService.kt:90,53`). On an OS-network-down or
  cellular-hard-local session, CP correctly skips the network, but the scrobble
  path still attempts it and waits for the failure before writing locally. This
  is not data loss by itself, but it widens the MET-2 window on exactly the
  sessions where the network is known to be unusable.

Impact:

- Offline is handled, but “online but flaky” and “OS network down” are not.
- A failed/lost scrobble also means a missing `listen_events` row, so Profile
  and Daily Mix both undercount.
- The user sees no indication.

Recommended fix:

1. Write local rows first (in a transaction), then attempt the server call.
2. Reuse the existing `listen_events` row with a `synced`/`sync_attempts` flag
   (simpler than a second outbox table) and drain it with
   WorkManager/connectivity callback. Retry transport/5xx failures; do not retry
   a definitive 4xx rejection.
3. Set `lastScrobbledTrackId` only after the local transaction commits; on
   failure, clear it or let the pending-event drain retry.
4. Add a bounded timeout to the API call so a hung socket cannot delay the local
   path.
5. Use the same `LocalOnlyPolicy` gate as CP (or a shared
   `canReachServer()` helper) so OS-network-down sessions do not attempt a call
   they know will fail.

Test requirements:

- API throws → local increment + event still happen.
- API hangs → local write happens before the call returns (use a fake with a
  latch).
- Process restart → outbox row is retried.
- Duplicate completion events → one event.

### MET-3 — Read-modify-write `INSERT OR REPLACE` can clobber play counts

**Severity: High. Confidence: High (race); Medium (frequency).**

Evidence:

- `ScrobbleService.ensureTrackRow()`: `ScrobbleService.kt:136-178`
  - `val existing = trackDao.getTrack(trackId)`
  - `existing.copy(...)` preserving all fields
  - `trackDao.upsert(entity)`
- `TrackDao.upsert` is `@Insert(onConflict = OnConflictStrategy.REPLACE)`:
  `Daos.kt:510-514`.
- `nowPlaying()` calls `ensureTrackRow()` on every `isPlaying` transition
  (`MediaService.kt:950-967`), not just on scrobble.

Race sequence:

1. Scrobble A reads `play_count = 5`.
2. `nowPlaying`/scrobble B reads `play_count = 5`.
3. A writes `copy(play_count = 5)` via REPLACE.
4. A increments to 6.
5. B writes `copy(play_count = 5)` via REPLACE → the increment is lost.

The same pattern can clobber `last_played_at`, `starred_at`, `is_disliked`,
`cached_file_path`, and download flags if a concurrent writer has a stale copy.

Impact:

- Daily Mix weight input drifts downward.
- Last-played/recency data can be reset.
- Local cache/download state can be lost in the worst interleaving.
- The existing test “ensureTrackRow preserves play count” is single-threaded and
  cannot catch this.

Cross-cutting REPLACE note: `DailyMixRepository.prepare()` calls
`populateAllTrackGenres()` / `populateGenresFromCachedGenreSongs()`
(`DailyMixRepository.kt:302-305`), which are full-table `INSERT OR REPLACE`
statements on `tracks` (`Daos.kt:59,73,83`). Room serializes writers and a
single SQL statement has a consistent snapshot, so this is not the same
read-copy-write race as `ensureTrackRow`. It is still a second REPLACE writer
and a large write-amplification pass; measure it and keep it out of the
concurrent metadata path if possible. Do not treat “REPLACE with a SELECT that
preserves columns” as automatically race-free.

Recommended fix:

- Do not use REPLACE for metadata enrichment. Use:
  - `INSERT OR IGNORE` to create a missing row;
  - a targeted `UPDATE tracks SET title = COALESCE(:title, title), ... WHERE id = :id`
    for existing rows.
- Or use a Room `@Transaction` `ensureTrackRow` that reads and updates only
  metadata columns, never the local-weight columns.
- `nowPlaying` should not write a full row on every play; it only needs metadata
  if the row is missing.

Test requirements:

- Concurrent `nowPlaying` + `scrobble` on the same track preserves the
  incremented `play_count`.
- A late metadata upsert does not overwrite `last_played_at` / `is_disliked` /
  `cached_file_path`.

### MET-4 — Play count and listen event are not atomic

**Severity: Medium. Confidence: High.**

Evidence: `ScrobbleService.kt:106-128` — `incrementPlayCount` then
`listenEventDao.insert`, no transaction.

Impact:

- If the insert fails after the increment, `play_count` and `listen_events`
  disagree.
- Daily Mix (play_count) and Profile (listen_events) show different histories.
- A crash between the two writes leaves a permanent skew.

Recommended fix:

- Add a `@Transaction` DAO method, e.g. `recordListen(trackId, event)` that does
  the increment and insert together. Room supports `@Transaction` on a DAO
  method that calls other suspend DAO methods.
- If the server call is moved after this, the local state is always consistent.

### MET-5 — The event schema cannot represent skips or completion

**Severity: Medium. Confidence: High.**

Evidence: `Entities.kt:600-623` — `listen_events` has `track_id`,
`listened_at`, `listened_seconds`, denormalized artist/album/genre/title,
`is_backfill`. No `duration_seconds`, `completed`, `started_at`, `ended_at`,
`source`/context, or `skipped` flag.

Impact:

- Only listens that pass the Last.fm threshold are written. A track skipped at
  20% leaves no event, so the system cannot learn “this was rejected”.
- A track played to 51% and skipped is indistinguishable from a full listen for
  count purposes; only `listened_seconds` hints at it, and duration is not in
  the row (it can be joined from `tracks`, but the row itself is not
  self-contained).
- There is no context (mix, album, artist radio, CP), so affinity cannot be
  attributed.
- No prune policy; the table grows one row per scrobble forever (ADR-0046 says
  acceptable for personal scale, but it is unbounded).

Recommended fix:

- Add optional `duration_seconds`, `completed`, and `context` columns in a
  future migration (nullable/defaulted for backfill).
- If skip learning matters, write a lightweight `play_sessions` row at start
  and update it at stop/skip; do not overload `listen_events` with incomplete
  plays.
- Add a retention policy or aggregate rollup for old events if the library is
  large.

### MET-6 — Unbounded recursive CTE in migration 52→53

**Severity: Low. Confidence: High.**

Evidence: `AppDatabase.kt:884-950` uses
`WITH RECURSIVE expand ... WHERE n > 1` to expand each track's `play_count`
into N rows.

Impact:

- The number of rows is `Σ play_count` across all tracks, not the maximum of a
  single track. A 50k-track library averaging 100 plays expands to ~5M rows;
  that is the realistic ANR/hang risk, even though each individual count is
  small.
- `play_count` is an `Int` with a `MIN(..., 2147483647)` guard in the increment
  query (`Daos.kt:520`), so a pathological single value is also possible, but it
  is not the primary risk model.

Recommended fix:

- Cap the expansion per track (e.g., `min(play_count, 100)`) and rely on
  `is_backfill`/`play_count` for all-time counts. The backfill rows are
  synthetic and excluded from honest minutes anyway.
- Or insert one aggregate row per track with `listened_seconds =
  duration * play_count` and mark it backfill, instead of N rows. This changes
  “plays” semantics; choose deliberately.

### MET-7 — What is correct

It is worth recording the parts that hold up, so fixes do not regress them:

- **Threshold:** Last.fm-aligned (>30s; >=50% or >=4min) and pause-aware
  accumulation (`ScrobbleDuration.kt:10-41`, `MediaService.kt:1901-1935`).
- **Honest seconds:** accumulated content-heard, clamped to track duration
  (`ScrobbleService.kt:108-114`).
- **Backfill semantics:** migration 53→54 marks existing rows `is_backfill=1`
  (`AppDatabase.kt:955-962`); Profile excludes backfill minutes but includes it
  for all-time plays (`ProfileViewModel.kt:58-69`).
- **Profile period bounds:** calendar week/month/year, device timezone, `[start,
  end)` (`ProfileStatsMath.kt:28-59`).
- **Dedupe:** `lastScrobbledTrackId` prevents duplicate completion events within
  a service instance (`MediaService.kt:1871-1880`).
- **Local row preservation intent:** `ensureTrackRow` explicitly copies the
  existing row to preserve weights (`ScrobbleService.kt:147-176`); the problem
  is the REPLACE/race, not the intent.
- **Only incremental writer:** `incrementPlayCount` is documented as being
  called only from `ScrobbleService`; keep that invariant when adding a
  transaction.

---

## 5. Continuous Play findings

### CP-1 — Journal coverage is incomplete, so CP has little to work with

**Severity: High. Confidence: High.**

Evidence:

- `PlaybackManager` journals only when **both** `sourceType` and `sourceId` are
  non-null:
  - `playAlbum`: `PlaybackManager.kt:487-489`
  - `shuffleAlbum`: `PlaybackManager.kt:586-588`
  - `playSingleTrack`: `PlaybackManager.kt:615-617`
  - `pushContext`: `PlaybackManager.kt:369-371`
- The following call sites omit those arguments:
  - `MixDetailScreen.playTrack()`: `MixDetailScreen.kt:706-710`
    (`playAll()` at 712-724 does pass a source)
  - `AlbumDetailViewModel.playTrack()`: `AlbumDetailViewModel.kt:117-121`
  - `ArtistDetailViewModel.playTrack()`: `ArtistDetailViewModel.kt:382-387`
  - `PlaylistDetailViewModel.playTrack()`: `PlaylistDetailViewModel.kt:412-416`
  - `FavoritesScreen.playTrack()`: function at `FavoritesScreen.kt:154`;
    `playAlbum` call at 174
  - `DownloadsScreen`: `ui/settings/DownloadsScreen.kt:274`
  - `NavHost.kt`: search single-track callbacks at 719 and 733; the album
    callback at 947 is also a single-track call
- `playAll()`/`shuffle()` paths generally do pass source:
  - Album: `AlbumDetailViewModel.kt:124-157`
  - Artist: `ArtistDetailViewModel.kt:393-407`
  - Playlist: `PlaylistDetailViewModel.kt:366-410`
  - Mix: `MixDetailScreen.kt:712-746`
- `AlbumDetailViewModel.playAlbumWithoutJournal()` is a private unused method
  (`AlbumDetailViewModel.kt:159-161`) — dead code that documents the gap.

Impact:

- A user who taps a track in an album/artist/playlist/favorites/downloads list
  gets no journal entry for that context. CP then relies on the similar-songs
  API or an unrelated older journal entry.
- The journal is the offline fallback; missing entries make offline CP much more
  likely to have no candidates.
- The behavior is inconsistent: “Play all” seeds CP, “tap track 3” does not.

Recommended fix:

- Pass `sourceType`/`sourceId` at every track-tap call site. Use stable source
  types (`album`, `artist`, `playlist`, `favorites`, `downloads`, `search`,
  `genremix`).
- Consider a `PlaybackManager` helper that infers a source from a supplied
  context object, so call sites cannot forget both arguments.
- Alternatively, journal with `sourceType` alone and a synthetic `sourceId`
  (e.g., `"favorites"`); the current all-or-nothing gate is the root cause.
- Remove the dead `playAlbumWithoutJournal()`.

Test requirements:

- Each track-tap entry point produces a `queue_journal` row with the expected
  source type/id.
- A subsequent queue end with no similar API and offline mode resolves
  candidates from that row.

### CP-2 — Disliked tracks can be autoplayed

**Severity: High. Confidence: High.**

Evidence:

- Similar path: `MediaService.maybeLoadContinuousPlay()` fetches
  `scrobbleService.fetchSimilarSongs(mediaId, count = 20)` and only filters
  `it.id !in currentTrackIds` (`MediaService.kt:3121-3140`).
- Journal path: `ContinuousPlayLoader.resolve()` checks only `loadTrack` and
  `localOnly` (`ContinuousPlayLoader.kt:18-48`); it does not inspect
  `entity.isDisliked`.
- Album/artist dislikes are not checked in either CP path.
- `DailyMixRepository.filterPlayableMixTrackIds()` already implements the
  correct SQL/name-only filter (`DailyMixRepository.kt:458-474`) on top of
  `TrackDao.filterMixPlayableIds` (`Daos.kt:489-497`, which joins album/artist
  dislike ledgers), but CP does not call either.

Impact:

- A user who thumbs-downed a track/album/artist can still hear it in the
  autoplay tail.
- The “autoplay” label makes it feel like a deliberate recommendation, so the
  trust violation is worse than a random queue.
- This contradicts the Daily Mix dislike guarantees (ADR
  `0037-daily-mix-parent-dislike-and-fill-mode.md`; the other file with the
  same number, `0037-library-browse-search-state-slices.md`, is unrelated).

Recommended fix:

- For journal candidates: filter `entity.isDisliked` in
  `ContinuousPlayLoader.resolve()` or before it.
- For album/artist dislikes: batch-query the dislike ledgers (or reuse
  `filterPlayableMixTrackIds` for IDs that exist in Room).
- For similar-songs results that are not yet in Room: at minimum drop IDs
  already known to be disliked; consider creating a stub row or fetching
  metadata before append so the filter can be applied.
- Add a final `filterPlayableMixTrackIds` pass on the combined candidate list in
  `maybeLoadContinuousPlay`.

Test requirements:

- Journal entry contains a disliked track + a clean track → only the clean track
  is appended.
- Similar API returns a disliked track → it is dropped.
- Album/artist dislike (name-only and id-based) is honored.

### CP-3 — The selector is random recency-weighted and ignores the useful signals

**Severity: High. Confidence: High.**

Evidence: `JournalTrackSelector.kt:20-44`

```kotlin
val effectiveEntries = entries.take(50)
...
val weight = maxOf(1, effectiveEntries.size - index)
val copies = minOf(weight, 3)
repeat(copies) { weightedPool.addAll(ids.shuffled().take(5)) }
return weightedPool.shuffled()
    .filter { it !in currentTrackIds }
    .distinct()
    .take(maxTracks)
```

Problems:

- It uses only journal recency. It ignores `tracks.play_count`, `user_rating`,
  `starred_at`, `last_played_at`, genre/artist similarity, and dislikes.
- It ignores `QueueJournalEntity.position`, so it cannot continue from the
  current point in the source queue.
- `take(50)` silently discards older journal entries even though the configured
  cap is 100 (`PlaybackManager.kt:130`, `setJournalCap` 10–500).
- `take(5)` per entry means a 100-track album contributes at most 5 random
  tracks per copy; `copies` repeats the sampling, which can duplicate the same
  five.
- `currentTrackIds` is the entire player queue, not just the current index
  (`MediaService.kt:3103-3109`). For a large “play all” queue, almost every
  candidate is excluded.
- The result is a random sample, not a “similar to what is playing” selection.
- `JournalTrackSelector` is a pure object with no DAO dependency. Adding
  rating/play-count/history scoring is a **signature and dependency change**,
  not a one-line tweak. The `handles null trackDao gracefully` test name is
  stale — the selector no longer takes a DAO at all.

Impact:

- Autoplay can jump genres/moods.
- A 100-track album/playlist is under-sampled.
- A user with a large queue may get no CP candidates at all.
- Offline CP is especially weak because it has no similar API.

Recommended fix:

- Replace with a scored selector:
  - Seed from the current track's artist/genre/album and the source context.
  - Add affinity from `tracks.play_count`/rating and recent `listen_events`.
  - Penalize recently played and disliked tracks.
  - Prefer tracks near `QueueJournalEntity.position` in the source queue.
  - Use a weighted sample without replacement, with a diversity cap per
    artist/album.
- Use the full configured journal cap (or a clearly documented effective cap).
- Exclude only tracks at/after the current index, not the entire queue, when the
  goal is “continue this context”.
- Consider using the Daily Mix pool/weights as a fallback scorer; it already
  encodes genre purity, ratings, and dislikes.

Test requirements:

- A journal entry with position 40 and one with position 2 → tracks after
  position 40 are preferred for continuity.
- High-rated/played tracks are more likely than unrated ones.
- Recently played tracks are down-ranked.
- A 100-track entry can surface more than 5 unique tracks across selections.
- `currentIndex` only excludes the already-played prefix.

### CP-4 — Similar-songs drift and unverified appends

**Severity: Medium. Confidence: High.**

Evidence:

- Similar call uses `mediaId` (the last item) as the seed
  (`MediaService.kt:3122-3124`).
- When the last item is itself an autoplay track, the next chain is seeded from
  that autoplay track, not the original context/artist. Over several bursts this
  drifts away from the user's starting point.
- `appendToContext()` returns `Unit` (`PlaybackManager.kt:726-754`), but the
  caller unconditionally sets `hasLoadedContinuation = true`
  (`MediaService.kt:3128-3131` and `3177-3183`).
- If `appendToContext` hits the `player == null` early return, CP will not
  retry because the flag is already set. (The `alignedTracks.isEmpty()` guard is
  unreachable at these two call sites, which both pre-check for non-empty
  input.)
- `appendToContext` mutates Dual (`dualQueue.addAllToContext(items)`) **before**
  the `val player = PlayerHolder.player ?: return` early return
  (`PlaybackManager.kt:732-748`). On the null-player path, Dual grows but the
  queue is not persisted and Cast is not emitted. The caller can still set
  `hasLoadedContinuation = true`, leaving Dual and the player/restore state
  inconsistent. This is a separate failure mode from the unreachable empty-list
  guard.
- Similar results are not written to Room before append; they exist only as
  queue items/URLs. This is acceptable for playback, but it means later
  like/dislike/CP scoring has no local row for them.

Impact:

- Autoplay can gradually move from the user's mix to a completely different
  genre.
- A failed append can silently disable CP for the rest of the queue.
- Similar tracks cannot be filtered by local dislike/rating until they are
  persisted.

Recommended fix:

- Keep an explicit CP anchor: the original context's seed track/artist or the
  last non-autoplay track. Seed similar-songs from the anchor, not from the
  latest autoplay tail item.
- Change `appendToContext` to return the number of appended items (or
  `Boolean`); set `hasLoadedContinuation` only when `> 0`.
- Move the `player == null` guard before the Dual mutation, or roll back the
  Dual append when the player is unavailable; never persist/emit a half-applied
  append.
- Persist minimal `TrackEntity` rows for similar results before appending, so
  filters and future scoring can see them.
- Add artist/album diversity to the similar list (e.g., max 2 per artist).

Test requirements:

- `appendToContext` no-op → flag stays false and a retry can occur.
- `appendToContext` with a null player → Dual is unchanged (or fully rolled
  back) and `hasLoadedContinuation` stays false.
- Two consecutive CP bursts keep the original anchor.
- Similar results are persisted/filterable.

### CP-5 — No prefetch/buffer; CP waits for the last item

**Severity: Medium. Confidence: High.**

Evidence:

- Trigger is `shouldLoadContinuation()` when `currentIndex >= mediaItemCount - 1`
  (`ContinuousPlayGate.kt:35-49`) or `STATE_ENDED`
  (`MediaService.kt:980-1014`).
- Similar fetch starts only when that gate fires
  (`MediaService.kt:3111-3201`).
- Local ExoPlayer can advance gaplessly once items are appended, but if the
  fetch/append completes after `STATE_ENDED`, there is an audible gap.
- The Cast path has a separate `preloadNextIfNeeded` (`MediaService.kt:4226-4230`)
  but it is Cast-only and operates on the existing queue; it does not fetch CP
  candidates.

Impact:

- On a slow network, the user hears silence at the end of the mix/queue.
- The CP burst is reactive, not buffered; a second failure has no safety margin.
- The `STATE_ENDED` retry is the only fallback and it is exactly the gap case.

Recommended fix:

- Trigger CP loading when N items remain (e.g., 2–3), not only at the last item.
- Maintain a target autoplay buffer (e.g., 5–10 unplayed autoplay tracks);
  top up when it drops below the low-water mark.
- Fetch similar candidates in the background and cache them (in memory/Room)
  before appending.
- On `STATE_ENDED`, if the append is still in flight, show a buffering state or
  use a local fallback immediately.

Test requirements:

- With a fake slow API, CP appends before the last track ends.
- Buffer top-up triggers at the low-water mark, not only at the end.
- `STATE_ENDED` with an empty buffer falls back without a long gap.

### CP-6 — No local fallback when the journal/similar path yields nothing

**Severity: Medium. Confidence: High.**

Evidence:

- Empty journal → log and return (`MediaService.kt:3149-3157`).
- Candidates empty after `localOnly` filter → log and return
  (`MediaService.kt:3166-3176`).
- There is no query for “random cached tracks”, “same genre”, or “same artist”
  in the fallback path.
- Offline mode forces `localOnly = true` (`MediaService.kt:3113-3115`), and
  `ContinuousPlayLoader` keeps only rows with a non-blank `cachedFilePath`
  (`ContinuousPlayLoader.kt:55`).

Impact:

- Offline users with an empty/stale journal get no autoplay at all, even when
  they have a large downloaded library.
- A new user with no journal history has no offline CP.

Recommended fix:

- Add a Room fallback query: same genre/artist/album first, then highest-rated
  playable tracks, then a random playable sample.
- Respect `localOnly` and dislikes.
- Use the Daily Mix pool for the current genre as the fallback source.
- Log which fallback tier produced the candidates for diagnostics.

Test requirements:

- Empty journal + downloaded tracks → candidates returned offline.
- Empty journal + no downloads + online → similar API still tried, then local
  fallback if the API returns nothing.
- Disliked tracks are excluded from the fallback.

### CP-7 — The autoplay tail grows without a cap and is persisted

**Severity: Medium. Confidence: High.**

Evidence:

- Each CP burst appends 10 journal tracks or up to 20 similar tracks
  (`MediaService.kt:3124`, `JournalTrackSelector` default 10).
- `appendToContext` adds to Dual and persists the full queue
  (`PlaybackManager.kt:726-754`).
- `clearAutoplayQueue()` is manual (`PlaybackManager.kt:759-789`).
- No cap/prune on the number of autoplay rows.

Impact:

- A long session can accumulate hundreds of autoplay rows.
- Queue persistence grows; restore/room writes get slower.
- The “Up Next” UI becomes noisy; users must manually clear.
- The queue is restored with the old autoplay tail on next launch, which may be
  stale.

Recommended fix:

- Cap the autoplay tail (e.g., 50 tracks or 2–3 bursts) and evict the oldest
  autoplay rows when appending.
- Do not persist more than the cap.
- Consider not restoring autoplay rows older than N hours/days.
- Keep a clear “Autoplay” section label and the existing manual clear.

Test requirements:

- 5 bursts → tail size <= cap; oldest rows removed.
- Restore after process death respects the cap.
- `clearAutoplayQueue` still removes only autoplay rows.

### CP-8 — Documentation is stale

**Severity: Medium. Confidence: High.**

Evidence: `docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md`

- Line 9: “append up to 10 journal-selected tracks”.
- Lines 23-29: journal write list and “Not journaled” list.
- Lines 35-36: “Gate via ContinuousPlayGate (!cast, ...)”.
- Lines 48-54: “Continuous Play skipped while casting”.

Current code:

- Similar-songs API is primary with `count = 20` (`MediaService.kt:3121-3140`),
  then journal fallback.
- `ContinuousPlayGate` has `isCasting` but deliberately ignores it
  (`ContinuousPlayGate.kt:35-49`); comments cite ADR-0093 allowing Cast.
- `playSingleTrack` journals when source args are supplied
  (`PlaybackManager.kt:615-617`).

ADR-0053 also says similar-songs is “out of scope”, but ADR-0094 added it later.
The ADR was not amended, so the docs and the code disagree.

Impact:

- New maintainers implement to the stale doc (e.g., re-add a Cast gate, assume
  10 tracks).
- QA expectations are wrong.

Recommended fix:

- Update `CONTINUOUS_PLAY_BEHAVIOR_REPORT.md` to describe the current
  similar-first + journal-fallback flow, the 20/10 counts, Cast support, and
  the actual journal call sites.
- Add an addendum/amendment to ADR-0053 referencing ADR-0094.
- Document the 50-entry/5-track selector caps.

### CP-9 — `QueueJournalEntity.position` is written but never read

**Severity: Low. Confidence: High.**

Evidence:

- `QueueJournalEntity.position` (`Entities.kt:542-550`).
- `journalQueue` stores the start index (`PlaybackManager.kt:942-964`).
- `JournalTrackSelector` never reads `position` (`JournalTrackSelector.kt:20-44`).

Impact:

- The data needed for “continue this album/playlist from where it was” is
  already stored but unused.
- The selector cannot distinguish a queue started at track 1 from one started
  at track 40.

Recommended fix:

- Use `position` in the candidate scorer (prefer IDs after the saved position).
- If the journal is meant only for “source context”, store the current index
  separately; today `position` is the start index, which is still useful.

### CP-10 — Cast behavior: code is ahead of docs, not necessarily buggy

**Severity: Low (documentation). Confidence: High.**

The gate comment and code explicitly allow Cast (`ContinuousPlayGate.kt:8-10,
35-49`; `MediaService.kt:3082-3090`). The stale report says otherwise. Treat
this as a docs fix (CP-8), not a code defect. The Cast path does use Dual as the
timeline source of truth, which is the right design per ADR-0095.

---

## 6. Prioritized improvement plan

### P0 — correctness / data loss

1. **Fix `ScrobbleService` lifecycle (MET-1).**
   - Remove `scrobbleService.dispose()` from `MediaService.performDestroyCleanup()`.
   - Or move the scope to an application-scoped owner.
   - Add a service-recreate test.
2. **Fix low-diversity underfill (DM-1).**
   - Relax the adjacency rule only when no alternative exists; never return 1
     track for a large single-artist pool.
   - Add size-bound tests with seeded RNG.
3. **Fix the unlistened-pool cliff (DM-2).**
   - Keep the full pool; prefer unlistened as a quota, not a replacement.
   - Add 29/31 unlistened tests.
4. **Filter dislikes in CP (CP-2).**
   - Track-level first, then album/artist ledgers.
   - Reuse `filterPlayableMixTrackIds` where possible.

### P1 — correctness / quality

5. **Make regeneration usage-based and always checked on Home (DM-5).**
   - Count listens since `createdAt`; run a cheap stale check even when a
     yesterday card exists.
6. **Make local writes first and durable (MET-2).**
   - Local transaction, then network; add an outbox/retry.
7. **Remove the REPLACE race (MET-3).**
   - Targeted metadata UPDATE / INSERT OR IGNORE; never REPLACE local weights.
8. **Fix Phase-3 fill (DM-3).**
   - Dynamic last artist, full weight map, listened exclusion, album diversity.
9. **Improve CP candidate scoring + prefetch + offline fallback (CP-3/5/6).**
   - Scored/hybrid selector, N-remaining trigger, buffer target, local fallback.
10. **Complete journal coverage (CP-1).**
    - Pass source args at every track-tap call site; remove dead code.

### P2 — observability / maintainability

11. **Add skip/completion/context data (MET-5).**
    - If the product wants to learn from skips, add a session table; otherwise
      document that only thresholded listens are recorded.
12. **Cap/prune the autoplay tail (CP-7).**
13. **Update docs/ADRs (CP-8) and tests (DM-7).**
14. **Cap the 52→53 migration expansion (MET-6).**
15. **Decide whether weight is a ranking or a probability (DM-6).**
16. **Use `position` in the journal selector (CP-9).**

### Suggested implementation order

```
P0: MET-1 → DM-1 → DM-2 → CP-2
P1: DM-5 → MET-2 → MET-3 → DM-3 → CP-3/5/6 → CP-1
P2: MET-5 → CP-7 → docs/tests → MET-6 → DM-6 → CP-9
```

Each P0/P1 item should land with a focused unit test that fails on the current
code. The tests can be written first; the current suite is green partly because
the assertions are too weak.

---

## 7. Concrete design sketches

### 7.1 Daily Mix selection loop (DM-1/DM-2/DM-3)

The sketch is illustrative, not compilable: `result`, `resultIds`,
`artistCounts`, `albumUsage`, and `chooseByDiversityAndWeight` are elided.
`resultIds` is `result.mapTo(mutableSetOf()) { it.id }`, and
`chooseByDiversityAndWeight` is the current Phase-1 `minByOrNull` over `pool`
keyed by `albumUsage * 1000 - weightMap[id]`, as long as DM-6 is not adopted.
Also maintain `artistCounts`/`albumUsage` incrementally and index `remaining`
by artist/album; re-filtering the full `remaining` list on every iteration is
`O(n²)` and would be costly on a 5k-track library.

```kotlin
fun generate(...): List<SongInfo> {
    if (songs.isEmpty()) return emptyList()

    val target = random.nextInt(30, 121)
        .coerceAtMost(songs.size + supplemental.size)

    // Keep the FULL pool. "Fresh" is a preference, not a replacement pool.
    val fresh = songs.filter { it.id !in previouslyListenedTrackIds }.toSet()
    val remaining = songs.toMutableList()

    // A fresh target is a soft quota: 70% is an example, not a hard rule.
    val desiredFresh = minOf(fresh.size, (target * 0.7).toInt())
    var freshPicked = 0

    while (result.size < target && remaining.isNotEmpty()) {
        val last = result.lastOrNull()?.artist
        val candidates = remaining.filter { track ->
            track.id !in resultIds &&
                (track.artist != last || remaining.none { it.artist != last })
        }
        if (candidates.isEmpty()) break

        // Prefer fresh candidates until the quota is met; after that, allow
        // familiar candidates. Never cap the target at fresh.size.
        val preferFresh = freshPicked < desiredFresh &&
            candidates.any { it.id in fresh }
        val pool = if (preferFresh) candidates.filter { it.id in fresh } else candidates

        val pick = chooseByDiversityAndWeight(pool, weightMap, albumUsage)
        result += pick
        remaining -= pick
        if (pick.id in fresh) freshPicked++
        // update artistCounts/albumUsage
    }
}
```

Key invariants:

- `result.size == min(target, available)` unless the caller explicitly asked
  for a smaller pool.
- No adjacent same artist while an alternative exists; when no alternative
  exists, allow the same artist rather than returning a one-track result.
- No adjacent same album while an alternative exists (where album data exists).
- Fresh tracks are preferred up to the quota, but familiar tracks fill the
  target; the pool is never truncated to `fresh.size`.
- If weighted sampling is desired (DM-6), replace
  `chooseByDiversityAndWeight` with a seeded weighted sample over `pool`; do not
  split the pool into two `take()` lists, which re-creates the DM-2 starvation
  shape.

### 7.2 Scrobble write path (MET-2/MET-3/MET-4)

```kotlin
fun scrobble(...) {
    scope.launch {
        // 1. Local, atomic, first.
        val row = trackDao.ensureMetadataOnly(...) // INSERT OR IGNORE + UPDATE
        val resolvedDuration = ...
        listenEventDao.recordListen(              // @Transaction
            increment = { trackDao.incrementPlayCount(trackId) },
            event = ListenEventEntity(...),
        )
        // 2. Best-effort server call after the local commit.
        if (!offline) {
            runCatching { api.scrobble(...) }
                .onFailure { outbox.enqueue(trackId, listenedAt) }
        }
        // 3. Dedupe only after the local commit.
        lastScrobbledTrackId = trackId
    }
}
```

Notes:

- `ensureMetadataOnly` must not write `play_count`, `last_played_at`, star,
  dislike, or cache columns.
- `recordListen` must be a single Room transaction.
- The outbox can be a small table or WorkManager input; cap retries.

### 7.3 Continuous Play candidate selection (CP-2/CP-3/CP-6)

```kotlin
suspend fun selectCandidates(seed: Track, currentIds: Set<String>): List<Track> {
    val journal = queueJournalDao.getAllRecent()
    val recent = listenEventDao.recentTrackIds(since = now - 7.days)
    val disliked = metadataDao.dislikedTrackIds()

    val weighted = journal.asSequence()
        .flatMap { entry -> decode(entry).map { it to entry } }
        .filter { (id, _) -> id !in currentIds && id !in disliked }
        .map { (id, entry) -> WeightedCandidate(id, score(id, entry, seed, recent)) }
        .filter { it.weight > 0 }
        .toList()

    // Seeded weighted sampling without replacement, not a global sort.
    // A sort would reintroduce the deterministic "same top tracks every day"
    // problem DM-6 criticizes.
    return seededWeightedSample(weighted, count = bufferTarget, random = dailyRandom)
}
```

`score` should combine:

- source match (same album/artist/genre as the seed),
- journal position continuity,
- personal affinity (rating/play count/recent listens),
- recency penalty,
- artist/album diversity penalty.

`seededWeightedSample` should respect diversity caps (e.g., max 2 per artist /
album) while sampling. Do not use `sortedByDescending` for candidate selection;
it makes CP deterministic and repeats the same tracks.

### 7.4 Home freshness check (DM-5)

The predicate must be evaluated against the **latest row for each mix**, not
only today/yesterday. Add a DAO query that orders by date descending:

```kotlin
@Query(
    "SELECT * FROM daily_mix WHERE mix_id = :mixId ORDER BY date DESC LIMIT 1"
)
suspend fun getLatestDailyMix(mixId: Long): DailyMixEntity?

@Query("SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = :dailyMixId")
suspend fun getDailyMixTrackCount(dailyMixId: Int): Int
```

Use the latest row in `generateForMix` before deciding `existingMix == null`:

```kotlin
private suspend fun maybeRefreshStaleMixes(mixes: List<CustomMix>, today: String) {
    val nowMs = System.currentTimeMillis()
    val latest = mixes.mapNotNull { mix -> genreMixDao.getLatestDailyMix(mix.id) }
    val stale = latest.any { row ->
        // Count listens that happened after generation, not lifetime play_count.
        val listenedSince = listenEventDao.countDistinctTracksForMix(
            dailyMixId = row.id,
            startMs = row.createdAt,
            endMs = null,
            includeBackfill = false,
        )
        DailyMixGenerator.shouldRegenerate(
            createdAtMs = row.createdAt,
            totalTracks = genreMixDao.getDailyMixTrackCount(row.id),
            listenedTracks = listenedSince,
            nowMs = nowMs,
            manual = false,
        )
    }
    val missing = mixes.filter { mix ->
        latest.none { row -> row.mixId == mix.id }
    }
    if (stale || missing.isNotEmpty()) generateAll(today, manual = false)
}
```

`countDistinctTracksForMix` is a new `ListenEventDao` query. Joining
`daily_mix_tracks` on the SQL side avoids both the per-mix two-step read and
the SQLite 999 bind-variable limit that `trackIds: List<String>` would hit for
large mixes:

```kotlin
@Query(
    """
    SELECT COUNT(DISTINCT e.track_id)
    FROM listen_events e
    INNER JOIN daily_mix_tracks dmt ON dmt.track_id = e.track_id
    WHERE dmt.mix_id = :dailyMixId
      AND (:includeBackfill = 1 OR e.is_backfill = 0)
      AND e.listened_at >= :startMs
      AND (:endMs IS NULL OR e.listened_at < :endMs)
    """
)
suspend fun countDistinctTracksForMix(
    dailyMixId: Int, // matches DailyMixEntity.id and daily_mix_tracks.mix_id
    startMs: Long,
    endMs: Long? = null,
    includeBackfill: Boolean = false,
): Int
```

Call `maybeRefreshStaleMixes()` from `loadDailyMixes()` regardless of
`cards.size`. `generateForMix` should also call `getLatestDailyMix` so the
“older than yesterday” case is evaluated by the predicate rather than falling
into the unconditional `existingMix == null` branch.

`countDistinctTracksForMix` must use `listen_events` (honest, post-migration
events), not `tracks.play_count`; lifetime play count is the root cause of the
false-positive regeneration in DM-5. Backfill rows are excluded by default so a
Last.fm backfill does not mark a fresh mix as listened.

The join starts from the small `daily_mix_tracks` side
(`idx_daily_mix_tracks_mix_id`, 30–120 rows) and probes
`index_listen_events_track_id`; `listened_at` is then a residual filter rather
than an index range. At personal scale one query per mix on Home resume is
cheap; if Home-open latency ever becomes an issue, batch all mixes into one
`GROUP BY dmt.mix_id` query instead of N calls.

---

## 8. Evidence appendix

### 8.1 Key files

| File | Role |
|---|---|
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/DailyMixGenerator.kt` | Selection, weights, regeneration predicate |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/repository/DailyMixRepository.kt` | Pools, generation, persistence, dislike filter |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/repository/ScrobbleService.kt` | Play count, listen event, server scrobble, similar API |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/MediaService.kt` | Threshold, transition/scrobble, CP orchestration, destroy |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/PlaybackManager.kt` | Context start, journal, autoplay append |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/JournalTrackSelector.kt` | CP candidate sampling |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/ContinuousPlayLoader.kt` | Candidate hydration/offline filter |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/ContinuousPlayGate.kt` | CP trigger conditions |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/ui/library/LibraryViewModel.kt` | Home lazy generation/cards |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/db/Daos.kt` | Play-count, listen-event, daily-mix queries |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/db/Entities.kt` | Track, daily mix, journal, listen event schemas |
| `compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/db/AppDatabase.kt` | Migrations 52→53/53→54 |
| `docs/adr/0046-listen-events-profile-metrics.md` | Intended listen-event semantics |
| `docs/adr/0047-profile-listen-metrics-honesty.md` | Backfill/honesty semantics |
| `docs/adr/0053-continuous-play-autoplay-tail.md` | CP autoplay design |
| `docs/adr/0094-api-hygiene-critical-path.md` | Similar-songs primary |
| `docs/adr/0128-profile-lastfm-threshold-and-charts.md` | Last.fm threshold |
| `docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md` | Stale CP report |
| `docs/DAILY_MIX_BEHAVIOR_REPORT.md` | Daily Mix pipeline report |

### 8.2 Test blind spots

- `DailyMixGeneratorTest`:
  - `handles single artist gracefully` → `isNotEmpty()` only.
  - `two artists with many tracks are interleaved` → adjacency only, no size.
  - `retries exhaust gracefully` → `isNotEmpty()` + upper bound.
  - No test for `previouslyListenedTrackIds` with a large pool.
  - No test for Phase-3 same-artist/album runs.
  - No test for supplemental weights.
- `DailyMixRepositoryTest`:
  - Direct `generateAll` tests, not the Home `loadDailyMixes()` flow.
  - “regenerates a 25h-old mix with listens” uses lifetime `playCount = 5` and
    does not distinguish plays before/after `createdAt`.
- `ContinuousPlayTest`/`JournalTrackSelectorTest`:
  - Recency weighting and dedupe only; no rating/history/position/dislike.
  - `caps effective entries at 50` documents the cap rather than questioning it.
  - `handles null trackDao gracefully` is a stale name: `JournalTrackSelector`
    is pure and takes no DAO. Adding rating/play-count scoring is a signature
    change.
- `ScrobbleServiceTest`:
  - Single-threaded; no lifecycle/dispose/recreate test.
  - `dispose cancels scope` (lines 100-104) has no assertion and encodes the
    production disposition; delete or rewrite it.
  - No concurrent `ensureTrackRow` test.
  - No network-first ordering test.
  - No localOnly/OS-network gate test.
  - No outbox/retry test.

### 8.3 Open questions for product

1. Should “Daily Mix” always include familiar favorites, or is it a
   fresh-only discovery surface? The current code tries to do both with a binary
   threshold.
2. Is “yesterday” meant as “not in yesterday's generated list” or “not actually
   listened yesterday”? The current code implements the former; the field name
   and user expectation suggest the latter.
3. Should CP continue the current context (album/playlist/mix) or explore
   similar-but-new music? The current similar-first path explores; the journal
   path continues. This should be explicit.
4. Is it acceptable for `listen_events` to represent only thresholded listens,
   or do we need skip/abandon data for future recommendations?
5. Should the autoplay tail survive process death indefinitely, or expire?

---

## 9. Cross-model review

This review followed the `android-feature-loop` cross-model process. The
orchestrator (DeepSeek) is not the sole validator of its own document.

### 9.1 Roles and verdicts

| Stage | Model | Role | Verdict |
|---|---|---|---|
| Discover / edge cases | Mistral | Independent code exploration and challenge of the draft findings | Identified several real areas (journal coverage, dislike filtering) but also produced claims that did not survive source verification (see 9.3). |
| Final audit | DeepSeek | Independent verification of every high-risk claim against source; adversarial correction pass | First pass **NOT OK** (design sketch defects and line drift). After corrections, focused re-audit **OK**. |
| Cross-model challenge | Mistral | Challenge the corrected document, the audit verdict, and the remaining risks; specifically re-check DM-6/MET-6/CP-2 | **OK**. DM-6, MET-6, and CP-2 all VERIFIED; severities judged fair; no material challenge to the audit’s OK. |

Both validators independently reasoned OK on the corrected document. The
deliverable is therefore accepted as a review artifact, not as an implementation
approval.

### 9.2 Corrections accepted from the audit

The first DeepSeek audit (NOT OK) produced actionable corrections, all applied:

1. **DM-1** now says “exactly one track from the **primary pool**” and states
   that supplemental can add more only when cross-mix fill is enabled. The
   original wording made the Critical rating look universal.
2. **DM-2** clarifies that the 30-track threshold replaces the pool rather than
   multiplying with DM-1, and that the two defects compound only through
   candidate-set capping.
3. **DM-5** line references were corrected (`loadDailyMixes` 835-858, gate
   846-854, `readMixCards` 861-897) and the “two doors” explanation was kept:
   the Home gate and the repository predicate are independent.
4. **DM-6** now states that the existing “5x” test only asserts
   `starCount >= 10`; it does not measure a ratio, so it cannot distinguish 5×
   weighting from uniform round-robin.
5. **MET-1** notes that the existing `dispose cancels scope` test has no
   assertion and encodes the production bug.
6. **MET-2** states the `LocalOnlyPolicy` nuance correctly: `ScrobbleService`
   gates on the software offline toggle (`isQueueEnabled()`), while CP gates on
   `LocalOnlyPolicy` (offline **or** no OS network **or** cellular-hard-local).
7. **MET-3** frames the `INSERT OR REPLACE` hazard as a lost-increment /
   `SUM(play_count)` drift, not a single-row `Int` overflow. The `MIN(...,
   2147483647)` clamp is noted but is not the primary risk model.
8. **MET-6** uses the total `Σ play_count` row-count model for the recursive
   CTE, not a per-row overflow model.
9. **CP-1** line references were verified and corrected; ADR citations use the
   full slug because two unrelated files share number `0037`.
10. **CP-2** wording now distinguishes “filter exists but CP does not call it”
    from “no filter exists”.
11. **CP-3** notes that `JournalTrackSelector` is a pure function today; adding
    rating/play-count/history scoring is a signature change, not a local edit.
12. **CP-4** distinguishes the reachable `player == null` early return from the
    unreachable empty-aligned-list guard at the two CP call sites, and records
    that `dualQueue.addAllToContext(items)` mutates Dual **before** the null
    check.
13. **§8.2** test blind spots were expanded to the actual assertions.
14. **§7.4** was reworked to use the latest row (`getLatestDailyMix`) instead of
    today/yesterday, a `listen_events` join (`countDistinctTracksForMix`) instead
    of lifetime `play_count`, the real `genreMixDao` name, `Int` daily-mix IDs,
    and the real `generateAll(date, manual)` signature.
15. **§7.1** now marks the sketch as illustrative, declares the elided
    identifiers, and calls out the `O(n²)` re-filter cost on large libraries.

### 9.3 Claims rejected after source verification

The earlier Mistral pass contained inaccuracies that were rejected rather than
propagated:

- **“Single artist produces repeated tracks up to 30–120.”** Rejected. The
  loop requeues the artist twice and exits on `consecutiveSkips < queue.size *
  2`; the primary result is exactly **1** track. The document records the
  correct trace.
- **“The 5x test verifies weighting.”** Rejected. The test asserts only
  `starCount >= 10`; it verifies presence, not a sampling ratio.
- **“`PlaybackManager` has a journal `playTrack`.”** Rejected. Journaling
  happens in `PlaybackManager` context-start methods only when `sourceType` and
  `sourceId` are supplied; there is no separate journal `playTrack`.
- **“`currentTrackIds` is only the current index.”** Rejected. It is the set of
  all player media IDs; using it as the similar-songs exclusion set is
  intentional (avoid duplicates), not a one-item check.
- **“`MediaService.onDestroy` is at line X.”** Rejected; the document uses the
  verified `onDestroy` 2430-2432 / `performDestroyCleanup` 2441 /
  `dispose()` call 2507 ranges.

These rejections are recorded because the disagreement itself is useful: the
final document should not inherit plausible-sounding but unverified claims.

### 9.4 Residual risks and implementation cautions

1. **DM-1 severity depends on real pool shapes.** The single-artist trace is
   exact, but the frequency of a one-artist pool in real libraries is not
   measured. The Critical rating is justified by “normal path can produce a
   one-track mix”; it is not a claim that every user hits it.
2. **The baseline Gradle run is reported, not re-run by the validators.** The
   audit verified source logic, not test execution. Re-run the targeted tests
   after any code change.
3. **MET-1 is a lifecycle bug without a runtime regression test.** The audit
   confirmed the code path and the source comment describing the restart
   self-heal, but no test recreates `MediaService` with the same singleton. The
   recommended test in MET-1 is the proof.
4. **DM-5’s every-other-day alternation is Home-driven.** `SyncingScreen` calls
   `generateAll` on every sync, so a user whose background sync runs daily may
   not alternate. The underlying defects (`existingMix == null` unconditional
   regeneration and lifetime `listenedCount`) are independent of that trace.
5. **§7.4 must be integrated with, not layered on top of,
   `maybeGenerateMixes()`.** `loadDailyMixes()` still has the
   `cards.size < mixes.size` gate and `maybeGenerateMixes()` applies the
   `DailyMixGenerationCoordinator`/`shouldAttemptToday` suppression. A verbatim
   copy of §7.4 would bypass or double-apply those guards. The sketch fixes the
   predicate; the coordinator must be reconciled when implemented.
6. **`shouldRegenerate` is named from the caller’s perspective.** In the §7.4
   sketch, `stale = latest.any { shouldRegenerate(...) }` means “needs
   regeneration”, not “is currently stale”. Rename to `needsRegen` in real code.
7. **Range `DailyMixGenerator.kt:132-154` is correct but padded.** The exact
   Phase-3 block is 134-147; the wider range is stable under the current file
   but will rot first after a refactor.
8. **CP fixes must preserve existing invariants.** Any change to
   `appendToContext`, autoplay appends, or Cast behavior must keep the Dual
   queue consistent (ADR-0067/0091/0095/0111) and add restore-regression tests.
   The audit found the proposed fixes directionally safe, but they are not
   implemented here.
9. **Content staleness is not covered by the DM-5 fix.** `daily_mix_tracks` has
   no FK to `tracks`; a mix can retain IDs whose metadata/track rows were
   removed. The freshness predicate addresses listen-based staleness, not
   missing-track staleness. `readMixCards()` hides zero-track mixes for display,
   but the persistence path still needs a cleanup rule if track deletion is a
   supported flow.
10. **Room Boolean binding is precedent-based, not compile-verified in the
    sketch.** `(:includeBackfill = 1 OR is_backfill = 0)` matches the existing
    DAO idiom and Room binds `Boolean` as 0/1; verify at implementation time,
    especially if the Room/KSP version changes.

### 9.5 Outcome

- The three original questions are answered in §1.
- All Critical/High findings have direct source evidence.
- The document distinguishes defects from accepted trade-offs and from
  product-policy questions (§8.3).
- No production code was changed.
- The review is ready to drive a follow-up implementation plan, with P0 items
  (MET-1, MET-2, DM-1/DM-2) first.

> **Superseded for implementation ordering:** §10 re-validation revises P0 to
> **MET-1 → DM-1 (artist-cap/adjacency) → CP-2**. DM-2 is no longer ahead of
> those. Use §10 severities when scheduling work.

---

## 10. Validation amendment (2026-11-02, docs-only)

Independent source re-trace of every finding in §1–§9 against current `main`,
plus android-orchestrator cross-model challenge (DeepSeek-style audit and
Mistral-style challenge). **No production code was changed.** The original
Gradle suite was not re-run by the validators; this amendment is static + agent
verification only.

Verdict vocabulary: `VERIFIED` | `VERIFIED_WITH_CORRECTION` |
`PARTIALLY_VERIFIED` | `OVERSTATED` | `REJECTED`.

### 10.1 Cross-model status

| Stage | Verdict on unamended doc | Outcome |
|---|---|---|
| DeepSeek-style final audit | **NOT OK** | Null-artist claim in DM-1 wrong; §7 sketches unsafe as copy-paste specs |
| Mistral-style challenge | **NOT OK** | Severity overstatements; ADR trade-offs mislabeled as defects; DM-2 500/31 unreachable; CP-4 sticky-flag overclaim |
| Orchestrator synthesis | Both NOT OK aligned | §10 encodes corrections; amended document is the implementation-facing artifact |

§9’s dual-OK remains a record of the first draft’s internal pass. It does **not**
override §10 for severity or recommended fix shape.

### 10.2 Amended verdict table

| ID | Original severity | Amended | Verdict | One-line evidence / correction |
|---|---|---|---|---|
| DM-1 | Critical | **High** (+ Critical footnote: pure named 1-artist + fill off → size 1) | VERIFIED_WITH_CORRECTION | Named collapse exact (`consecutiveSkips < queue.size * 2`). Systemic bug is `dynamicMaxPerArtist = target/10` under-fill (&lt;~10 artists). Null-artist → size 1 **REJECTED** (see DM-9). |
| DM-2 | High | **Medium** | NEEDS_CORRECTION | Mechanism real; `previouslyListenedIds` only prior-mix IDs (≤~120) so 500/31 production example unreachable. Cliff matters for small pools. |
| DM-3 | High | **Medium** | VERIFIED | Four Phase-3 defects real; `allowCrossMixFill` defaults false. Weights all 1 → sort no-op. |
| DM-4 | High | **Split** | PARTIALLY_VERIFIED | Unused `listen_events` in weighting = ADR-0046 trade-off/enhancement. Yesterday=generated list + lifetime anti-repeat on prior-mix IDs = Medium semantic bugs. Weight-5 needs ≥17 plays (not “5 plays → 5”). |
| DM-5 | High | **High** | VERIFIED_WITH_CORRECTION | Home gate + lifetime listens + today/yesterday lookback hold. Soften “immediate after creation”; SyncingScreen ≠ every sync; see DM-11/DM-13. |
| DM-6 | Medium | **Medium** | VERIFIED | Deterministic priority ranking; “5x” test asserts presence only. |
| DM-7 | Medium | **Medium–High** | VERIFIED (understated) | Weak size asserts; `LibraryViewModelTest` covers gate not multi-day yesterday path; unseeded Random / tautological tests. |
| DM-8 | n/a | n/a | Mostly VERIFIED | Null-artist bullet superseded by DM-9. |
| MET-1 | Critical | **Critical** | VERIFIED | `@Singleton` + `dispose()`→`scope.cancel()`; same-process restart via MainActivity resume self-heal. See MET-1a. |
| MET-2 | High | **High** | PARTIALLY_VERIFIED | Network-before-local on `scrobble` and `nowPlaying`. OkHttp 20/30/30s — “unbounded hang” overstated; recommend `callTimeout`. |
| MET-3 | High | **High** | VERIFIED_WITH_CORRECTION | RMW REPLACE race real. `Daos.kt:59` is DO UPDATE not REPLACE (populate* 73/83 are). Expand to CacheService (MET-9). |
| MET-4 | Medium | **Medium** | VERIFIED | Non-atomic increment+insert; use `db.withTransaction` (cross-DAO). |
| MET-5 | Medium | **Accepted trade-off / Low–Medium enhancement** | VERIFIED | ADR-0046/0128 thresholded listens by design; not a schema defect. |
| MET-6 | Low | **Informational** | VERIFIED | Migration only for DB ≤52 (app now v68); one-time legacy cost. |
| MET-7 | — | — | VERIFIED | Correct behaviors hold (±1 line drift). |
| CP-1 | High | **Medium** | NEEDS_CORRECTION | Track-tap omissions real; Play/Shuffle usually journals. Journal ≠ current-context seed. NavHost:947 is Profile. Decouple from `clearPriority()`. |
| CP-2 | High | **High** | VERIFIED | No dislike filter either path. Remedy: `getDislikedIdsByIds` / `entity.isDisliked` — **not** `filterMixPlayableIds` on similar (starves non-Room IDs). |
| CP-3 | High | **Medium** | VERIFIED_WITH_CORRECTION | ADR-0053 recency design, not taste model. Weight flat for newest ~48/50; single-entry ≤5 candidates; “reservoir” KDoc false. |
| CP-4 | Medium | Drift = product/Low–Medium; null-player = **Low** | PARTIALLY_VERIFIED | Dual mutates before persist early-return. Flag resets on transition; STATE_ENDED `forceRetry`. “Disables rest of queue” overstated. |
| CP-5 | Medium | **Low–Medium** | PARTIALLY_VERIFIED | Gate fires on transition *into* last item (~1 track lead time). |
| CP-6 | Medium | **Medium** | VERIFIED | No local fallback; `getRandomCachedTracks` etc. already exist unused. |
| CP-7 | Medium | **Low** | VERIFIED | Burst ≤20; new context replaces; “hundreds” needs long uninterrupted play. |
| CP-8 | Medium | **Medium** | VERIFIED | Stale report + dead cast string; ADR-0053 lacks supersede pointer. |
| CP-9 | Low | **Low** | VERIFIED | `position` written, unread; start index (shuffle→0), not last-played. |
| CP-10 | Low | **Low** | VERIFIED | Cast allowed in code; docs/dead-resource only. |

### 10.3 Corrections to §3–§5 wording (do not silently rewrite those sections)

1. **DM-1 / DM-8 null-artist.** Queue key uses `s.artist ?: "unknown"`; adjacency compares to raw `SongInfo.artist`. For null artists `"unknown" != null` → adjacency never trips → fill up to `dynamicMaxPerArtist`, not size 1. Collapse-to-1 is **named** single artist only. Literal artist name `"unknown"` collides with the null bucket.

2. **DM-1 reframed.** The durable systemic bug is the per-artist cap (`maxOf(2, targetSize / 10)`): recipes with fewer than ~10 artists under-fill on every run (2 artists @ target 30 → ≤6). Early exit also when the only remaining uncapped artist is the one just picked. One-track mixes are persisted (`generated.isEmpty()` only rejects empty) and appear on Home (`readMixCards` hides only 0-track).

3. **DM-2 production reachability.** `previouslyListenedIds` comes from the previous mix row only (≤~120 tracks). A 500-track pool cannot shrink to 31 unlistened via this caller. Real issues: binary cliff just under 30 fresh; medium/small pools capped near fresh size; optional supplemental expands the cap. Comment at generator L60 is not an ADR.

4. **DM-3.** Keep all four defects; severity Medium because cross-mix fill is opt-in default off. Supplemental weights always 1 → `sortedByDescending` is a no-op (DB/input order); yesterday penalty never applied to supplemental.

5. **DM-4.** Split: integrating `listen_events` into weights is an enhancement against ADR-0046 (“`play_count` still feeds Daily Mix”). Real Medium bugs: yesterday set is generated-list based; anti-repeat uses lifetime `play_count > 0` on prior-mix IDs. Fix example: 5 plays → weight 3; weight 5 needs ≥17 plays.

6. **DM-5.** `maybeGenerateMixes()` KDoc claims stale regeneration; Home only calls when `cards.size < mixes.size`. SyncingScreen runs `generateAll` after user/first-login sync (and rebuild `manual=true`), not “every sync”. “Regenerated immediately after creation” is wrong for the 24h+10% branch (needs ≥24h, or 48h hard max, or `existingMix == null`).

7. **DM-5 × DM-4 (DM-13).** On the every-other-day Home path, Day 3 sees `existingMix == null` → unconditional regen with empty `previouslyListenedIds` and empty `yesterdayIds`. Anti-repeat and yesterday penalty both drop.

8. **CP-1.** Naive “pass sourceType/sourceId everywhere” would call `clearPriority()` (`sourceType != null`). Decouple journaling from Priority clear. Journal feeds later sessions; `currentTrackIds` already excludes the just-started queue. Correct cite: NavHost ~947 is Profile `onTrackClick`, not album.

9. **CP-2 remedy.** Do not final-pass `filterPlayableMixTrackIds` / `filterMixPlayableIds` on similar-songs results (drops IDs not yet in Room and can empty the primary path). Use `trackDao.getDislikedIdsByIds`; journal path can use loaded `entity.isDisliked`; album/artist ledgers separately.

10. **CP-3 / CP-4 / CP-5.** Recency weight is effectively flat for the newest ~48 of 50 entries; single-entry journal yields ≤5 candidates. Dual-before-persist ordering smell remains; null-player path is Low practical risk. Prefetch already starts when entering the last item.

11. **MET-2.** Soften unbounded-hang claim (OkHttp connect/read/write timeouts). Primary loss path vs process death mid-call is often MET-1 cancel-in-flight. `catch (Exception)` swallows `CancellationException`.

12. **MET-3.** Fix `Daos.kt:59` cite. Expand fix scope to `CacheService` full-row writers. Reuse `TrackDao.ensureTrackRow` (`INSERT OR IGNORE`) — rename vs private `ScrobbleService.ensureTrackRow`.

13. **MET-4.** Cross-DAO atomicity needs `database.withTransaction`, not a `@Transaction` method spanning two DAO interfaces.

14. **MET-5 / MET-6.** MET-5 = accepted ADR trade-off. MET-6 = informational legacy migration only.

### 10.4 New discoveries

| ID | Severity | Finding |
|---|---|---|
| DM-9 | Medium | Null-artist / `"unknown"` adjacency key mismatch: consecutive nulls allowed; cap still binds; literal `"unknown"` name collides. |
| DM-10 | Medium (test) | `falls back to appending when supplemental also exhausted` — Phase-3 same-artist filter → size 1 masked by `isNotEmpty()`. |
| DM-11 | Medium | `maybeGenerateMixes` KDoc vs Home `cards.size` gate. |
| DM-12 | Medium–High | Early exit when only last-picked artist remains uncapped (not only pure single-artist). |
| DM-13 | High (compound) | Day-3 null `existingMix` drops anti-repeat + yesterday penalty. |
| DM-14 | Medium | Home `markEmptyAttempt` after all mixes skipped-as-fresh (conflates skip with empty pool). |
| DM-15 | Low | No prune of historical `daily_mix` rows found (confirm before scheduling cleanup). |
| MET-1a | Critical (part of MET-1) | Teardown `saveQueueStateSync` → `savePlayQueue` on ScrobbleService scope then `dispose()` cancels it; in-flight scrobbles cancelled; `CancellationException` swallowed. |
| MET-8 | Medium–High | No `CoroutineExceptionHandler` on ScrobbleService scope (contrast MediaService ADR-0122) — Room failure can crash process. |
| MET-9 | Medium | `CacheService` read-copy-`upsert`/`update(copy)` races with `ensureTrackRow` (cache finish ↔ nowPlaying). |
| CP-12 | Medium | `sourceType != null` clears Priority — journal coverage fix must split that gate. |
| CP-13 | Medium | Recency weighting effectively flat; single-entry journal ≤5 candidates. |
| CP-14 | Medium | `continuousPlayInFlight` drops STATE_ENDED `forceRetry` while a load is in flight. |
| CP-15 | Medium | ~~Offline fallback DAO helpers exist unused; ADR-0053 offline filter text stale (`cachedFilePath` only).~~ **Resolved (2026-11-03):** the cached helper queries are live in the CP-6 fallback ladder (`MediaService.kt`) and Library; ADR-0053 + behavior report already state `cachedFilePath` is required and `isDownloaded` alone is not playable (ADR-0095). |
| CP-16 | Low | ~~Dead string `player_autoplay_unavailable_cast`; ADR-0053 missing supersede note for 0093/0094.~~ **Resolved (2026-11-03):** string removed (no source reference); ADR-0053's 2026-11-02 amendment already records 0093/0094/0095 as the current behavior after the original out-of-scope note. |

Optional / low-confidence (do not elevate without more evidence):

- `populate*` may overwrite `duration_seconds` without COALESCE — metrics-adjacent.
- Subsonic `getSimilarSongs2` `id` may be artist-id per some server docs; verify against Navidrome before treating as a bug.
- Favorites DISLIKED mode journaling without CP-2 filters would poison autoplay (implement CP-1 with CP-2).

### 10.5 §7 design sketches — illustrative only

Do **not** treat §7 as compilable implementation specs.

- **§7.1:** `target` includes supplemental size but the sketched loop only consumes `songs` — size invariant fails when fill needs supplemental. Artist-key normalization unspecified (leaves DM-9 open).
- **§7.2:** `lastScrobbledTrackId` lives on `MediaService`, not `ScrobbleService`. Outbox row must insert **with** the local listen then mark sent (not only on network failure). Gate with `LocalOnlyPolicy`, not a vague `if (!offline)`.
- **§7.3:** Sketch APIs mostly missing (`recentTrackIds`, `dislikedTrackIds`). Use `getDislikedIdsByIds`; sample then hydrate (`getTracksByIds`, chunked). Keep `JournalTrackSelector` pure with precomputed signals. Sketch omits `localOnly` / CP-6 fallback. Daily-seeded sampling can lock burst order.
- **§7.4:** Must integrate with `DailyMixGenerationCoordinator` / `shouldAttemptToday` — do not bypass or double-apply (reaffirm §9.4 item 5).

### 10.6 Revised prioritized plan pointer

Replace §6 P0 ordering for scheduling:

```
P0: MET-1 (+ MET-1a, MET-8) → DM-1 (artist-cap + adjacency + null-key) → CP-2
P1: DM-5 (+ DM-13) → MET-2 → MET-3 (+ MET-9) → DM-3 → CP-3/5/6 (quality) → CP-1 (+ CP-12)
P2: docs/tests (CP-8, DM-7) → CP-7 → MET-5 enhancement → MET-6 informational → DM-6 → CP-9
```

DM-2 remains a Medium tuning/policy item, not ahead of MET-1 / DM-1 / CP-2.

### 10.7 Residual risks (amended)

1. Named single-artist + fill off still yields a one-track mix; favorite-artist recipes with &lt;~10 artists under-fill after severity downgrade.
2. Untagged libraries stop at the per-artist cap (shared `"unknown"` bucket) without collapsing to one.
3. Below 30 fresh (prior-mix-relative) the anti-repeat filter still flips off in one step.
4. After in-process service restart, local scrobbles/`listen_events`/`savePlayQueue` stay dead until process death; destroy-cleanup tests mock `scrobbleService` relaxed and cannot catch MET-1.
5. Overlapping `nowPlaying` / `scrobble` / `CacheService` / densify REPLACE can still clobber weights or cache flags.
6. Tap-only / favorites / downloads still never journal; offline CP depends on an earlier Play/Shuffle of another source.
7. Dislikes can still be autoplayed (CP-2) — independent of challenged severities.
8. Similar-songs drift is explore-mode per ADR-0094 unless product chooses “continue context.”
9. §7.4 / Home freshness must reconcile the coordinator; content staleness (orphan mix track IDs) remains out of DM-5’s listen-based fix.

### 10.8 Outcome of this amendment

- Unamended §1–§9 remain the historical review artifact.
- **Amended severities, corrections, discoveries, and P0 order in this section are authoritative** for follow-up implementation.
- Cross-model NOT OK on the original draft is closed by encoding the accepted challenges here.
- Still no production code changed.

---

## 11. Implementation log — P0 and low-risk P1 (2026-11-02)

Scope: the authoritative P0 set from §10.6 plus the low-risk P1 starts
(DM-5/DM-13, MET-2, MET-3). This section records what changed so the remaining
P1/P2 work can be scheduled without re-reading the diff.

### 11.1 P0

| ID | Change |
|---|---|
| MET-1 / MET-1a | Removed `scrobbleService.dispose()` from `MediaService.performDestroyCleanup()`. `ScrobbleService` is a process-scoped `@Singleton`; a service teardown no longer cancels in-flight scrobbles or the queued `savePlayQueue` write. |
| MET-8 | Added a `CoroutineExceptionHandler` to the scrobble scope; `CancellationException` is rethrown in `nowPlaying`, `scrobble`, `savePlayQueue`, and `fetchSimilarSongs` so structured cancellation still works. |
| DM-1 / DM-9 / DM-12 | `DailyMixGenerator`: normalized null/blank artists to a sentinel key (distinct from the literal name `"unknown"`), replaced the `target/10` hard cap with a proportional **soft** cap, made adjacency soft when no alternative exists, and added a hard-fill fallback so a small alternative pool cannot strand the mix below target. Phase-3 supplemental uses the same rules with its own artist groups. |
| CP-2 | `ContinuousPlayLoader.resolve()` accepts track/album/artist dislike exclusion sets. `MediaService` applies the track ledger (`getDislikedIdsByIds`) plus album/artist ledgers to both the journal path and the similar-songs API path (the latter falls back to Room for payloads that omit `albumId`/`artistId`). A shared `ContinuousPlayLoader.isExcluded` keeps both paths consistent. |

### 11.2 P1

| ID | Change |
|---|---|
| DM-5 / DM-13 | Added `GenreMixDao.getLatestDailyMix`/`getDailyMixTrackCount`, `ListenEventDao.countDistinctTracksForMix`/`getListenedTrackIdsForMix`, and `deleteDailyMixesBefore`. `generateForMix` now evaluates the 48h / 24h+10% predicate against the **latest persisted row** and counts only honest listens after that row's `createdAt` (lifetime `play_count` no longer triggers regeneration). Anti-repetition and the yesterday weight penalty survive a missed generation day by falling back to that latest list. `DailyMixRepository.anyMixNeedsRegeneration` mirrors the same predicate; `LibraryViewModel.loadDailyMixes` calls it even when every card is present, still behind `DailyMixGenerationCoordinator.tryBegin()`/`shouldAttemptToday`. |
| MET-2 | `nowPlaying` and `scrobble` write the metadata row, play count, and `listen_events` **before** the best-effort server report, so a slow/failed API call cannot delay or lose the durable local record. |
| MET-3 | Replaced the ScrobbleService read-copy-REPLACE with `TrackDao.ensureTrackRow` + targeted `updateTrackMetadata` (`COALESCE` patch). The patch never writes `play_count`, `last_played_at`, `starred_at`, `is_disliked`, or cache columns. |

### 11.3 Verification

Targeted Gradle suite (all passing):

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lucasdss.ftpmusic.app.playback.DailyMixGeneratorTest" \
  --tests "com.lucasdss.ftpmusic.app.data.repository.DailyMixRepositoryTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.ContinuousPlayTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.ContinuousPlayLoaderTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.JournalTrackSelectorTest" \
  --tests "com.lucasdss.ftpmusic.app.data.repository.ScrobbleServiceTest" \
  --tests "com.lucasdss.ftpmusic.app.ui.library.LibraryViewModelTest" \
  --tests "com.lucasdss.ftpmusic.app.playback.MediaService*"
```

**BUILD SUCCESSFUL**. `ktlintMainSourceSetCheck` / `ktlintTestSourceSetCheck`
report no violations in the changed files (only pre-existing, unrelated
violations elsewhere).

Cross-model review: a DeepSeek-style audit first returned **NOT OK** (empty-pool
history resurrection, Home freshness divergence, CP-2 payload-id drift, weak
DM-13 test). Those were fixed and re-audited to **OK**; a Mistral-style
challenge accepted the fixes and confirmed the remaining items as documented
trade-offs.

### 11.4 Remaining work

- **MET-9** (paired with MET-3): `CacheService` still performs read-copy
  `trackDao.update`/`upsert` for cache flags. Add targeted cache-column updates
  so a cache finish cannot clobber a concurrent `incrementPlayCount`.
- **DM-3, CP-3/5/6, CP-1 (+CP-12)** remain the rest of P1.
- **DM-5 follow-ups:** the freshness check is N+1 across mixes (cheap at personal
  scale per §7.4, but batch it if Home-open latency regresses); Home still
  shares the repository's generation mutex with the sync screen, which can show
  the generation spinner while waiting.
- **Residual risks** in §10.7 still apply. In particular, MET-1 has no meaningful
  unit seam (§10.7 item 4); its fix is verified by code inspection and the
  process-scoped `@Singleton` wiring, not by a destroy-cleanup test.

### 11.5 Iteration log — MET-9, DM-3, DM-7 hardening (2026-11-02)

| ID | Change |
|---|---|
| MET-9 | Added targeted `TrackDao` cache-column writes (`markCachedAsDownload`, `markCachedAsAuto` with a download-preserving `CASE`, `clearCacheState`, `clearAutoCacheState`, `clearDownloadState`, guarded `promoteAutoCacheToDownload`). `CacheService` no longer read-copies or `upsert`s whole `TrackEntity` rows on cache writes; promotion that loses a race clears the SimpleCache download marker and unpins. |
| DM-3 | Weight map now covers primary + supplemental; supplemental is deduped against primary; Phase 3 uses a fresh-only soft pass with album-aware selection; hard-fill prefers fresh, different artist/album, then familiar. `albumKey` is `albumId` or a per-single key. |
| DM-7 | Added DAO tests for MET-9 column preservation and guarded promotion; generator tests for exact supplemental size, full weight map, fresh-before-familiar ordering, album adjacency, duplicate IDs, and seeded determinism. Updated cache tests to assert the targeted contract. |

Verification: full `:app:testDebugUnitTest` green; `ktlintTestSourceSetCheck`
green; `ktlintMainSourceSetCheck` reports only the pre-existing unrelated
violations in `PlaySuppressionWatch.kt`, `LibraryScreen.kt`, and
`LibraryViewModel.kt`. Cross-model: DeepSeek audit → Mistral challenge →
fixes → DeepSeek re-audit OK → Mistral challenge OK.

### 11.6 Iteration log — CP-1 + CP-12 journal coverage (2026-11-02)

| ID | Change |
|---|---|
| CP-1 | `PlaybackManager` now accepts a journal-only `JournalSource(type, id, name)` on `playAlbum`, `shuffleAlbum`, and `playSingleTrack`. Track taps pass a full-context source for album/artist/playlist/mix/favorites/downloads and a single-track source for search/profile. Favorites journals only LIKED mode; DISLIKED/DESIRED stay silent. Removed the dead `AlbumDetailViewModel.playAlbumWithoutJournal`. |
| CP-12 | `clearPriority()` remains gated on `sourceType != null`; `journalSource` never clears Priority. Legacy Play All/Shuffle call sites still pass `sourceType` and keep their overwrite semantics. Journaling falls back to `sourceType`/`sourceId` when no explicit journal source is supplied. |

Verification: full `:app:testDebugUnitTest` green; `ktlintTestSourceSetCheck`
green; `ktlintMainSourceSetCheck` reports only the same pre-existing unrelated
violations. Added regression tests for journal-source Priority preservation,
legacy `sourceType` clearing, single-track search journaling, repeated search
taps sharing one key, and `PlaybackViewModel` forwarding a non-null journal
source. Cross-model: Mistral design challenge → implementation → DeepSeek
audit OK → Mistral final challenge OK.

Known non-blocking gap: `tryStartContext`/`PendingPlayback` do not carry
`journalSource`. No tap path uses them today; any future tap that needs
overwrite protection must extend that path before relying on journaling.

### 11.7 Iteration log — CP-3 + CP-9 + CP-6 selector and fallback (2026-11-02)

| ID | Change |
|---|---|
| CP-3 | `JournalTrackSelector` rewritten from random recency weighting into a scored selector. `parse(entries, entryCap = playbackManager.journalCap, poolCap = 800)` reads the full configured journal cap (10–500), de-duplicates ids, bounds Room hydration, and reports `truncated` when the pool cap cuts ids. `select(parsed, hydrated, Signals, maxTracks, Random)` scores entry recency (5× newest → 1× oldest), taste (`play_count` capped at 40, `user_rating`, `starred_at`), current artist/album/genre affinity, and a recent-play penalty (≤2 days 0.35, ≤7 days 0.65), with hard filters for the excluded queue and track/album/artist dislike ledgers. Sampling is weighted without replacement; artist/album repeats are soft down-weights (0.55/0.75), so a small pool still fills `min(maxTracks, eligible)`. |
| CP-9 | Candidate scoring now reads `QueueJournalEntity.position`. When the current track appears in a journal entry, that entry uses the current index: after → 2.0, before → 0.4. Otherwise the saved start index is used: after → 1.4, before → 0.75. This makes “continue this album from track 40” observable without a schema change. |
| CP-6 | Added pure `ContinuousPlayFallback` (tier order `SAME_GENRE → SAME_ARTIST → MOST_PLAYED → RANDOM`, skipped when context is absent) plus `eligibleIds`, which applies `isDisliked`, offline cache-path, and album/artist dislike filters **before** slicing. `MediaService` removed the empty-journal early return, hydrates journal ids in 500-id chunks, and after the similar API + journal both miss runs a bounded ladder that accumulates up to 10 tracks across tiers, excluding the whole active queue, the playing `mediaId`, and previously picked ids. The ladder runs both online (new uncached queries) and offline (`localOnly` means the cached variants and loader re-check). |

DAO additions are query-only (no schema change, no migration): bounded
`ORDER BY RANDOM()` genre/artist reads for online and cached-only modes, plus
`getMostPlayed` / `getRandomTracks` online counterparts of existing cached
queries. Randomized tiers avoid replaying the same alphabetically-first rows on
every burst.

**Whole-queue exclusion trade-off (CP-3).** The journal excludes the entire
active queue rather than only the played prefix. Continuous Play appends to the
context queue, so prefix-only exclusion would re-append tracks that are still
queued (upcoming) or already played, depending on which side of the cursor is
excluded. The review wording is internally inconsistent on this point, and a
correct prefix rule needs a `currentJournalKey`/cursor that does not exist.
Consequently a fully-excluded pool now logs `journal-excluded` and falls through
to the CP-6 ladder, which uses the current track's genre/artist context. A
cursor-based prefix policy is deferred rather than guessed. Mistral and DeepSeek
both reviewed this and agreed it is defensible and non-blocking.

**Known residual risk.** The fallback ladder adds up to four bounded DAO reads
inside `continuousPlayInFlight`, which lengthens the in-flight window CP-14
watches. The gate itself was not changed in this iteration.

Verification: full `:app:testDebugUnitTest` green; `ktlintTestSourceSetCheck`
green; `ktlintMainSourceSetCheck` reports only the same pre-existing unrelated
violations in `PlaySuppressionWatch.kt`, `LibraryScreen.kt`, and
`LibraryViewModel.kt`. New tests cover current-index and saved-position
continuity, rating/play-count bias, recent-play penalty, dislike ledgers,
>5 unique tracks from a 100-track entry, entry/pool caps and truncation,
soft-diversity underfill, seeded determinism, fallback tier/filter order,
offline vs online eligibility, and real in-memory Room execution of every new
fallback query. Cross-model: DeepSeek audit returned NOT OK (deterministic
genre/artist sampling, current-track replay, pre-filter slot waste) → all fixed
→ Mistral challenge accepted the whole-queue trade-off → DeepSeek re-audit
returned reasoned OK with no blocking issue.

### 11.8 Iteration log — CP-5 + CP-14 prefetch and in-flight retry (2026-11-02)

| ID | Change |
|---|---|
| CP-5 | `ContinuousPlayGate.shouldLoadContinuation` now fires at a 3-track low-water mark (`LOW_WATER_MARK`) instead of only on the last item. `remaining = mediaItemCount - 1 - currentIndex`; the gate fails closed for negative or out-of-range indexes (Cast Dual `-1`) and accepts an optional `lowWaterMark` for tests. `hasLoadedContinuation` remains the once-per-stint latch, so a successful 10-track burst does not re-fire until the tail drains to the low-water mark again. |
| CP-14 | Added pure `ContinuousPlayRetry` (`tryBegin(forceRetry)`, `finish(appended, canRetry)`) and replaced the `continuousPlayInFlight` boolean. A `STATE_ENDED` retry that arrives while a load is running is now remembered and replayed exactly once after the in-flight load finishes, provided it appended nothing and the coroutine was not cancelled. The deferred retry re-resolves `PlayerHolder.player` and suppresses itself when `lastTrackId != mediaId` (the user skipped during the load), so it cannot seed context from a track that is no longer current. Cancellation is rethrown instead of being swallowed, and `finish` uses `Job.isCancelled` (not `isActive`, which is also false after normal completion). |

Decisions and rejected alternatives:

- **Low-water = 3** (review allows 2–3). With a 10-track burst this fires once
  per tail cycle; the 3-track lead is one track earlier than a 2-track mark and
  costs no extra bursts. DeepSeek argued for 2, Mistral for 3; 3 is chosen for
  slow-network margin.
- **No standing 5–10 buffer counter.** The append-growth model already keeps a
  ~10-track burst ahead; a separate buffer ledger would need cursor state and
  would duplicate the gate.
- **No tiny-pool/no-progress cooldown.** A 1–2 track burst on a tiny queue is
  the intended infinite tail; a failing pool exhausts the whole exclusion set
  and logs `continuous-play exhausted` on that transition. A cooldown could
  silence CP for new/small libraries.
- **No `play()`/`prepare()` after an ENDED append.** Media3's `play()` at
  `STATE_ENDED` seeks within the current item; blindly calling it can replay
  the last track. Appending the tail is the existing resume mechanism.
- **No buffering UI.** The CP-6 ladder already gives a local last resort; a
  buffering state is deferred as scope creep.

Verification: targeted `ContinuousPlayGateTest`, `ContinuousPlayRetryTest`, and
`ContinuousPlayTest` green; full `:app:testDebugUnitTest` green;
`ktlintTestSourceSetCheck` green; `ktlintMainSourceSetCheck` reports only the
same pre-existing unrelated violations in `PlaySuppressionWatch.kt`,
`LibraryScreen.kt`, and `LibraryViewModel.kt`. New tests cover remaining = 2/3/4,
one-item queues, `lowWaterMark = 0` legacy semantics, negative/index-at-count
failure, disabled inside the low-water mark, and the `ContinuousPlayRetry`
interleavings (deferred once, append suppresses, cancellation suppresses —
including a real cancelled coroutine, ENDED after clear starts immediately, no
loop). There is no MediaService coroutine-level integration test: the
`tryBegin`/`finish` wiring and the three `appended = true` sites are covered by
the pure holder tests plus compile-time checks, not by a service harness.
ADR-0053 Decision #1 now records the low-water prefetch, in-flight retry, and
the corrected offline filter. Cross-model: DeepSeek plan → Mistral challenge
(3 vs 2 low-water, cancellation, ENDED resume) → DeepSeek reconciliation →
implementation → Phase-5 audits. DeepSeek audit returned reasoned OK
with non-blocking test/doc notes (count=1 and real-cancellation tests added;
service-level wiring documented as untested); Mistral challenged back-to-back
refiring and a stale-`mediaId` retry, the stale-track retry was fixed with the
`lastTrackId` guard, and Mistral withdrew the refiring finding after the call
sites showed a cooldown would suppress CP-14's intended ENDED retry. Both
returned reasoned OK.

### 11.9 Iteration log — CP-8 + DM-6 + MET-5/6 docs and tests (2026-11-02)

| ID | Change |
|---|---|
| CP-8 | Rewrote `docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md` for the current flow: 3-track low-water prefetch, `STATE_ENDED` last chance, one burst per load (similar ≤20 online **or** journal ≤10 **or** fallback ≤10), CP-3 whole-queue exclusion, CP-2 dislike ledgers, offline `cachedFilePath` eligibility, CP-14 retry/stale-track suppression, Cast support, actual journal call sites, and current caps (journal rows are `journalCap`-bounded by eviction; the 800-id pool cap bounds parsed candidates after the read). Appended an ADR-0053 amendment referencing ADR-0093/0094/0095; Decision #5's "out of scope" list is now historical, not current behavior. |
| DM-6 | Documented `DailyMixGenerator` weight as a **deterministic priority rank**, not a probability, and corrected the stale "max 4 tracks per artist" note to the dynamic `maxTracksPerArtist` soft cap. Renamed the misleading tests to presence/representation checks, removed the "5x" and "weighted distribution" claims, and added a characterization test proving that within one artist the five weight-5 tracks are selected before the five weight-1 tracks (unique ids, target 10). |
| MET-5 | Closed as an **accepted trade-off** (ADR-0046 thresholded listens by design). No `listen_events` schema change, no migration; skip/context learning remains a future enhancement. |
| MET-6 | Closed as **informational**. The 52→53 recursive CTE only runs for databases ≤52; the app schema is v68. `MIGRATION_52_53` was not edited. Backfill rows are synthetic and excluded from honest minutes. |

Decisions and rejected alternatives:

- **No weighted sampling.** DM-6's alternative (Efraimidis–Spirakis sampling)
  would change mix behavior and need statistical tests; the review explicitly
  permits documenting deterministic ranking instead. A statistical test would
  encode a model the code does not implement.
- **No `listen_events` expansion.** MET-5 is a schema/product decision, not a
  bug fix; adding nullable columns and a retention policy belongs in its own
  migration iteration.
- **No migration cap edit.** MET-6 is unreachable for current installs; editing
  an already-shipped migration would add risk without user benefit.
- **Documented caveats rather than blessing bugs:** legacy
  `sourceType`+`sourceId` still clears Priority (tap paths use `journalSource`);
  the 800-id pool cap can truncate with `poolTruncated` diagnostics; runtime
  `journalCap` clamps 10..500 while the Settings slider exposes 100..500.

Verification: `DailyMixGeneratorTest` green; full `:app:testDebugUnitTest`
green; `ktlintTestSourceSetCheck` green; `ktlintMainSourceSetCheck` reports only
the same pre-existing unrelated violations in `PlaySuppressionWatch.kt`,
`LibraryScreen.kt`, and `LibraryViewModel.kt`. Cross-model: DeepSeek plan →
Mistral challenge (several file/line citations were fabricated and rejected;
valid coverage gaps for CP-2/3/5/6/14 and Cast were folded in) → DeepSeek
reconciliation returned OK. Phase-5: DeepSeek audit returned reasoned OK after
a journal-cap wording correction (`getAllRecent()` is row-bounded by
`journalCap` eviction, not by the 800-id pool cap); Mistral challenged the
DM-6 mixed-tier assertion, then withdrew after the probability-vs-contract
clarification and returned reasoned OK. Remaining P2 work: **CP-7**
(autoplay-tail cap, prune, restore bound, Cast-aware removal), then CP-15/CP-16
documentation cleanup if still in scope.

### 11.10 Iteration log — CP-7 autoplay-tail cap (2026-11-03)

| ID | Change |
|---|---|
| CP-7 | Added `CONTINUOUS_PLAY_AUTOPLAY_CAP = 50` and a pure `autoplayEvictionIndices` helper. `appendToContext(asAutoplay=true)` now evicts the oldest autoplay rows **before** appending (never the playing row, matched by `queueEntryId`, then merged index for legacy rows, then `mediaId`), so the merged queue and the subsequent `persistenceSave` cannot exceed the cap. Non-autoplay CONTEXT/PRIORITY/LATE rows are untouched. `restoreQueue` applies the same bound to legacy Room snapshots, protects/remaps the playing row by entry id, and rewrites the trimmed queue with `src=restoreTrim`. While casting, an append that evicts emits one batched `CastQueueAction.RemoveAll` (one `queueRemoveItems`) **before** the `AddAll`; the RemoveAll carries the pre-burst snapshot revision, so a failed prune rolls back locally instead of silently leaving evicted rows ahead of the playhead. A failed `AddAll` can leave the receiver a subset of the phone queue, repaired by the next full queue load (not the prefix watchdog). `clearAutoplayQueue()` is unchanged and may still remove the playing autoplay row (explicit user action). |

Decisions and rejected alternatives:

- **Cap = total autoplay rows, 50.** Bursts are 10–20, so 50 is 2–3 bursts. Capping only upcoming rows would leave arbitrary played history in Room, which is the persistence/restore problem CP-7 names; Queue UI only projects upcoming rows anyway. A new queue context replaces the tail naturally.
- **Protect the playing row, not the whole burst.** Eviction is oldest-first and skips the current row (entry id first, media id fallback). This keeps jump-back playback safe without pinning stale history.
- **Prune before append.** Pruning after `addAllToContext` also works because old autoplay rows are CONTEXT and sit before the context/LATE splice point, but prune-first avoids relying on that invariant and mirrors `clearAutoplayQueue`.
- **No ClearAndPlay per burst.** Re-seating the receiver on every overflow would reset preload/gapless state. RemoveAll (when needed) goes first so it owns the pre-burst snapshot revision; a failed prune rolls back locally, while a failed AddAll leaves at worst a receiver subset that the next full queue load repairs. The batched remove targets the jump-back case where evicted rows still sit ahead of the playhead on the receiver.
- **No schema/migration change.** `queue_items.is_autoplay` already exists; the cap is enforced in `PlaybackManager` (the only writer of autoplay rows) rather than in `QueuePersistenceManager.save`, which would desync in-memory Dual/index flags.
- **No mutable cap setting.** 50 is a constant; Settings keeps the journal cap only. The helper takes `cap` so tests can exercise smaller bounds.

Verification: new `AutoplayTailCapTest` (helper edges incl. duplicate-mediaId index precedence, 5×10 bursts, oldest-burst eviction, protected jump-back row, non-autoplay/priority intact, Cast RemoveAll→AddAll, below-cap AddAll only, local player removals), `RestoreQueueTest` legacy 65-row trim + index remap + `restoreTrim` save, `CastQueueCommandExecutorTest` RemoveAll batching/filtering, and `MediaServiceCastQueueTest` one batched `queueRemoveItems` (or none when ids are absent). Targeted queue/Cast suites green; full `:app:testDebugUnitTest` green; `ktlintTestSourceSetCheck` green; `ktlintMainSourceSetCheck` reports only the same pre-existing unrelated violations in `PlaySuppressionWatch.kt`, `LibraryScreen.kt`, and `LibraryViewModel.kt`. Cross-model: DeepSeek plan → Mistral challenge raised ordering, two-action Cast atomicity, and deferred restore persistence → DeepSeek rebutted the index-shift claim, agreed on prune-first and immediate restore persistence, and rejected ClearAndPlay-per-burst as a receiver re-seat regression → revised plan (prune-first, RemoveAll-then-AddAll, restore-then-trim + `restoreTrim` save, constant cap) → DeepSeek agreed on the final failure-mode analysis → Mistral final challenge returned reasoned OK. Phase-5 DeepSeek audit then flagged the AddAll-first revision-0 hole and the legacy mediaId-twin risk; both were fixed (RemoveAll first; merged-index protection precedence). Phase-5 Mistral challenge recorded below.

### 11.11 Iteration log — CP-15 / CP-16 closure (2026-11-03)

| ID | Outcome |
|---|---|
| CP-15 | **Closed as already-fixed.** The CP-6 fallback ladder now calls `getRandomCachedTracksByGenre`, `getRandomTracksByGenre`, `getRandomCachedTracksByArtist`, and `getRandomCachedTracks` (`MediaService.kt`); `getRandomCachedTracks` also serves Library. No unused-helper defect remains. Offline playability is documented consistently: `ContinuousPlayLoader.isPlayableOffline` and ADR-0053 both require a non-blank `cachedFilePath` (`isDownloaded` alone is insufficient, ADR-0095). No code change. |
| CP-16 | **Closed.** Removed the unreferenced `player_autoplay_unavailable_cast` string from `values/strings_player.xml` (verified no Kotlin/XML/SourceSet reference). ADR-0053's 2026-11-02 amendment already supersedes the original out-of-scope wording for ADR-0093/0094/0095, so no further ADR note is needed. |

Verification: `:app:processDebugResources` green after string removal; full `:app:testDebugUnitTest` green; `ktlintTestSourceSetCheck` green; `ktlintMainSourceSetCheck` still reports only the pre-existing unrelated violations in `PlaySuppressionWatch.kt`, `LibraryScreen.kt`, and `LibraryViewModel.kt`. No behavior, schema, or persistence change in this iteration. Cross-model: DeepSeek closure audit → Mistral challenge, both reasoned OK.
