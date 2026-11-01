# FTPMusic Diagnostics — 2026-10-31 Sync + Playback Behavior Report

Date: 2026-10-31 (review 2026-11-01; amended after code re-validation 2026-11-01)
Status: **Analysis only. No code changed in this pass.** Amended §10 after
re-validating cited call sites against current `compose/` sources.
Source: user-supplied `Settings → Share diagnostics` export.
Build: `2.0.0 (26)`, `sdk=37`, Pixel 8 Pro.
Related: ADR-0022, ADR-0045, ADR-0048, ADR-0060, ADR-0061, ADR-0067, ADR-0071,
ADR-0079, ADR-0108, ADR-0109, ADR-0112, ADR-0119, ADR-0139, ADR-0140;
`LIBRARY_SYNC_BEHAVIOR_REPORT.md`, `COLLECTION_PLAY_FROM_TRACK_BEHAVIOR_REPORT.md`,
`HOME_RECENTLY_ADDED_BEHAVIOR_REPORT.md`.

This report covers the full reasoning chain behind each finding: what the export proves,
what it merely suggests, which hypotheses were tested against the code, which were
discarded, and what the minimal correct fix is.

---

## 0. Verdict

| # | Sev | Finding | Confidence | User-visible impact |
|---|---|---|---|---|
| F1 | **P0** | WorkManager periodic sync has **never run** (`lastWmAttemptMs=0`). `@HiltWorker` classes are scheduled but no `HiltWorkerFactory` is installed, so `DefaultWorkerFactory` cannot instantiate them. Automatic DELTA/FULL sync and metadata enrichment are dead. | High (code) / Medium-High (device) | Library stops discovering new music; weekly FULL heal never runs; search enrichment never runs; WM-cadence update probe is dead (cold-start probe still live — §10.3). Data rots silently between manual Resyncs. |
| F2 | P2 | Every queue start warms the download cache **twice** (explicit `enqueuePlayQueue` + transition callback). 13 tracks → 23 enqueue calls in this log. | High | ~2× Room reads/writes per queue start; log noise hides real download issues. No data corruption. |
| F3 | P3 | `saveQueueState` persists the queue from **two independent call sites** on `Dispatchers.IO`; writes can commit out of order. | High (race exists) / Low (observed harm) | Rare stale index/position on restore. |
| F4 | P2 | Library counts disagree (`corpus=20534`, `tracks=20325`, `songSum=20453`) because 3 different sources are printed without labels, and the reconciler is WM-gated (dead). | High | Misleading diagnostics; ghost rows accumulate while F1 persists. |
| F5 | P3 | `homeRecentFetchedMs` cannot distinguish a successful refresh from a timeout/empty backoff stamp (ADR-0112 writes the same field). | High | Home “Recently Added” can look fresh while serving stale ids; debugging blind spot. |
| F6 | P3 | Diagnostics ergonomics: route logs the pattern (`{anchor}`), `lastWmAttemptMs=0` is ambiguous, ring window is 68 s, count/mode line is appended after the body. | High | Slower diagnosis; looks like a bug in the log itself. |

**Not bugs** (documented behavior — do not “fix”): search tap replacing the queue (ADR-0061),
`reason=3` transitions, `cached_skip_upgrade`, empty `btResumeEvents`.

**Open question** (not a finding): the 21-track / `pos=90934` save at 18:52:56 with no
transition or seek event. Needs instrumentation; hypotheses in §5.

---

## 1. Export anatomy — what this artifact can and cannot prove

### 1.1 Format

`DiagnosticLog.snapshot()` (`data/diagnostics/DiagnosticLog.kt:49-92`) emits:

1. a header built from `SharedPreferences` + live counters,
2. `btResumeEvents=…` (ADR-0119 ring; ADR-0071 is the broader BT autoplay decision),
3. `---`,
4. the in-process ring buffer (CAP=1000, `DiagnosticLog.kt:16-31`),
5. trailing `mode=… skip=… corpus=… songSum=…` appended by
   `SettingsViewModel.diagnosticsSnapshot()` (`ui/settings/SettingsViewModel.kt:925-950`).

Note the trailing fields are appended **after** the ring body because of the string
concatenation at `:948-949`; they are not part of the header. This is why
`mode=DELTA skip= corpus=… songSum=…` appears as the last line, below the log body.
Cosmetic, but it reads like log corruption.

### 1.2 Time budget

- First body line: `2026-10-31T18:52:06.362349Z | route=home`.
- `share diagnostics lines=54` is logged immediately before the snapshot is built.
- The exported body has exactly 54 pre-existing lines plus that one share line.
- `DiagnosticLog` is **in-process**: the ring dies with the process.

Therefore the process started at ≈`18:52:04Z` (Home composition 2.4 s later, after
`MetadataSyncWorker.start(initialDelayMs=2000)`), and the body covers **68 seconds**
(`18:52:06.362 → 18:53:14.399`). No line in the body can describe anything that happened
on 10-25/10-30. All cross-session reasoning must come from the header prefs.

Consequence: “no W/E lines” is not evidence of health; it only means nothing failed during
those 68 seconds.

### 1.3 Timestamp math

`capturedAt = 2026-10-31T18:53:14.401Z`.

| Pref | Value | UTC | Age at capture |
|---|---|---|---|
| `lastFullSyncMs` | 1792920816480 | 2026-10-25T09:33:36Z | **6 d 09:19:38** |
| `lastDeltaSyncMs` | 1793371011683 | 2026-10-30T14:36:51Z | **1 d 04:16:23** |
| `homeRecentFetchedMs` | 1793472567648 | 2026-10-31T18:49:27Z | **3 m 47 s** |

`syncIntervalHours=4` → by capture time **7 delta windows were missed**
(10-30 18:36, 22:36, 10-31 02:36, 06:36, 10:36, 14:36, 18:36).

The weekly FULL is due at `lastFullSyncMs + 7 d = 2026-11-01T09:33:36Z`, i.e. **14 h 40 m
after this capture**. At capture it was not yet overdue — but with F1 unfixed it will be
missed, and ADR-0108 corpus reconcile runs only on FULL. Precise wording matters: the log
shows the DELTA cadence is dead today; the FULL heal is the next casualty.

---

## 2. Timeline reconstruction (68 s)

| Time (UTC) | Event | Reading |
|---|---|---|
| 18:52:06.362 | `route=home` | UI composed; process ≈2 s old. |
| 18:52:06.373 | `transition id=3Gbe… reason=3 idx=2 count=16`; `saveQueueState tracks=16 contextSize=16 idx=2 pos=69036` | Queue restore **worked**: 16-track context, index 2, 69.0 s position. `reason=3` = `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED` (media3 1.10.1), i.e. the queue was installed, not auto-advanced. |
| 18:52:06.383–.418 | 23 `enqueue` lines for 13 distinct upcoming track ids, all `result=cached_skip_upgrade`; 3×`priority=0`, 10×`priority=1` | Queue-remainder prefetch ran (see F2). All tracks already cached; no network work. |
| 18:52:09.220 | `route=search` | User navigates. |
| 18:52:18.440 → 18:52:51.222 | 7× `transition reason=3 idx=0 count=1 pos=0` (ids `2RY2…` ×6, `3Gbe…` ×1); `saveQueueState` after each; four pairs 10–17 ms apart | Each search tap runs `playSingleTrack` → single-item CONTEXT → new playlist → position 0. Per ADR-0061 this is intended. The pairs are transition + `onIsPlayingChanged(false)` saves (see F3). |
| 18:52:53.472 | `route=nowplaying` | User opens Now Playing. |
| 18:52:56.488 | `saveQueueState tracks=21 contextSize=21 idx=0 pos=90934` | Context grew 1 → 21 with **no transition log**. See §5. |
| 18:53:00.192 | `pos=129906` | +38.97 s over 3.7 s wall time → seek or position-source anomaly. |
| 18:53:03.362 | `pos=132845` | +2.94 s over 3.17 s → normal playback. |
| 18:53:08.538 / 09.511 / 13.036 | `route=search` → `settings` → `settings/about?anchor={anchor}` | Settings hub leaf; route logged as a template (F6). |
| 18:53:14.399 | `share diagnostics lines=54` | Export. |

Healthy signals in the window: queue restore with correct index/position; offline flags
consistent (`offline=false reachable=true`); all prefetched tracks already cached; no `W`/`E`
lines; home ids count 10 = `HOME_RECENT_LIMIT`.

---

## 3. F1 (P0) — Automatic metadata sync is dead

### 3.1 Evidence

1. `lastWmAttemptMs=0`, `lastWmSkipReason=-`.
2. `recordWmAttempt()` is called **only** from `SyncScheduleWorker.doWork()`
   (`data/db/SyncScheduleWorker.kt:37,43,49,64,82`) and is invoked *before* every branch:
   - unreachable → `recordWmAttempt("unreachable")` then `Result.retry()`
   - start → `recordWmAttempt("")`
   - skipped (already_running/cooldown/offline/cellular) → `recordWmAttempt(reason)`
   - error/exception → `recordWmAttempt("error"|"exception")`

   There is no path through `doWork` that leaves the pref at `0`, on a device whose
   `ftpmusic_sync` prefs already contain `last_full_sync_ms` and `last_delta_sync_ms`
   (proving the file is populated and not restored-empty).
3. `offline=false reachable=true` rules out the two “deferred” reasons at capture time —
   and they would have stamped the pref anyway.
4. `MetadataSyncWorker.start()` (`MetadataSyncWorker.kt:254-266`) is the only in-process
   startup sync, and it **only** runs when `metadataDao.albumCount() == 0`:
   ```kotlin
   if (metadataDao.albumCount() == 0) {
       syncNow(mode = LibrarySyncMode.FULL)
   }
   ```
   With `albums=2353`, it does nothing.
5. All other `syncNowAsync` call sites are user actions or Home-id-change
   (`LibraryViewModel.kt:1599`, `SyncingScreen.kt:186,253`, `LibraryViewModel.kt:1224`
   Resync). Grep confirms there is no resume/boot/alarm auto-sync.
6. The only automatic scheduler is `NetworkPolicyHolder.rescheduleMetadataSync()`
   (`data/cache/NetworkPolicyHolder.kt:94-116`), called from
   `FtpmusicApp.schedulePeriodicMetadataSync()` (`FtpmusicApp.kt:139,161-170`) and on
   settings changes.

So the chain “automatic sync” is exactly: `WorkManager → SyncScheduleWorker.doWork →
MetadataSyncWorker.syncNowAsync`. That chain never fired.

### 3.2 Root cause

`SyncScheduleWorker` and `MetadataEnrichScheduleWorker` (file
`MetadataEnrichWorker.kt`) are `@HiltWorker` classes with `@AssistedInject`
constructors (`SyncScheduleWorker.kt:24`, `MetadataEnrichWorker.kt:172`).
`androidx.hilt:hilt-work` and `hilt-compiler` are on the classpath
(`app/build.gradle.kts:254-258`).

But:

- `FtpmusicApp` extends `Application()` only and does **not** implement
  `androidx.work.Configuration.Provider` (`FtpmusicApp.kt:20-30`).
- No `HiltWorkerFactory` is injected or exposed anywhere (repo grep: zero source hits).
- The merged manifest contains the stock `androidx.startup.InitializationProvider` entry
  for `androidx.work.WorkManagerInitializer` with no `androidx.work.Configuration`
  meta-data. WorkManager therefore initialises with `DefaultWorkerFactory`.

`DefaultWorkerFactory` instantiates workers reflectively via a `(Context, WorkerParameters)`
constructor. A `@HiltWorker` class does not have one — its `@AssistedInject` constructor takes
the assisted params plus the injected deps. Instantiation throws, WorkManager marks the run
failed, `doWork()` is never entered, and `recordWmAttempt()` never executes. Because
`enqueueUniquePeriodicWork(..., UPDATE, ...)` runs on every app start, the cycle repeats
forever: scheduled → fails to instantiate → rescheduled → fails.

Collateral damage (same factory):

- `MetadataEnrichRunner.enqueue()` (`MetadataEnrichWorker.kt:56-78`) → MusicBrainz /
  Last.fm / Navidrome aliases and tags never enrich (ADR-0079). Search stays cold.
- `AppUpdateProbe.probe()` after a successful periodic sync (ADR-0140) never runs →
  the WM-cadence update probe is dead. Cold-start probe in `FtpmusicApp.onCreate` still
  runs (see §10.3); the tray is degraded, not necessarily fully dead.
- Playlist sync is **not** affected (`PlaylistSyncWorker` is an in-process coroutine loop,
  `FtpmusicApp.kt:136`), which is why playlist edits still reach the server.

### 3.3 Why test coverage missed it

`SyncScheduleWorkerTest.kt:40` and `SyncScheduleWorkerBranchTest.kt:45` build the worker
manually:

```kotlin
worker = spyk(SyncScheduleWorker(context, workerParams, metadataSyncWorker, appUpdateProbe))
```

That bypasses `WorkerFactory` entirely. No test ever asks WorkManager to instantiate the
worker, and no test asserts `FtpmusicApp : Configuration.Provider`. This is a wiring gap,
not a logic gap, so branch coverage stayed green.

### 3.4 What would falsify F1 (must be checked on-device before closing)

| Observation | Meaning |
|---|---|
| WorkManager DB: `state=ENQUEUED`, `run_attempt_count=0`, `last_enqueue_time` old | Scheduler/OS never fired it (OEM throttling, battery restriction, job quota). Different bug, same symptom. |
| `state=FAILED`, `run_attempt_count>0`, logcat `WM-WorkerWrapper: Could not instantiate …` | Confirms F1. |
| `ftpmusic-work periodic sync …` lines in a real logcat | Worker ran; then the pref write was lost or read from a different file. Investigate prefs/backup. |
| logcat `ftpmusic-work: WorkManager not available` | `WorkManager.getInstance` threw during init; also ends in “never runs”. |

Run the commands in §6.

### 3.5 Impact

- New releases are discovered only when the user manually Resyncs or first-installs.
- Weekly FULL heal (ADR-0045 §3) never runs → deletions/renames never reconciled;
  ADR-0108 corpus reconcile never runs → ghosts accumulate (F4).
- Enrichment dead → weaker search / aliases (ADR-0079).
- Likely affects **all users on 2.0.0**, not just this device, because the wiring is
  build-wide. Treat as release-blocking.

---

## 4. Secondary findings

### 4.1 F2 (P2) — Queue prefetch runs twice per queue start

**Observation.** 16-track queue at `idx=2` → 13 upcoming tracks. The log shows 23 enqueue
calls for 13 ids:

| Id | Lines | Priority |
|---|---|---|
| `7Avg…`, `3tMF…`, `0KwA…` | 2 each | 0 |
| `5EhE…`, `5WwC…`, `0n79…` | 2 each | 1 |
| `78fV…`, `73Me…` | 1 each | 1 |
| `5Fe6…`, `73Os…`, `3PVb…`, `61Wl…` | 2 each | 1 |
| `1NOF…` | 1 | 1 |

**Why 23, not 26.** Two call sites start the same loop:

- `PlaybackManager.playAlbum/shuffleAlbum/restoreQueue` call `enqueuePlayQueue` explicitly
  (`PlaybackManager.kt:468, 540, 567`; body `:1104-1143`).
- `MediaService.onMediaItemTransition` calls it again after the queue is installed
  (`MediaService.kt:1008`).

Both loops run concurrently in separate coroutines. `DownloadManager.enqueue` is
check-then-act (`DownloadManager.kt:122-181`):

```kotlin
val existing = cacheQueueDao.getByTrackId(trackId)
if (existing.status == "completed" && !isDownload && cacheService.isPlayableCached(trackId)) {
    if (priority < existing.priority) { updatePriority(...); log("cached_skip_upgrade") }
    return
}
```

If pass A and pass B read the old priority before either updates it, both log; if B reads
after A’s update, `priority < existing.priority` is false and B logs nothing. That explains
10 doubled ids and 3 singles — the signature of a benign race, not a deterministic 2× loop.

The loop starts at `currentIndex + 1`, so the 3 p0 ids are the next three tracks and the
rest are priority 1 — exactly the ADR-0022 contract. Note the doc comment says “the ENTIRE
queue is enqueued”, which is inaccurate for indices ≤ current (see F7 below).

**Impact.** 26 (not 13) `getByTrackId` queries per queue start, duplicate `updatePriority`
writes, duplicated diagnostic noise. No correctness issue: `CacheQueueItemEntity` insert is
`INSERT OR IGNORE` on a unique `track_id` (`DownloadManager.kt:235-258` loser-upgrade path),
and completed/cached rows are skipped. The double lines are the early check-then-act race,
not duplicate rows (see §10.3).

**Fix (pick one):**

1. Keep the transition path (it is the universal hook for Auto/MediaSession/external queue
   starts) and remove the explicit calls in `playAlbum`/`shuffleAlbum`/`restoreQueue`.
   Risk: if a queue install produces no item transition (identical current item / Cast
   setup), prefetch is skipped until the next transition.
2. Keep both but add an owner token — cheap and safe:
   ```kotlin
   @Volatile private var lastWarmKey: Int = 0
   @Volatile private var lastWarmAtMs: Long = 0L

   fun enqueuePlayQueue(tracks: List<Track>, urls: List<String>, currentIndex: Int, src: String) {
       val key = 31 * tracks.hashCode() + currentIndex
       val now = android.os.SystemClock.elapsedRealtime()
       synchronized(this) {
           if (key == lastWarmKey && now - lastWarmAtMs < 2_000L) {
               DiagnosticLog.d("ftpmusic-download", "enqueue-pass skip src=$src key=$key")
               return
           }
           lastWarmKey = key; lastWarmAtMs = now
       }
       …
   }
   ```
   and log `src=playAlbum|shuffleAlbum|restoreQueue|transition` so the next export is
   self-explanatory.

**Verification.** One `enqueue-pass src=…` line and ~13 `enqueue` lines per 16-track queue
start. Falsifier: if two `enqueue-pass` lines appear with the same key, the guard is not
effective (called from different instances or threads not synchronized).

### 4.2 F3 (P3) — `saveQueueState` has two writers on a parallel dispatcher

**Observation.** Pairs of saves 10–17 ms apart at 18:52:34.195/.212, 37.342/.352,
39.028/.040, 49.845/.858 — but single saves after transitions at 18:52:18, 42, 56.

**Explanation.** Two call sites, not two listeners:

- `MediaService.kt:1079` — after `onMediaItemTransition` (queue changed)
- `MediaService.kt:917` — inside `onIsPlayingChanged(false)` (pause)

A playlist change that also pauses produces a pair; a playlist change while already paused
produces one. Both persist full snapshots through `playbackManager.buildQueueStateFromDual()`
and `persistenceManager.save(...)`.

Hot-path `saveQueueState()` launches the Room write on `scope`
(`CoroutineScope(SupervisorJob() + Dispatchers.IO)`, `MediaService.kt:831-836`, launch at
`:3379`), **not** on `persistenceScope` (`:842`). `persistenceScope` is used for teardown /
cast-disconnect drains. Both scopes are unconstrained `Dispatchers.IO`, so commit order is
not guaranteed. A pause-time snapshot (new index/position) can be overwritten by the
slightly older transition snapshot. The server `savePlayQueue` call is debounced 1.5 s
(`:3394-3425`); the Room write is not. (Original draft attributed the pair-race to
`persistenceScope` alone — corrected in §10.3.)

**Impact.** Rare: restore can lag by one transition (stale index or position). Teardown is
protected by `saveQueueStateSync` on `onDestroy`/`onTaskRemoved` (`:2094`, `:2188`), so
process-death data loss is unlikely. Classified P3.

**Fix.**
1. Serialize the Room writer used by `saveQueueState()` (`scope.launch` at `:3379`) —
   `Dispatchers.IO.limitedParallelism(1)` for that path, or a dedicated single-threaded /
   `Channel`-backed writer shared with teardown. Serializing `persistenceScope` alone
   misses the hot pair-race (§10.5).
2. Skip identical snapshots: compare `(ids.hashCode, currentIndex, position/1000)` with the
   last write; skip if unchanged.
3. Optional 120–200 ms coalescing for the Room write too (the teardown path stays
   synchronous).
4. Log `save src=transition|pause|teardown gen=<n>` so the next export shows which writer
   won.

### 4.3 F4 (P2) — Counts are three different SoTs and the reconciler is WM-gated

Header fields:

| Field | Source | Value |
|---|---|---|
| `tracks` | `cached_album_tracks` ledger — `Daos.kt:1041-1043` | 20 325 |
| `corpus` | `tracks` table incl. densified rows — `Daos.kt:222-224` | 20 534 |
| `songSum` | Σ `cached_albums.song_count` — `Daos.kt:1045-1047` | 20 453 |

Arithmetic:

- `corpus − tracks = 209` → search rows with no album-track ledger row (densify ghosts,
  playlist-only rows, or stale rows).
- `songSum − tracks = 128` → album songs not yet fetched into the ledger (soft-stop or
  albums whose tracks were never drained).
- `corpus − songSum = 81` → ghosts not accounted for by any album’s `song_count`.

Caveat: `song_count` is a server-reported field, so the 128 gap can also mean the server
metadata moved after the last sync — which is exactly what DELTA would have fixed.

ADR-0108 reconciles the corpus **on FULL only**, and FULL is WM-gated (F1). So the two
findings compound: the drift is expected while F1 is unfixed, and the *measurement* is
misleading because `tracks` and `corpus` are printed side by side without labels.

**Fix (diagnostics first, behaviour second).**
- Rename: `tracksLedger=`, `corpus=`, `songSum=`, and add `albumTracksAlbumsZero=` (already
  exists as a DAO query) to expose albums with no cached tracks.
- Move `mode=… corpus=… songSum=…` into the header block, not after the body.
- After F1 is fixed, run a manual Resync once and confirm `corpus ≈ songSum` and
  `tracks ≈ songSum`; if not, investigate per-album drain failures (ADR-0139 soft-stop).

### 4.4 F5 (P3) — `homeRecentFetchedMs` conflates success, timeout and empty

`HomeRecentCache` stores a single timestamp (`HomeRecentCache.kt:31-50`):

- `writeSnapshot()` on success
- `touchFetchedMs()` on timeout (`LibraryViewModel.kt:1567-1570`) and on empty list
  (`:1585-1587`, `MetadataSyncWorker.kt:778-780`)

So `homeRecentFetchedMs=18:49:27` + `homeRecentIdsCount=10` can mean “fresh list” **or**
“30 s timeout, old ids retained” (ADR-0112). This matters because the DELTA trigger is
`newIds != previousIds` (`LibraryViewModel.kt:1597-1603`); a permanently failing fetch looks
identical to a healthy one and never triggers DELTA.

Also note the Home strip itself is refreshed independently of the library sync: the newest-10
fetch at 18:49 wrote `ensureCachedAlbumsForLikes` rows. That is why Home can look current
while the library metadata is 28 h stale.

**Fix.** Add `PREF_HOME_RECENT_LAST_RESULT = "ok" | "timeout" | "empty"` and print
`homeRecentResult=` in the header. Optional: backoff the empty/timeout case with a persisted
counter instead of reusing the success TTL.

### 4.5 F6 (P3) — Diagnostics ergonomics

1. **Route template.** `NavHost.kt:117-122` logs `navBackStackEntry.destination.route`,
   which is the pattern (`settings/about?anchor={anchor}`), not the resolved route. Either
   log the argument value/pattern key, or label the line `routePattern=` so nobody hunts a
   navigation bug.
2. **`lastWmAttemptMs=0`.** Render `never` when ≤ 0 and add live WorkManager state:
   `wmState=`, `wmRunAttempts=`, `wmLastEnqueueMs=`, `wmNextRunMs=`, `wmPeriodHours=`.
   This turns the 68-second window into a multi-day signal.
3. **Ring window.** Add `ringSince=` and `clearCount=` to the header (and log
   `DiagnosticLog.clear()`). A multi-day failure cannot be diagnosed from a fresh process.
   Optional: persist a rotated tail of the ring to `filesDir` (bounded, e.g. 256 KB).
4. **Trailing line.** Put `mode/skip/corpus/songSum` in the header for machine parsability.
5. **Enqueue/pass identity.** Add `src=` (F2) and `save src=` (F3).

### 4.6 F7 (P3, doc/behaviour mismatch) — “ENTIRE queue” prefetch skips earlier tracks

`enqueuePlayQueue` iterates `for (i in start until size)` with `start = currentIndex + 1`
(`PlaybackManager.kt:1104-1143`). The doc comment above it claims the entire queue is
cached. For a restore at `idx=2`, tracks 0–1 are never prefetched by this path. Usually
harmless (they were cached when they were upcoming), but after a fresh install or an
imported queue, earlier tracks stay uncached until played. Fix the comment or enqueue
`0 until size` with the current track skipped.

---

## 5. Open anomaly — 21-track context at `pos=90934`, no transition log

At 18:52:56 the save reports `tracks=21 contextSize=21 idx=0 pos=90934`, four seconds after
a `count=1 pos=0` state. There is **no** `transition` line and **no** seek line. Then
`pos` jumps +38.97 s in 3.7 s wall time, then advances normally.

Candidate explanations, cheapest first:

1. **User interaction.** Dragging the Now Playing seek bar (+90 s, +39 s) and/or a queue
   action that appends via `addMediaItems` (appending after the current item does not emit
   an item transition). Both would be invisible today.
2. **Continuous play.** `maybeLoadContinuousPlay` (ADR-0053/0074) extends the context from
   the journal on transition; if it appends after the current item, no transition is logged
   and `contextSize` grows. Position should still continue from the current track’s real
   position, so the 90 s figure still needs a seek.
3. **Stale persistence read.** A queue-restore path reading a row written by the previous
   process (21 tracks / 90.9 s) would produce exactly this, and F3’s out-of-order writes
   make it possible. It should be accompanied by a `setMediaItems` transition, but the
   transition listener is gated on `PlayerHolder.player`/`isCasting` and could be missed
   during service setup.

**Instrumentation to decide (do this before touching playback):**
- Log `onPositionDiscontinuity(reason, oldPosition, newPosition)` (media3 1.10.1) in
  `MediaService` — distinguishes seek vs auto vs internal.
- Add `queueGen=<n>` to `saveQueueState`, bumped on every Dual mutation; log the Dual
  context source/size on replace.
- Log `DualQueue` context appends (`appendContext size-before/after`).
- Log `save src=…` (F3).

Until then this stays an open question, not a defect.

---

## 6. Fix plan

### 6.1 P0 — install the Hilt worker factory (required)

```kotlin
// FtpmusicApp.kt
@HiltAndroidApp
class FtpmusicApp : Application(), androidx.work.Configuration.Provider {

    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() { … }
}
```

```xml
<!-- src/main/AndroidManifest.xml: add xmlns:tools on <manifest> if absent,
     then inside <application>: -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <!-- On-demand WorkManager init via Configuration.Provider -->
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Both parts are required. If you implement `Configuration.Provider` but leave the
`androidx.startup` initializer, WorkManager is initialised first with the default factory and
the provider is never consulted — the bug survives. This is the standard Hilt + WorkManager
setup; see `androidx.hilt` docs. The app manifest currently has **no** `xmlns:tools`;
add it (or an equivalent merge rule) when applying the remove node (see §10.3).

Also add an explicit startup probe for debug builds (or a diagnostics field) so this class of
failure is visible without a full logcat:

```kotlin
// debug only, after scheduling
WorkManager.getInstance(this)
    .getWorkInfosForUniqueWork("metadata_sync")
    .addListener({
        val infos = it.get()
        android.util.Log.d("ftpmusic-work", "wm state=" + infos.joinToString { wi ->
            "${wi.state}/attempts=${wi.runAttemptCount}/next=${wi.nextScheduleTimeMillis}"
        })
    }, ContextCompat.getMainExecutor(this))
```

### 6.2 Tests to prevent regression

1. **Static guard (cheap, runs everywhere):**
   ```kotlin
   @Test fun `app exposes a hilt worker factory`() {
       assertTrue(Configuration.Provider::class.java.isAssignableFrom(FtpmusicApp::class.java))
   }
   ```
   It would have failed on 2.0.0.
2. **Instrumented wiring test** (`WorkManagerTestInitHelper` + Hilt test runner): enqueue
   `SyncScheduleWorker` once and assert `doWork` reached `recordWmAttempt` (or that the
   worker transitions to `SUCCEEDED`). This tests the factory, not the sync logic.
3. Keep the existing direct-construction tests — they cover logic, not wiring.

### 6.3 P2 fixes

| Item | Change |
|---|---|
| F2 | One prefetch owner (guard key + `src=`), 2 s window |
| F3 | Serialize Room writer for `saveQueueState()` (`scope.launch` at `:3379`; shared single-thread / limitedParallelism(1) with teardown); skip identical snapshot; log `save src=` |
| F4 | Rename header counters; move trailing line into header; after F1 fix, run one Resync and re-check |
| F5 | Add `homeRecentResult` pref + header field |
| F6 | `ringSince`/`clearCount`; WM state fields; route args; `never` sentinel |
| F7 | Fix comment or iterate `0 until size` |

### 6.4 Optional hardening (product decision)

Because WM can legitimately be throttled on aggressive OEM builds (and on Android 17’s job
quota), add a cheap foreground stale-check after first frame:

```kotlin
// FtpmusicApp or MainActivity, after first composition
val last = maxOf(metadataSyncWorker.lastFullSyncMs(), metadataSyncWorker.lastDeltaSyncMs())
val intervalMs = syncIntervalHours * 3_600_000L
if (last > 0 && System.currentTimeMillis() - last > intervalMs) {
    metadataSyncWorker.syncNowAsync(mode = LibrarySyncMode.DELTA) // gates: offline/reachable/cellular/cooldown
}
```

ADR-0109 already accepts a Home-open DELTA trigger; this extends the same idea to app open.
It needs a persisted attempt timestamp to avoid retry storms while the server is down
(the current `lastSyncFinishMs` cooldown is process-local — field at
`MetadataSyncWorker.kt:236`, cooldown check at `:343-344`).
Treat as optional, not a substitute for F1.

### 6.5 Do **not** do

- Do **not** remove the transition-path prefetch to “fix” F2 without the guard — it is the
  hook that covers Android Auto / MediaSession / external queue starts.
- Do **not** change `playSingleTrack` to preserve the context tail. ADR-0061 explicitly
  assigns search/profile taps to a single-item CONTEXT; changing it breaks documented
  behaviour and the market contract.
- Do **not** trust the `UPDATE`-resets-period theory. Verified in WorkManager 2.9.1
  bytecode: `WorkerUpdater.updateWorkImpl` copies the old `lastEnqueueTime` into the updated
  spec, and periodic next-run = `lastEnqueueTime + interval`. Re-enqueueing on every launch
  does not postpone the window.
- Do **not** add a UNIQUE constraint to “fix corruption from double enqueue” — the download
  queue already uses `INSERT OR IGNORE` on a unique `track_id`, and the observed duplicates
  are reads racing, not duplicate rows.

---

## 7. Verification runbook

### 7.1 Confirm F1 (before the fix)

```bash
# 1. WorkManager's own DB (debug build; name may be androidx.work.workdb)
adb shell run-as com.lucasdss.ftpmusic.app \
  sqlite3 databases/androidx.work.workdb \
  "select worker_class_name,state,run_attempt_count,last_enqueue_time,period_count \
   from WorkSpec where worker_class_name like '%SyncScheduleWorker%';"

# 2. Lifecycle logs
adb logcat -c
adb logcat -s WM-WorkerWrapper WM-JobService ftpmusic-work ftpmusic-metasync

# 3. JobScheduler view
adb shell dumpsys jobscheduler | grep -i -A6 ftpmusic

# 4. Battery/standby state (rule out OEM throttling)
adb shell dumpsys deviceidle | grep -i -E "whitelist|com.lucasdss"
adb shell am get-standby-bucket com.lucasdss.ftpmusic.app

# 5. WorkManager diagnostics broadcast (debug only; noisy)
adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS \
  -p com.lucasdss.ftpmusic.app
```

Decision table:

| Result | Conclusion |
|---|---|
| `FAILED`, attempts > 0, `WM-WorkerWrapper Could not instantiate` | F1 confirmed (factory) |
| `ENQUEUED`, attempts 0, `last_enqueue_time` older than interval | scheduler/OS; not factory — investigate doze/standby/restrictions |
| `ftpmusic-work` lines present | worker ran; pref write/read problem, revisit |

### 7.2 Confirm F2/F3 instrumentation

```bash
adb logcat -s ftpmusic-download:D ftpmusic-playback:D | grep -E "enqueue-pass|save src="
```

- Queue start: exactly one `enqueue-pass src=…`; `enqueue trackId=` count ≈ queue size − idx − 1.
- Pause after transition: one `save src=transition` then one `save src=pause` (serialized).

### 7.3 Post-fix acceptance

1. Cold start → `lastWmAttemptMs` becomes non-zero within one interval (or immediately if
   the window is already due); `ftpmusic-work periodic sync start mode=DELTA|FULL` appears.
2. Manual Resync once → `corpus ≈ songSum`, `tracks ≈ songSum`.
3. Next diagnostics export shows `wmState=ENQUEUED|RUNNING`, `homeRecentResult=ok`,
   `ringSince` and `clearCount`.

---

## 8. Cross-model review record (why this report differs from first drafts)

The review ran per the android-feature-loop cross-check rule (Mistral discover → DeepSeek
challenge → Mistral challenge of the audit). Several plausible-sounding claims were tested
and **rejected**; recording them prevents a wrong fix:

| Claim | Verdict | Evidence |
|---|---|---|
| “The app doesn’t use WorkManager at all; workers are started directly.” (Mistral) | **Wrong** | `FtpmusicApp.kt:139` → `NetworkPolicyHolder.kt:108-111` calls `enqueueUniquePeriodicWork`. `MetadataSyncWorker.start()` only covers first-run (`albumCount()==0`). Retracted by the same agent after source quoting. |
| “`ExistingPeriodicWorkPolicy.UPDATE` rolls the 4 h window forward on every launch, so the job is never ripe.” (DeepSeek) | **Wrong** | WorkManager 2.9.1 `WorkerUpdater.updateWorkImpl` copies `oldWorkSpec.lastEnqueueTime` into the new spec before `updateWorkSpec`; periodic `calculateNextRunTime` = `lastEnqueueTime + interval`. Also, this cannot explain a 6-day-old install that was already overdue. |
| “`playSingleTrack` destroying the queue is a UX bug; preserve the context tail.” (DeepSeek) | **Wrong (intended)** | ADR-0061 §3: “Non-collections (search results, profile recent) → `playSingleTrack`”; `COLLECTION_PLAY_FROM_TRACK_BEHAVIOR_REPORT.md` contract table. |
| “`reason=3` is `TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED`.” (Mistral) | **Imprecise** | media3 1.10.1: `TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0`, `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3`; the log is from `onMediaItemTransition`, so 3 is correct semantically. |
| “Double enqueue corrupts the queue / needs a UNIQUE constraint.” (Mistral) | **Wrong** | `CacheQueueItemEntity` already has a unique `track_id` and `insertIgnore`; duplicates are two concurrent read/update passes, not duplicate rows. |
| “Every track is enqueued exactly twice.” (both drafts) | **Imprecise** | 23 lines / 13 ids (10 doubled, 3 single) — the signature of a read-before-write race, which is why a “delete one call site” fix must keep the transition hook or add a guard. |
| “`lastWmAttemptMs=0` could be a lost `apply()`.” | **Unlikely** | The same prefs file holds non-zero `last_full/delta_sync_ms` written with `apply()`; `recordWmAttempt` executes before every `doWork` branch, so any execution would have stamped it. |

The P0 conclusion survived both challenges and is agreed by both models, with the residual
uncertainty being device-level (see §7.1 decision table), not code-level.

---

## 9. Appendix

### 9.1 WorkManager 2.9.1 bytecode check (UPDATE semantics)

From `work-runtime-2.9.1.aar` (local Gradle cache):

- `WorkSpec.calculateNextRunTime()` delegates to `WorkSpec$Companion.calculateNextRunTime(...)`.
- For periodic work (`isPeriodic=true`): next = `lastEnqueueTime + intervalDuration`
  (or `lastEnqueueTime + initialDelay` when `periodCount == 0`), with a flex adjustment.
- `WorkerUpdater.updateWorkImpl$lambda$2` reads `oldWorkSpec.lastEnqueueTime` and passes it
  into `WorkSpec.copy$default(...)` before `WorkSpecDao.updateWorkSpec(...)`.

Conclusion: `ExistingPeriodicWorkPolicy.UPDATE` preserves the schedule window. It is not the
cause of F1.

### 9.2 media3 1.10.1 constants

```
TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED      = 0
TIMELINE_CHANGE_REASON_SOURCE_UPDATE         = 1
MEDIA_ITEM_TRANSITION_REASON_REPEAT          = 0
MEDIA_ITEM_TRANSITION_REASON_AUTO            = 1
MEDIA_ITEM_TRANSITION_REASON_SEEK            = 2
MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3
```

`MediaService` logs the second group (`onMediaItemTransition(mediaItem, reason)`), so
`reason=3` = “queue was rebuilt”, not “track ended”.

### 9.3 Log arithmetic used in this report

- Body: 55 lines total; 54 present when `lines=54` was logged; 1 is the share line itself.
- Queue prefetch: `start = idx + 1 = 3`; `urgentEnd = min(3 + 3, 16) = 6` →
  indices 3,4,5 at priority 0, indices 6…15 at priority 1 → 13 distinct tracks.
- Counts: `20534 − 20325 = 209`; `20453 − 20325 = 128`; `20534 − 20453 = 81`.
- Position deltas: `129906 − 90934 = 38972 ms` over `3.703 s`; `132845 − 129906 = 2939 ms`
  over `3.171 s`.

### 9.4 Key code references

| Concern | Path |
|---|---|
| App wiring | `FtpmusicApp.kt:138-139, 161-170` |
| WM scheduling | `data/cache/NetworkPolicyHolder.kt:94-116` |
| WM worker | `data/db/SyncScheduleWorker.kt:24-86` |
| Sync gates / start / cadence | `data/db/MetadataSyncWorker.kt:184-266, 344-348, 826-836` |
| Diagnostics export | `data/diagnostics/DiagnosticLog.kt:16-120`; `ui/settings/SettingsViewModel.kt:925-950` |
| Queue prefetch | `playback/PlaybackManager.kt:468, 540, 567, 1104-1143`; `playback/MediaService.kt:1008` |
| Download enqueue | `data/cache/DownloadManager.kt:122-259` |
| Queue persistence | `playback/MediaService.kt:831-836 (scope), 842 (persistenceScope), 917, 1079, 3362-3490` |
| Counts | `data/db/Daos.kt:222-224, 1041-1047` |
| Home recent TTL | `data/db/HomeRecentCache.kt:20-55`; `ui/library/LibraryViewModel.kt:1567-1603` |
| Route logging | `ui/NavHost.kt:117-122` |

---

## 10. Code-validation amendment (2026-11-01)

Re-validated against current `compose/` sources after the original analysis. §§0–9 are kept
as the original reasoning chain; this section records confirmations, corrections, new
discoveries, and fix-plan deltas. **No app code was changed in this amendment.**

### 10.1 Method

1. Re-read cited call sites under `compose/app/src/main/java/com/lucasdss/ftpmusic/app/`
   (`FtpmusicApp`, `SyncScheduleWorker`, `MetadataEnrichWorker`, `NetworkPolicyHolder`,
   `MetadataSyncWorker`, `PlaybackManager`, `MediaService`, `DownloadManager`,
   `DiagnosticLog`, `SettingsViewModel`, `HomeRecentCache`, `LibraryViewModel`, `NavHost`,
   `Daos`).
2. Grep for `HiltWorkerFactory`, `Configuration.Provider`, `workManagerConfiguration`,
   `lastWarmKey`, `homeRecentResult` — all absent from production source (findings unfixed).
3. Cross-check related ADRs: ADR-0022, 0045, 0048, 0060, 0061, 0067, 0071, 0079, 0108,
   0109, 0112, **0119**, 0139, 0140.
4. Confirm dependency versions: `work-runtime-ktx` **2.9.1**, `hilt-work` **1.2.0**,
   media3 **1.10.1**.
5. Search for alternate automatic sync schedulers (`AlarmManager`, app-owned
   `JobScheduler`, `BOOT_COMPLETED`) — none found for library DELTA/FULL.

### 10.2 Confirmed (no change to severity)

| Id | Verdict | Notes |
|---|---|---|
| **F1** P0 | **CONFIRMED, still unfixed** | `FtpmusicApp` is plain `Application()`; zero `HiltWorkerFactory` / `Configuration.Provider` hits. Unique work name `"metadata_sync"`. Release-blocking. |
| **F2** P2 | **CONFIRMED** | Explicit warm at `PlaybackManager.kt:468,540,567` + transition re-warm `MediaService.kt:1008`. Loop `start = si + 1` at `:1108-1111`. |
| **F3** P3 | **Race CONFIRMED** | Two writers (`onMediaItemTransition`, `onIsPlayingChanged(false)`); unconstrained `Dispatchers.IO`. Scope attribution corrected in §10.3. |
| **F4** P2 | **CONFIRMED** | Three SoTs (`cached_album_tracks` / `tracks` / `SUM(song_count)`); trailing append `SettingsViewModel.kt:948-949`; ADR-0108 FULL-only reconcile. |
| **F5** P3 | **CONFIRMED** | `writeSnapshot` + `touchFetchedMs` share one ms field; DELTA only on id change. |
| **F6** P3 | **CONFIRMED** | Template `route=`; `lastWmAttemptMs=0` ambiguous; trailing mode/corpus line. |
| **F7** P3 | **CONFIRMED** | KDoc “ENTIRE queue” vs `si + 1`; also in ADR-0022 §6 (see D4). |
| **§5** | **Still open** | Continuous-play / Dual append can grow without item transition; recommended instrumentation not landed. |
| **§6.1** | **Still correct** | Provider + remove `WorkManagerInitializer` matches WM 2.9.1 + hilt-work 1.2.0. |
| **§8 rejections** | **Hold** | UPDATE preserves `lastEnqueueTime`; ADR-0061; media3 constants; insertIgnore already present. |

### 10.3 Corrections (amend prior wording)

1. **Enrich worker class name.** The `@HiltWorker` class is `MetadataEnrichScheduleWorker`
   in `MetadataEnrichWorker.kt:172`. Earlier “MetadataEnrichWorker” referred to the file.
   Collateral under F1 (enrich never runs via WM) still holds; unique work name is
   `"metadata_enrich"`.

2. **AppUpdateProbe not fully dead.** `SyncScheduleWorker` post-sync `appUpdateProbe.probe()`
   is unreachable under F1, but `FtpmusicApp.onCreate` still launches a cold-start probe
   (`FtpmusicApp.kt:140-148`). Softened F1 impact: **WM-cadence probe dead; cold-start
   probe still live** → tray degraded, not necessarily absent.

3. **F3 writer scope (material).** Hot `saveQueueState()` Room write is
   `scope.launch { … }` at `MediaService.kt:3379` (`scope` = `:831-836`).
   `persistenceScope` (`:842`) is teardown / cast-disconnect. Serializing only
   `persistenceScope` would **miss** the observed pair-race. See §4.2 (updated) and §10.5.

4. **`lastSyncFinishMs` cite.** Field declaration is `MetadataSyncWorker.kt:236`;
   cooldown read is `:343-344`. §6.4 now cites both.

5. **btResumeEvents ADR.** The diagnostics ring is **ADR-0119**; ADR-0071 is the broader
   BT autoplay decision. Header and §1.1 updated.

6. **Gradle line drift.** hilt-work / work-runtime block is `app/build.gradle.kts:254-258`
   (report previously said `:255-258`).

7. **F1 manifest prerequisite.** App `AndroidManifest.xml` currently lacks `xmlns:tools`.
   The §6.1 `tools:node="remove"` snippet requires adding that namespace (or an equivalent
   manifest-merger rule); otherwise the remove does not apply.

8. **F2 insert path nuance.** `DownloadManager.enqueue` uses `insertIgnore` + loser
   upgrade (`:235-258`) for new rows — duplicate *rows* are largely handled. The observed
   double `cached_skip_upgrade` lines are a concurrent read / priority-upgrade race on the
   early check-then-act path (`:122-181`), not duplicate inserts. Guard-key + `src=` remains
   the right fix; do not add another UNIQUE constraint.

### 10.4 New discoveries

| Id | Sev | Discovery | Fix consideration |
|---|---|---|---|
| **D1** | P3 | Hot save uses `scope`, not `persistenceScope` (same as §10.3.3) | Retarget §6.3 serialization to `saveQueueState()` Room launch site; optionally share one single-threaded writer with teardown |
| **D2** | note | Continuous-play / Dual append logs go to Logcat only (`maybeLoadContinuousPlay`), not `DiagnosticLog` | Add DiagnosticLog lines for Dual append size-before/after — required to close §5 from Share diagnostics |
| **D3** | note | `onPositionDiscontinuity` exists (`MediaService.kt:1138+`) but is DiagnosticLog-silent | Log `reason` / old / new position into diagnostics before treating the 18:52:56 anomaly as a defect |
| **D4** | P3 | ADR-0022 §6 also claims “ENTIRE queue” prefetch | Fix KDoc **and** ADR-0022 together with F7 (comment-only or iterate `0 until size` skipping current) |
| **D5** | P3 | `countUncachedAlbums` already exists in DAO (`Daos.kt:1053-1059`) but is unused in the export | Wire into F4 header rename as `albumTracksAlbumsZero=` |
| **D6** | note | No missed AlarmManager / BOOT_COMPLETED library sync paths | Home RESUMED id-change DELTA (ADR-0109) remains the only non-WM automatic library sync; §6.4 optional stale-check still valuable after F1 |

### 10.5 Fix-plan deltas (amend §6; do not replace)

- **F1:** Keep §6.1. Add `xmlns:tools` (or equivalent) when removing
  `WorkManagerInitializer`. Keep the static
  `Configuration.Provider::class.java.isAssignableFrom(FtpmusicApp::class.java)` unit
  test. Prefer also surfacing live WM state in diagnostics (F6) so factory failures are
  visible without logcat.
- **F2:** Prefer option 2 (guard-key + `src=` , ~2 s window). Keep the transition-path
  prefetch (Auto / MediaSession / external queue starts).
- **F3:** Serialize the Room writer used by `saveQueueState()` (`scope.launch` at
  `:3379`). Teardown may stay on `persistenceScope` **or** share the same
  single-threaded / `Channel` writer. Skip identical snapshots; log `save src=`.
  Do **not** treat “`persistenceScope.limitedParallelism(1)` alone” as sufficient.
- **F4:** Rename counters; move trailing line into header; wire `countUncachedAlbums`
  (D5). After F1, one manual Resync then re-check corpus / songSum / tracks.
- **F5–F7:** Unchanged intent (result pref; ergonomics; comment/ADR or full-queue iterate).
- **§5:** Instrumentation first — DiagnosticLog for `onPositionDiscontinuity`, Dual append
  size-before/after, `save src=` / `queueGen` — before any playback behaviour change.
- **§6.4 optional stale-check:** Still valid after F1; remember `lastSyncFinishMs` is
  process-local (`:236`). Persist an attempt stamp if used, to avoid retry storms.
- **§6.5 do-not list:** Unchanged. §8 rejections re-validated against WM 2.9.1 and
  media3 1.10.1.

### 10.6 Review considerations

Checklist for the next engineer closing these findings:

1. **Before declaring F1 fixed on-device:** run §7.1. Distinguish
   `FAILED` + `Could not instantiate` (factory — this report) from
   `ENQUEUED` + `run_attempt_count=0` + old enqueue time (OEM/doze/quota — different bug,
   same symptom).
2. **After F1 lands:** cold start → non-zero `lastWmAttemptMs` / `ftpmusic-work` lines;
   one manual Resync → re-check `corpus ≈ songSum`, `tracks ≈ songSum`.
3. **F2:** do not delete transition-path prefetch without the guard; keep `src=` in
   diagnostics so the next export proves a single warm pass.
4. **F3:** fix the **hot** Room launch site (`scope`), not only `persistenceScope`.
5. **Do not** change `playSingleTrack` / search-tap queue replace (ADR-0061).
6. **Do not** treat §5 as a defect until DiagnosticLog shows discontinuity and Dual-append
   provenance (D2/D3).
7. **F7 / D4:** update both the KDoc and ADR-0022 if the behaviour stays “upcoming only”.
8. **Diagnostics-only improvements** (F4 labels, F5 `homeRecentResult`, F6 WM state /
   `ringSince`, D5 `albumTracksAlbumsZero`) are safe to ship independently of F1 and
   improve the next Share export even if OEM throttling remains.

### 10.7 Amendment verdict

Original P0 (F1) and secondary findings F2–F7 survive code re-validation. Material
correction: F3 scope attribution and AppUpdateProbe cold-start nuance. New discoveries
are mostly observability / doc-consistency (D2–D6) plus the F3 retarget (D1). Minimal
correct first code change remains §6.1 (Hilt `Configuration.Provider` + remove default
WorkManager initializer, with `xmlns:tools`).

---

## 11. Validation of the §10 amendment (2026-11-01)

Independent re-check of every §10 claim against current sources. Method: line-exact reads
of the cited ranges plus repo-wide greps for the supposed-absent symbols. Cross-model:
DeepSeek validated, then Mistral challenged the verdicts (7 agree / 1 correction). **No app
code changed.** §10 survives with two wording fixes and one new finding (D7).

### 11.1 Claim-by-claim verdicts

| §10 claim | Verdict | Evidence |
|---|---|---|
| 10.3.1 `MetadataEnrichScheduleWorker` in `MetadataEnrichWorker.kt:172` | **Confirmed** (cite nits) | `@HiltWorker` at `:172`, class declaration at `:173`. Use `:172-173`. `UNIQUE_WORK_NAME = "metadata_enrich"` at `:47`. |
| 10.3.2 cold-start `AppUpdateProbe` still live | **Confirmed** | `FtpmusicApp.kt:140-148` launches `appUpdateProbe.probe()` in `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. |
| 10.3.3 hot save uses `scope`, not `persistenceScope` | **Confirmed** | `scope` decl `MediaService.kt:831-836`, launch `:3379`; `persistenceScope` `:842` used only at `:1319`, `:1872`, `:2812`, `:3471` (teardown / cast disconnect). |
| 10.3.4 `lastSyncFinishMs` cite | **Confirmed** | Field `MetadataSyncWorker.kt:236`; read `:343-344`; write `:573`. It is in-memory only — process-local, as stated. |
| 10.3.5 btResumeEvents ring is ADR-0119 | **Confirmed** | `docs/adr/0119-bt-connect-autoplay-market-hardening.md` §Context item 3 + Decision (Poweramp-style last-BT-outcomes in Share diagnostics). ADR-0071 is the broader autoplay decision. |
| 10.3.6 gradle block `:254-258` | **Confirmed** | `compose/app/build.gradle.kts:254` comment, `:255` `workVersion = "2.9.1"`, `:256` work-runtime-ktx, `:257` hilt-work 1.2.0, `:258` hilt-compiler. media3 `1.10.1` at `:184`. |
| 10.3.7 manifest lacks `xmlns:tools` | **Confirmed** | `compose/app/src/main/AndroidManifest.xml` root `<manifest>` declares no `tools` namespace; the §6.1 `tools:node="remove"` merge rule would be a no-op/merge error until added. |
| 10.3.8 `insertIgnore` + loser upgrade `:235-258` | **Confirmed** | `insertIgnore` `DownloadManager.kt:235`; `inserted == -1L` loser-upgrade `:243-250`; dedup/inserted logs to `:258`; early existing-row path `:122-234` with `cached_skip_upgrade` at `:178`. |
| D1 hot save retarget | **Confirmed, extended** | Same as 10.3.3; see D7 — the writer set is larger than two. |
| D2 continuous-play logging | **Wording wrong, direction right** | `maybeLoadContinuousPlay` (`MediaService.kt:2668-2788`) logs via `android.util.Log` only — correct. But `appendToContext` (`PlaybackManager.kt:710-729`) emits **no log at all** (neither Logcat nor DiagnosticLog), so “Dual append logs go to Logcat only” is false; there are no append logs. The proposed DiagnosticLog instrumentation is therefore even more necessary. |
| D3 `onPositionDiscontinuity` DiagnosticLog-silent | **Confirmed** | `MediaService.kt:1138-1157`: only `sourceErrorCircuit.onUserSeek()` and `seekCoalescer.onSettled()`; no DiagnosticLog, no Logcat line. |
| D4 ADR-0022 also says “ENTIRE queue” | **Confirmed** | `docs/adr/0022-offline-enforcement-and-download-safety.md` §6 heading “Queue-always-cached (whole queue)” and “enqueues the ENTIRE queue”. Loop starts at `si + 1` (`PlaybackManager.kt:1104-1111`), so indices ≤ current are skipped. |
| D5 `countUncachedAlbums` exists, unused in export | **Confirmed** | `Daos.kt:1053-1059` (query + `suspend fun`); repo-wide grep shows the declaration only, no callers. |
| D6 no AlarmManager / BOOT_COMPLETED / JobScheduler / SyncAdapter sync | **Confirmed** | Zero source hits. Only `PeriodicWorkRequestBuilder` is `NetworkPolicyHolder.kt:100`. Non-WM `syncNow*` callers: `SyncingScreen.kt:186,253` (user), `LibraryViewModel.kt:1224` (Resync), `LibraryViewModel.kt:1599` (Home id-change DELTA), `MetadataSyncWorker.kt:262` (first-run only), `SyncScheduleWorker.kt:44` (WM). Home id-change is indeed the only non-WM automatic path. |

### 11.2 D7 (P3, new) — there are **three** full-snapshot queue writers, not two

§4.2 / D1 correctly retargeted F3 from `persistenceScope` to `scope`, but still frames the
race as two writers. There is a third, much more frequent writer:

- `PlaybackManager.scope = CoroutineScope(Dispatchers.IO)` — `PlaybackManager.kt:111`
  (unconstrained; reassignable via `:78`).
- `PlaybackManager.persistenceSave(...)` — `:1293-1319`, `scope.launch { persistenceManager.save(...) }`
  at `:1305`.
- ~18 call sites, all from queue mutations / appends / clears, e.g. `:469`, `:568`, `:597`,
  `:628`, `:656`, `:689`, `:726` (`appendToContext`), `:761`, `:788`, `:823`, `:852`,
  `:908`, `:948`, `:1002`, `:1035`, `:1085`, `:1344`, `:1511`.
- `QueuePersistenceManager.save` (`playback/QueuePersistenceManager.kt:37-102`) has **no
  mutex**, and is itself read-modify-write: `val prev = dao.getState()` (`:85`) followed by
  `dao.replaceAllAndState(...)` (`:86`). Two concurrent saves can therefore lose
  `sleepTimerEndMs` / `isPlaying` / `repeatMode` / `shuffleEnabled` merges in addition to
  committing an older snapshot last.

Consequences:

1. **F3 fix location.** Serializing `MediaService.saveQueueState()` alone still leaves
   `PlaybackManager.persistenceSave` racing it. Serialize inside
   `QueuePersistenceManager.save` (single-threaded dispatcher / `Mutex` / `Channel` writer),
   or make it the only writer and have both callers enqueue snapshots to it.
2. **§5 explanation gains a mechanism.** `appendToContext` → `persistenceSave` writes a
   grown context without any log. `MediaService.saveQueueState` logs
   `tracks/contextSize/idx/pos`, but the write that actually persisted the 21-track context
   may have been the silent `persistenceSave`; the visible line is only the next snapshot.
3. **Position semantics differ.** `saveQueueState` samples `currentPosition` *before*
   launching (`MediaService.kt:3370`); `persistenceSave` reads
   `PlayerHolder.player?.currentPosition` *inside* the coroutine (`PlaybackManager.kt:1310`).
   The latter is “position at execution time”, which can drift later than the index/context
   that was snapshotted — a second, independent way to persist an inconsistent
   index/position pair.

### 11.3 F2 guard-key refinement (fix-plan caveat)

The proposed key `31 * tracks.hashCode() + currentIndex` is not reliable across the two
call sites:

- `PlaybackManager.enqueuePlayQueue` receives the caller’s `alignedTracks`
  (`playAlbum`/`shuffleAlbum`/`restoreQueue`), `PlaybackManager.kt:468,540,567`.
- `MediaService.onMediaItemTransition` passes `buildQueueStateFromDual()`
  (`MediaService.kt:1005-1008`), i.e. the merged PRIORITY+CONTEXT projection.

Same logical queue, potentially different list order/flags → different `hashCode()` → guard
misses → double warm persists. Also `currentIndex` can differ by one if a transition has
already advanced between the two calls.

Use a key both sites cannot disagree on:

```kotlin
// canonical identity of the installed queue, independent of caller list shape
val key = dualQueue.entryIds().joinToString(",") + "|" + playbackManager.contextSize
```

or bump a `queueGen` counter in `DualQueue` on every replace/append and key on
`(queueGen, currentIndex)`. Keep `src=` in the log either way.

### 11.4 Validation verdict

- **F1 (P0) unchanged** — no `Configuration.Provider`, no `HiltWorkerFactory`, plain
  `Application()`, `"metadata_sync"` unique periodic work. Release-blocking; §6.1 + the
  manifest namespace fix remain the minimal first change.
- **F2–F7 unchanged in severity**; F3’s fix scope is now wider (D7), and D2’s wording must
  be corrected before anyone relies on “append logs exist”.
- **§5 stays open** — D2/D3 instrumentation is a prerequisite for calling it a defect.
- **§10 otherwise accurate**; two editorial fixes: D2 wording, `MetadataEnrichWorker.kt:172-173`.

## 12. Fix implementation and per-finding QA (2026-11-01)

Scope: F1–F7 and D1–D7 (D6 required no code change). Each area was reviewed by an
independent Mistral reviewer against the code (read-only, no test/bypass of the
building), and every substantive finding was re-checked by the orchestrator before
being accepted or rejected.

### 12.1 What shipped

| Finding | Change | Regression guard |
|---|---|---|
| F1 | `FtpmusicApp : Application(), Configuration.Provider` + injected `HiltWorkerFactory`; manifest removes `androidx.work.WorkManagerInitializer` via `tools:node="remove"` (on-demand init) | `FtpmusicAppWorkManagerTest` guards the **code half** (interface + injected-field reflection); the manifest half is review-verified only (see §12.5) |
| F2 | Coalescing guard in `enqueuePlayQueue` keyed on order-insensitive id hash + `currentIndex`, 2 s window, `hasWarmed` sentinel, injectable `elapsedRealtimeMs` clock, `src=` logging | 2 `PlaybackManagerTest` cases (duplicate pass collapsed, new index re-warms, window expiry re-warms) |
| F3 / D1 / D7 | Serialisation + generation gate inside `QueuePersistenceManager.save` (not at one call site); generation sampled at snapshot time by all three writer paths; `savePositionOnly` / `savePlaybackExtras` share the mutex | 5 `QueuePersistenceManagerTest` cases (stale, out-of-order, identical, monotonic, url-only) |
| F4 / D5 | Labelled single header line `libraryCounts albums=… artists=… tracksLedger=… corpus=… songSum=… albumTracksAlbumsZero=…`; trailing append removed; `countUncachedAlbums()` wired | `DiagnosticLogTest`, `SettingsViewModelTest` |
| F5 | `home_recent_last_result` (`ok`/`timeout`/`empty`) written by every `touchFetchedMs` caller | header `homeRecentResult=` |
| F6 | Live WorkManager `state/attempts/next`, `lastWmAttemptMs=never` for ≤0, `ringSince=`, `clearCount=` | `SettingsViewModelTest` |
| F7 / D4 | KDoc and ADR-0022 §6 corrected to “upcoming queue” + amendment | doc review |
| D2 | `appendToContext` now logs size-before/after; continuous-play outcomes logged per branch | DiagnosticLog lines |
| D3 | `onPositionDiscontinuity` logs `reason/oldIdx/oldPos/newIdx/newPos` before branching | DiagnosticLog lines |
| D6 | No code change — confirmed there is no non-WorkManager automatic library-sync path | — |

### 12.2 QA verdicts

All findings returned `APPROVE` or `APPROVE-WITH-NITS`; none were rejected outright.
Accepted nits (fixed in the same change):

1. **Fingerprint omitted URLs (F3/D7).** A legacy/proxy URL refresh rewrites
   `QueueItemEntity.url` without changing ids, index, flags or position, so the
   identical-snapshot skip could persist a stale URL. `urls` is now part of
   `Objects.hash`, with a dedicated test.
2. **`catch (Exception)` around the DAO call (F4/D5).** Swallowed
   `CancellationException` and broke structured concurrency. Now rethrown.
3. **`shareDiagnostics()` ran the snapshot on `Dispatchers.Default` (F4/F6).**
   The snapshot blocks up to 2 s in `WorkManager.get()` and reads Room. Moved to
   `Dispatchers.IO`.
4. **`lastWarmKey = 0` at process start (F2).** A key that legitimately hashes to 0
   within 2 s of boot could be skipped. Added the `hasWarmed` sentinel.

Rejected after verification (kept as-is, reasons recorded so they are not
re-litigated):

- **“Call `WorkManager.getInstance(this)` in `onCreate` to avoid duplicate init.”**
  This would force eager init and defeat the `Configuration.Provider` on-demand
  path; it also throws `IllegalStateException` in plain-JVM tests. Rejected.
- **“The default `generation = nextGeneration()` can go backwards.”** `AtomicLong`
  capture at the call expression is exactly the snapshot-time ordering the fix
  needs; a later capture is always greater.
- **“`savePlaybackExtras` can be clobbered by an identical snapshot.”** `save()`
  re-reads `prev` *inside* the mutex and copies sleep/play extras, so the
  fresh extras win. No change needed.
- **“`Objects.hash` collisions are unacceptable.”** The key covers ten fields and a
  collision only skips one write; the next position tick rewrites. A full-snapshot
  key would pin a queue-sized list in a singleton. Left as a hash.
- **“Teardown `runBlocking` can starve the main thread.”** `performDestroyCleanup`
  is unbounded-but-async; only `onTaskRemoved` blocks, bounded at the pre-existing
  `lastChancePersistBoundMs = 1500 ms`. The mutex adds only the duration of an
  in-flight Room write. No regression.
- **Renames `tracksLedger` → `cachedTracks`, `albumTracksAlbumsZero` → longer name.**
  Both labels are specified by §4.3/D5 and are used by the export contract; renaming
  would break parity with this report. The SQL comments were left intact. Dated
  historical reports (`DIAGNOSTICS_20261011`, `20261019`) are snapshots and were
  deliberately not rewritten.
- **“`savePositionOnly` can lose to an older full snapshot.”** Pre-existing and
  bounded by one position-poller interval (3 call sites in `MediaService`); gating
  position-only writes on a generation would instead drop a pending structural
  snapshot (reorder) that has not reached the DB yet — a worse failure. Accepted.

### 12.3 §6.4 foreground self-heal (implemented, tier A+C)

§6.4 shipped in the hardened form described there as a prerequisite, because the
naive version had an unsolved retry-storm risk: the existing `MIN_SYNC_COOLDOWN_MS`
and `ReachabilityStateHolder` gates are **in-memory**, so every cold start reset
them.

- `data/sync/SyncHealthPolicy.kt` — pure decision table over persisted stamps
  (`lastSuccess`, WM heartbeat, fallback stamp/streak, interval, offline,
  reachable). Verdicts: `NeverRan`, `NotDue`, `SkippedOffline`,
  `SkippedUnreachable`, `Backoff`, `Trigger`.
- `data/sync/SyncHealthGuard.kt` — runs once per app foreground
  (`ProcessLifecycleOwner` ON_START, wired in `FtpmusicApp`), reads prefs, logs
  `sync-health verdict=…` to `DiagnosticLog`, and at most fires one DELTA via the
  existing `syncNowAsync` (so offline / unreachable / cellular / cooldown /
  `isSyncing` gates still apply).
- Silence is `now - max(lastSuccess, lastWmAttempt)` — an attempt that ran but
  failed counts as a sign of life, so a dead server is surfaced rather than
  retried harder.
- Backoff `1 h → 6 h → 24 h` is **persisted** and stamped *before* the trigger, so
  a process kill cannot re-arm the same failure epoch; the streak resets on the
  next success.
- `WorkInfo.runAttemptCount` is deliberately unused: it is per-run and resets on
  success, so it cannot serve as a heartbeat.

Not shipped, with reasons: a WM health probe that *acts* (tier B) — F6's
`workManager state/attempts/next` now gives the evidence first; a second periodic
watchdog — throttled identically to the worker it watches; an AlarmManager
fallback — a second scheduler for a P3; `CANCEL_AND_REENQUEUE` — resets
`lastEnqueueTime` and therefore *postpones* the next run.

### 12.4 Verification

- `./gradlew :app:assembleDebug` — SUCCESS
- `./gradlew :app:testDebugUnitTest` — 3963 tests, 0 failures, 0 errors, 6 skipped
- `./gradlew ktlintCheck` — SUCCESS

### 12.5 Phase-5 audit and adversarial challenge

DeepSeek audited the full diff; a Mistral reviewer then challenged that audit. Both
reviews were required to agree before commit. Outcome:

| Audit item | Verdict | Resolution |
|---|---|---|
| B1 "teardown snapshot can be dropped as stale — lost queue" | **Rejected by both** | `generation` is sampled at snapshot time, so a dropped snapshot is always the *older* one; the retained write has newer content in every mutex order. The suggested `src != "teardown"` exemption would let an older teardown overwrite newer state — actively unsafe. No change. |
| B2 "`withTimeoutOrNull` consumes a generation that a later save reuses" | **Rejected by both** | `AtomicLong.incrementAndGet()` never reuses a value, `Mutex.withLock` cannot leave a partial write, and a timed-out teardown simply writes nothing while later snapshots carry higher generations. `catch (_: Exception) {}` in a fire-and-forget teardown coroutine is intentional best-effort. No change. |
| B3 stale `MediaService` "whole queue" comment | **Accepted** | Comment now says "every track after the current one … next 3 urgent". |
| B4 no test guards F2 | **Accepted** | Two `PlaybackManagerTest` cases; the guard now reads the existing `elapsedRealtimeMs` seam so the 2 s window is tested without sleeping. |
| B5 `FtpmusicAppWorkManagerTest` cannot catch a manifest regression | **Accepted as a doc fix** | A merged-manifest assertion is brittle build-output coupling; the §12.1 claim was narrowed instead of adding a fake guard. |

Adjudicated out of the challenge (each claim was checked against the code and is
false): the identical-fingerprint branch *does* advance `lastAppliedGeneration`; the
`persistenceSave` snapshot *is* sampled before the launch; the timeout cannot mark a
snapshot applied because only `save()` stamps `lastAppliedGeneration`; `FtpmusicApp`
*does* implement `Configuration.Provider` and the manifest *does* declare `xmlns:tools`.

Residual accepted risks: `savePositionOnly` does not participate in the generation
gate (bounded by one position-poller tick; gating it would risk dropping a pending
structural snapshot), and `QueuePersistenceManager.clear()` bypasses the mutex (every
current caller clears only after `buildQueueStateFromDual()` returned empty).
