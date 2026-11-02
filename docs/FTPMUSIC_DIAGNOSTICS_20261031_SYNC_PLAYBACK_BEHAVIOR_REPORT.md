# FTPMusic Diagnostics — 2026-10-31 Sync + Playback Behavior Report

Date: 2026-10-31 (review 2026-11-01; amended after code re-validation 2026-11-01;
§13 added 2026-11-01 from a second capture; §14–§15 added 2026-11-01)
Status: **Analysis only. No code changed in this pass.** Amended §10 after
re-validating cited call sites against current `compose/` sources. §12 records the
F1–F7 implementation; §13 is a second, *pre-fix* capture (`2026-11-01T17:45Z`),
re-validated in §13.8 and challenged again in §13.9. §14 adds a server-side-deletion
amendment for the 12:32 burst and the new finding **N11**; §15 is the implementation
specification for N3, N4, N5, N8 and N1 option C. Nothing in §13–§15 has been
implemented: N1 is gated on a product decision (§13.8.1) and N11 on classifying the
burst (§14.5/§14.7).
Source: two user-supplied `Settings → Share diagnostics` exports
(`2026-10-31T18:53Z` → §1–§11, `2026-11-01T17:45Z` → §13).
Build: `2.0.0 (26)`, `sdk=37`, device label `Pixel 8 Pro` in this line is
unverified — the second export reports `model=SM-S942B`; see §13.4/A4.
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

---

## 13. Second capture (2026-11-01T17:45Z) — re-validation and new findings

Status: **analysis only; no code changed in this pass.** Appended after the F1–F7
implementation (§12), from a *different* export than §1–§11.

### 13.1 Provenance — which build produced this export

Header: `version=2.0.0 (26)`, `sdk=37 model=SM-S942B`, ring covers
`12:24:38.960 → 17:45:00.946Z` = **5 h 20 m 22 s**, 676 lines. CAP is 1000, so nothing
was evicted — and the first line is `route=splash`, i.e. process start, so a
`DiagnosticLog.clear()` cannot have removed anything either (the pre-fix build has no
`clearCount` to prove it otherwise).

This export **predates** `32ff1e5` / `7812bae`. Proof, by the absence of every
post-fix marker:

| Post-fix marker | Present in this export? | Introduced by |
|---|---|---|
| `libraryCounts albums=… artists=…` header line | **no** | F4 |
| `homeRecentResult=` | **no** | F5 |
| `workManager state=… attempts=… next=…` | **no** | F6 |
| `ringSince=` / `clearCount=` | **no** | F6 |
| `lastWmAttemptMs=never` (prints `0` instead) | **no** | F6 |
| `enqueue-pass src=…` (`ftpmusic-playback`) | **no** | F2 |
| `save src=… gen=…` (`ftpmusic-queue`, from `QueuePersistenceManager`) | **no** | F3 |
| `positionDiscontinuity reason=…` | **no** | D3 |

So this is *corroboration of the same defects on a second day*, not a regression
report against the fixes. No post-fix **device** capture exists yet — §12.4 verification
is unit-test only. Two consequences for reading §13:

1. Every "confirmed" item below is a second independent data point, not a repeat of §1.
2. Line numbers cite **HEAD (post-fix)**. Each item says whether the defect is still
   present on HEAD (`open`) or was already fixed by §12 (`fixed`).

Also a doc defect to fix: the front matter says `Pixel 8 Pro`, but this export says
`model=SM-S942B` and §1.1 never quoted the `model=` line. One of the two is wrong; it
matters for §13.4/A1 (install identity).

### 13.2 What the second capture confirms

| Finding | Independent evidence in this export |
|---|---|
| **F1** WorkManager dead | `lastWmAttemptMs=0` and `lastWmSkipReason=-` at capture; `lastFullSyncMs=1791020482855` = `2026-10-03T09:41:22Z` (**29 d 8 h**), `lastDeltaSyncMs=1792585710469` = `2026-10-21T12:28:30Z` (**11 d 5 h**) with `syncIntervalHours=12` ⇒ **22 missed DELTA windows**. The only sync in the window started 0.3 s after `route=home` (`12:24:38.983 → 12:24:39.282`), i.e. the ADR-0109 Home id-change path — the D6 conclusion holds: it is the sole surviving automatic path. *Caveat:* the 29 d / 11 d arithmetic assumes the stamps belong to this install — see A1. The mechanism (no `Configuration.Provider`, no worker factory) is independent of it. |
| **F2** double prefetch | Most priority-0/1 enqueues are followed by a `dedup` of the same id 1–3 ms later: `5l1O8p20FXusZP1ZUMg4nl` `.067675 inserted` / `.069253 dedup`, `3MLRRtlHaHxbvorFRJBWSM` `.099375` / `.100130`, `7bjqCtBg7O2EzvOD8fQws6` `.070102 inserted` / `.070681 dedup`. 49 `inserted` + 29 `dedup` rows for 50 unique tracks in 0.13 s ⇒ ~37 % redundant work. The burst also contains timestamp inversions (`.069253` printed before `.069227`); N9 shows those are not proof of concurrency, so they are *not* used as such here. |
| **F3** two writers, 4 ms apart | `transition … 12:28:22.418634` → `saveQueueState .419008` **and** `saveQueueState .423126`; same shape at `12:28:30.336` (`+.336984`, `+.344775`). Two independent samples. |
| **F4** three SoTs | Within one run: `sync end … albums=5091` vs header `albums=5017` (**−74**); `phase=tracks tracks=30674` vs header `tracks=29510` (**−1164**); `artists=370` vs header `artists=320` (**−50**); trailing `corpus=30515` vs `songSum=44369` (**−13 854**). These are exactly the reconciliation gaps F4's labelling makes attributable — and A3 shows labelling alone does not explain them. |
| **F6** ergonomics | `route=library?tab={tab}`, `route=settings/about?anchor={anchor}` still emit unexpanded patterns; the trailing `mode=DELTA skip= corpus=… songSum=…` is still appended after the body with no key prefix (`skip=` empty). |
| **F5** | Not observable here in either direction (no `homeRecentResult` field in this build, and the export cannot distinguish "timeout" from "empty"). The `albums.isEmpty()` → `RESULT_EMPTY` path is in the tree (`LibraryViewModel.kt:1587-1592`), so F5 remains a labelling fix, unverifiable from this artifact. |
| **F7** | No new evidence (behaviour unchanged: `si + 1`). |

New confirmations of *rate*, not just presence: the 128 ms prefetch burst enqueued 78
requests for 50 unique tracks; a 755-track queue burned **58** tracks in 3.95 s (N1).

### 13.3 New findings

#### N1 (P1) — the cross-track Source-error circuit is neutralised by the auto-skip it guards

**Evidence.**

- `12:32:41.156` a 755-track queue is set (`transition reason=3 idx=0 count=755`).
- `12:32:41.225` first `player error: Source error`. Over the next **3.95 s** the export
  shows **58** occurrences of `autoSkip action=SKIP_NEXT … errorCount=1` (failed `idx`
  0…57), each paired with `removeCached … result=ok` and a `transition reason=2` to
  `idx=1…58`.
- **No `CIRCUIT_STOP`** in the burst. Playback resumed on `idx=58`
  (`1nqmgs4iJnL9WgXe47t0xK`, scrobbled at `12:36:37`), so the failure was item-level,
  not queue-wide.
- Contrast `12:29:33.973 → 12:29:36.372`: a **single-track** queue (`count=1`), three
  errors on the *same* item 1.4 s apart → `CIRCUIT_STOP errors=3` fired on the third.

⇒ the circuit counts errors only while the queue does **not** advance.

> **Scope note (added 2026-11-01).** N1 explains *propagation*, not *cause*. The export
> does not establish why the 58 items failed: the `Source error` message is a media3
> literal and `errorCode`/`cause` are dropped by the logger. A server-side deletion is
> now the leading hypothesis for this contiguous burst; see §14.1/§14.3 and the new
> finding **N11** (§14.4). N1's mechanism is independent of the cause and remains valid
> either way.

**Mechanism (open on HEAD).** `SKIP_NEXT` advances with `ep.seekToNextMediaItem()`
(`MediaService.kt:1461`). media3 constants (verified from the 1.10.1 `classes.jar`, see
§9.2 for the transition half): `DISCONTINUITY_REASON_SEEK = 1`,
`SEEK_ADJUSTMENT = 2`, `AUTO_TRANSITION = 0`, `SKIP = 3`. A seek that changes the item
index emits **both** `onMediaItemTransition(SEEK)` and
`onPositionDiscontinuity(SEEK)`, and the export proves the transition half: every skip
line carries `reason=2` = `MEDIA_ITEM_TRANSITION_REASON_SEEK`, while natural advances
carry `reason=1` (AUTO) and queue sets `reason=3` (PLAYLIST_CHANGED). The same seek
therefore reaches the handler that clears the circuit on **any**
`DISCONTINUITY_REASON_SEEK` / `SEEK_ADJUSTMENT` (`MediaService.kt:1151-1155`):

```kotlin
if (reason == Player.DISCONTINUITY_REASON_SEEK ||
    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
) {
    sourceErrorCircuit = sourceErrorCircuit.onUserSeek()
}
```

The reset was meant for *user* seeks (`onUserSeek()`), but it keys on the *symptom*
every seek shares, including the programmatic one issued by the error handler itself.
`playerErrorCount` is separately reset per transition (`MediaService.kt:977`), so
`RETRY_LIMIT_STOP` cannot fire either. The resets that *should* survive are play ≥ 2 s
with no error pending (`:1584`) and an explicit user seek; the reset inside
`CIRCUIT_STOP` (`:1511`) is the aftermath of the trip itself, not a competing path.

**Elimination proof (independent of media3 internals).** The 12:32:41 burst produced 58
errors inside a 15 s window with no trip, so the counter must have been cleared between
every pair of errors. On HEAD the only writers of `sourceErrorCircuit` are
`:1151-1155` (seek discontinuity), `:1511` (CIRCUIT_STOP, not reached — no log line),
`:1584` (poller, requires `isPlaying` and a *fresh* `currentPosition ≥ 2000 ms`, which
cannot hold 68 ms after a seek-to-0), and `:1369` (the increment itself). Only the
discontinuity path is reachable, therefore it fired on every skip. That is also why the
single-track queue at 12:29:36 *did* trip: `LAST_TRACK_STOP` never seeks.
Service or player recreation inside the burst is excluded too: it would emit `route=`
/ restore lines that the export does not contain, and it would leave a *fresh* counter
(0), which still trips at the third error inside the 15 s window. `Player.Listener`
callbacks are delivered on the app main looper, so `onPlayerError` cannot interleave
with itself.

**Smoking gun.** The service already has an explicit "this transition is not a user
action" marker: `onPlayerError` sets `PlayerHolder.playbackErrorAutoSkipInFlight = true`
before every auto-skip (`:1382`), and `onMediaItemTransition` consumes it (`:979-983`)
precisely so the sticky error banner survives the programmatic transition. The
discontinuity handler was never taught about that marker. Nothing new has to be
invented; one condition is missing. (Note: the `playerErrorCount = 0` on the line above,
`:977`, is *not* guarded — it resets on every transition, which is why
`RETRY_LIMIT_STOP` cannot fire during a skip either.)

Fragility note: the bug is specific to the *command*. `seekToNextMediaItem()` /
`seekTo()` are seeks; `seekToNext()` / `seekToPrevious()` would emit
`DISCONTINUITY_REASON_SKIP = 3`, which this handler does **not** reset on. Switching
commands must not be mistaken for a fix, and a fix must not rely on that accident.

**Why the tests missed it.** `SourceErrorCircuitTest` (6 cases) tests the pure state
machine only. Nothing asserts *which* reset the `MediaService` calls on which path, so
"error → skip → error → skip → error" is untested.

**Blast radius.** 58 items skipped in 3.95 s, up to 58 `removeCached` calls, sticky error
banner, playback resumed at `idx=58`. Scaled to the 5 017-album corpus an entire
artist/album queue can be consumed in minutes.

**Severity caveat (added on re-validation — this changes the shape of the fix).** In
*this* log the bypass was arguably benign: 58 consecutive *item-level* failures were
skipped and the 59th track played. A circuit that tripped at the 3rd error — which is
what options 1 and 2 below restore — would have **stopped playback at track 3** instead
of reaching a working track. ADR-0111's rule ("3 consecutive source errors ⇒ stop") is
therefore only right when the failures are *systemic* (server down, expired token,
corrupt cache region); it is wrong for a run of individually broken items. Fixing N1 by
counting skips blindly would trade one bad behaviour for another. See §13.8.1 for the
decision this implies.

**Fix options, in preference order.**

1. **Reset on intent instead of symptom (recommended)** — delete the discontinuity-based
   reset at `:1151-1155` and reset from the two *user* entry points instead:
   - in-app UI: the `PlaybackControl.SEEK_TO` branch (`MediaService.kt:4552-4557`),
     before the coalescer check so a coalesced/dropped seek still counts as intent;
   - external clients: override `MediaSession.Callback.onSeekTo` in
     `MediaSessionCallback` (currently absent — line 86 shows the callback interface; the
     session is built on `exoPlayer` in `MediaModule.kt:108`, so notification, lock
     screen, Android Auto, AVRCP and Cast seeks currently reach the player *without*
     passing through `PlaybackControl`). The override resets the circuit and delegates
     the seek to `session.player`.

   No dependency on media3 internals, and the only thing that can clear the counter
   becomes an actual user command (plus verified play ≥ 2 s). It also stops the other
   programmatic seeks (`:2489`, `:3348`, `:3359`, `:4636`) from silently clearing the
   circuit.
2. **Guard the existing reset with the existing flag (minimal fallback)** — at
   `:1151-1155` consult `PlayerHolder.playbackErrorAutoSkipInFlight`, the marker the
   recovery path already maintains. ~1 line, and it covers external seeks too, but it is
   safe
   only because media3 1.10.1 dispatches `onPositionDiscontinuity` before
   `onMediaItemTransition` (§13.8.1). If that order ever inverts, the guard silently
   stops working — the failure mode is invisible.
3. **Do not** add a second counter increment in the `SKIP_NEXT` branch. `onSourceError`
   already increments exactly once per failure; a second increment would make the
   threshold fire at 2 failures (and worse for `CACHED_JUMP`). Instead add a
   `circuit clear src=user-seek|play-2s|…` diagnostic line so the next capture can prove
   *which* path reset the counter.

Note that the failure being observed at all *implies* the reason was `SEEK` or
`SEEK_ADJUSTMENT`: a `DISCONTINUITY_REASON_SKIP` (3) would not match the condition at
`:1151-1155` and the circuit would never have been cleared. The observed behaviour is
therefore its own proof of the reason.

**Test to add:** feed `error → seekToNext → error → seekToNext → error` through the
handler-shaped seam and assert trip; plus the negative case (genuine user seek still
clears).

**Verification status (updated on re-validation):** the discontinuity reason no longer
needs to be "confirmed" — the elimination proof above settles it from build-26 evidence
alone, and media3 1.10.1 constants agree. A post-fix capture showing
`positionDiscontinuity reason=1` before the second error is confirmation, not a gate.
The gate is now a *product* decision: item-level fast-skip (current behaviour) vs
systemic stop.

#### N2 (P2) — a mass skip leaves no aggregate or terminal line, and `removeCached` cannot be told from a no-op

- The burst in N1 ends at `idx=58` with **no** `CIRCUIT_STOP`, `RETRY_LIMIT_STOP`,
  `STOP_NO_CACHED` or abort line — the next line is a scrobble 3 m 52 s later. From the
  artifact alone the loop looks like it was interrupted by something unlogged.
- `removeCached … result=ok` is emitted unconditionally after
  `audioCache.removeResource()` (`CacheService.kt:561-566`), which returns nothing.
  Therefore the 58 identical lines prove *calls*, not *evictions*. (`result=refused_pinned`
  and `result=heal_poison_pinned` do distinguish the interesting cases — good — but the
  common case is blind.)
- The F6 export cannot answer "did we just delete 58 cache entries?", which is the first
  question a user asks after a silence burst.

**Fix:** on burst end/trip, log one summary
`autoSkip burst count=… firstIdx=… lastIdx=… durationMs=… evicted=… cause=…`; and add
`hadSpan=true|false bytes=…` to `removeCached` (cheap: `getSpanFile(trackId) != null`
before the call).

#### N3 (P2) — `worker write fail` is not diagnosable, and retry exhaustion is silent

**Evidence.** `40yUsifXvxWpy6Pji8Y191` (priority 2, the *currently playing* track):
`worker write fail` at `12:44:31.132`, `12:44:33.320`, `12:44:36.336` — then nothing.

**Mechanism (open on HEAD).**

- The line carries neither reason nor size (`DownloadManager.kt:411-417`).
- The three candidate causes are distinguishable only in logcat or not at all:
  zero-length body → `Log.w` only, **no** DiagnosticLog (`CacheService.kt:195-199`);
  locked/busy import → `Log.w` only (`:269-272`, `:283-287`); metadata/IO failure →
  `Log.w` only (`:303`).
- `handleRetry` (`DownloadManager.kt:470-484`) with `MAX_RETRIES = 3` gives 1 s then 2 s
  — matching the observed 2.2 s / 3.0 s spacing — and on the third call writes
  `STATUS_FAILED` with **no DiagnosticLog line at all**. The row is then terminal and
  invisible: `failed` rows are re-armed only while `retryCount < MAX_RETRIES`
  (`:94-102`).

So an auto-cache that never succeeded, for a track the user was playing, leaves zero
explanation in the artifact. **Fix:** `worker write fail reason=zero_length|locked|busy|io
bytes=… retry=n/3`; a `worker terminal reason=retries_exhausted` line; promote the
zero-length branch to DiagnosticLog.

#### N4 (P2) — soft-stop is cause-blind in the export

`soft-stop: cadence+metadata_version skipped` (`12:26:13.143`) is immediately followed by
`sync end phase=complete … elapsed=93865ms` (`12:26:13.145`), so the artifact's last word
on the run is "complete". Cause: `syncAlbumTracks` returns `false` after
`TRACK_CONSECUTIVE_FAIL_SOFT_STOP = 8` consecutive `fetchAlbumTracks() == null`
(`MetadataSyncWorker.kt:1485`, const at `:217`) — logged to logcat only
(`"Soft-stopping track drain after $streak consecutive failures"`), while `phase` is
still set to `"complete"` (`:528-536`). The export therefore reads "sync finished" and
"watermark skipped" with no way to learn *which* albums failed or why.

**Fix:** `soft-stop reason=album_fetch_streak streak=8 firstAlbumId=… err=…` and
`sync end drainComplete=false`.

#### N5 (P2) — `lastDeltaSyncMs` is structurally unable to show freshness after a soft-stop (F1 amplifier, open on HEAD)

ADR-0068 deliberately stamps the cadence only on a full track drain
(`MetadataSyncWorker.kt:545-560`); the header prints `lastDeltaSyncMs` as the freshness
signal. In this capture a DELTA run refreshed the corpus (5 091 albums) at `12:26:13`
and the header still said **11 days**. *Confound:* A1 shows the same key went backwards
between captures, so this particular value may also be a restored prefs snapshot rather
than a missed stamp. The claim below is therefore proven from the code, not from this
number. The loop that follows:

1. F1 leaves no WorkManager ⇒ the only automatic sync is Home-id-change
   (`LibraryViewModel.kt:1602-1606`, `newIds != previousIds`).
2. Every such run soft-stops on a large corpus ⇒ no watermark.
3. `lastDeltaSyncMs` stays ancient ⇒ the next new album on the server re-runs the whole
   ~94 s DELTA.

The user-visible result is a permanently "dead" sync indicator on an app that is in fact
syncing, plus repeated full-corpus deltas. **Fix (display only, do not change
ADR-0068):** header `lastSyncAttemptMs=`, `lastSyncOutcome=complete|soft-stopped|error`,
`softStopCount=`, `lastDrainCompleteMs=`.

#### N6 (P3) — `phase=tracks tracks=N` logs the corpus, not this run

`MetadataSyncWorker.kt:486-492` logs `trackDao.trackCountAll()`. In the export that
reads as "fetched 30 674 tracks in 94 s". **Fix:** `phase=tracks fetched=… corpus=…`.

#### N7 (P3) — no process boundary in the ring, so a process death reads as an outage

`13:26:16.241 saveQueueStateSync` (teardown) is the last line before
`17:44:35.259 transition reason=3` — 4 h 18 m with **zero** lines — and the pair is
bracketed by `ftpmusic-reach | server unreachable` (`13:26:16.246`) and
`server reachable again` (`17:44:35.306`). Cold reading: a 4-hour outage that healed
itself. Reality: the process was killed (`onTaskRemoved`) and the first probe of the new
process succeeded. The monitor is not at fault (`60 s` reachable / `15 s` unreachable,
`ServerReachabilityMonitor.kt:68-69`).

**Fix:** `app start pid=… ver=…` from `Application.onCreate`, `service teardown reason=…`
from `onDestroy`/`onTaskRemoved`.

#### N8 (P2) — the global reachability boolean flaps on a single failed call, and error recovery branches on it

`12:35:58.956 → 12:35:59.970` and `13:22:29.040 → 13:22:29.656` are sub-second
`unreachable`/`reachable again` pairs. `ReachabilityStateHolder` is flipped by **every**
API response (`NetworkModule.kt:159-179`: success → `onApiSuccess`, and any
`Connect/SocketTimeout/UnknownHost/Socket/IOException` → `onApiFailure`) *and* by the
monitor's ping. One `SocketTimeoutException` on any **Retrofit API** request is therefore
enough to declare the whole server unreachable, and the next success republishes it.
Playback (`DefaultHttpDataSource`) and Coil cover art use separate clients, so they do not
flip this boolean at all — which also means the boolean says nothing about stream health.

That boolean is a **branch selector inside error recovery**:
`serverUnreachable` decides `CACHED_JUMP` / `STOP_NO_CACHED` vs `SKIP_NEXT`
(`MediaService.kt:1383-1461`) and gates `OfflineAwareDataSource` stream selection. A
single flapped sample can therefore turn a recoverable skip into a hard stop (or the
reverse), non-deterministically. The `13:26:16` failure is a teardown `savePlayQueue`
flush, i.e. the last value before process death is poisoned by an irrelevant call.

**Fix:** hysteresis in `ReachabilityStateHolder` (N consecutive failures within a window
before flipping false; instant recovery true), or move the decision to a dedicated
`probeReachability(now)` that the error path calls. `AppIssueAggregator` already uses
`debounceAfterFirst`, so the pattern exists in-tree.

#### N9 (P3) — the export is not totally ordered

`DiagnosticLog.append` formats the timestamp **before** taking the lock
(`DiagnosticLog.kt:126-136`), so two threads can construct T1<T2 and append T2 first.
Observed in this artifact: `5l1O8p20FXusZP1ZUMg4nl … dedup` at `.069253` printed
*before* `24qpHmjw1O2g4OlFthB7mF … inserted` at `.069227`.

A second, independent cause of the same symptom is possible: `System.currentTimeMillis()`
is `CLOCK_REALTIME` and two cores can observe it inverted by microseconds. The artifact
cannot distinguish the two, and the distinction matters for what we can promise:
stamping inside the lock guarantees *append order == lock order*, not *timestamps
monotonic*. **Fix:** build the line inside the `synchronized` block; if the export is
then still not monotonic, switch the field to `elapsedRealtime`+start-time (or
`Instant.now` from a monotonic base) rather than papering over it.

### 13.4 Open anomalies — needs evidence, not a fix

#### A1 — `lastDeltaSyncMs` went *backwards* between the two captures

| Capture | `lastDeltaSyncMs` | UTC |
|---|---|---|
| §1 (2026-10-31T18:53) | 1793371011683 | 2026-10-30T14:36:51Z |
| §13 (2026-11-01T17:45) | 1792585710469 | **2026-10-21T12:28:30Z** |

Only `MetadataSyncWorker` writes this key, always `System.currentTimeMillis()`
(`:545-560`), i.e. monotonic. A 9-day regression therefore **cannot** come from app
logic: the prefs file was replaced (reinstall + auto-backup restore) or these are two
different installs (see the `model=` discrepancy in §13.1). It also means every
"days stale" number in §1.3 and §13.2 must be read per-install.

Evidence to collect: prefs file mtime, `PackageManager.getPackageInfo().firstInstallTime`,
and `last_full_sync_ms` (here `2026-10-03`, i.e. consistent with a 10-21-era backup).
Until then, do not "fix" anything here.

#### A2 — heard time exceeds track duration, and one transition is 5 m 51 s late

| Scrobble | `secs` (= duration) | `heardMs` | Δ |
|---|---|---|---|
| 12:36:37 `1nqmgs4i…` | 201 | 232 190 | **+31.2 s** |
| 12:39:59 `7C0hKGF5…` | 200 | 200 998 | +1.0 s |
| 12:47:15 `40yUsifX…` | 166 | 166 587 | +0.6 s |
| 12:50:25 `4uSI6zBj…` | 190 | 190 680 | +0.7 s |
| 12:59:03 `1LaHjsqy…` | 167 | 200 522 | **+33.5 s** |
| 13:01:50 `54JzNhpQ…` | 166 | 166 934 | +0.9 s |

Plus `12:52:03 pos=97389` (97.4 s into a 167 s track started at 12:50:25) with **no
log line at all** until `12:59:03` — 5 m 51 s later — while heard grew only 103 s.

`secs` is capped at duration on purpose (`ScrobbleDuration.kt:40`), so `secs` is not the
defect. The two candidates for `heardMs > duration`:

1. **End-of-track stall**: `isPlaying` stays true for ~30 s past the media end, so the
   poller counts silence (`MediaService.kt:1571-1578`) before media3 emits the end
   transition. In the `12:36:37` case heard ≈ the whole wall time since the item started
   (12:32:45 → 12:36:37 = 232 s), which fits a 201 s track + ~31 s stall *exactly*.
2. **Missed pause / unlogged replay** for the `12:59:03` case (317 s of the 420 s window
   accrued nothing, yet no `saveQueueState` pause line exists).

Ruled out: duplicate position-pollers. `handler` is the main looper (`:828`),
`onIsPlayingChanged` posts and the pause path `removeCallbacks` (`:897`, `:916`), and
both run on the same thread, so no interleaving can fork a second chain — *provided* the
chain is removed before it re-posts. That is worth an assertion test, not a fix claim.

Discriminators after a post-fix capture: D3 `positionDiscontinuity`, the
`save src=… gen=…` lines that `QueuePersistenceManager` now emits (the service-level
`saveQueueState` line itself still has no `src=` — join by timestamp), and (if we want
it) a `pollChain=<id>` field.

#### A3 — the corpus reconciliation gap is much larger than a label problem

`sync end albums=5091` vs header `albums=5017` (−74); `phase=tracks tracks=30674` vs
header `tracks=29510` (−1164); `songSum=44369` vs `corpus=30515` (−13 854). F4 makes
each number *attributable* but does not explain a 13 854-track gap. Either `song_count`
is inflated server-side (album-less / duplicate attributions) or ~31 % of the corpus is
missing locally. This needs one written-down reconcile definition (ADR-0108) or the
counts remain un-actionable.

#### A4 — device identity in the front matter is wrong

`Pixel 8 Pro` vs `model=SM-S942B`. Fix the front matter; it feeds A1.

### 13.5 Ranked recommendations (nothing implemented)

> **Ordering superseded (2026-11-01).** §14.7 replaces the execution order below:
> N11-1 + N3 first, then N4/N5/N8, then a capture to classify the burst, then
> N11-2/N11-3, and only then N1-C. The severities and costs in this table still stand;
> only the order changes. N11 is a §14 finding and is deliberately not renumbered into
> this §13 table.

| # | Item | Priority | Cost | Still open after the §12 fixes? | Gate |
|---|---|---|---|---|---|
| 1 | N3 write-fail/retry reason + terminal line | P2 | ~20 lines | yes, untouched by §12 | — |
| 2 | N4 soft-stop cause line | P2 | ~10 lines | yes, untouched by §12 | — |
| 3 | N5 attempt/outcome stamps in the header | P2 | ~20 lines | partially mitigated (WM retries again, so fewer soft-stops) but the header still cannot show a soft-stopped run | display only, no ADR-0068 change |
| 4 | N8 reachability hysteresis | P2 | design first | yes — and *more* exposed, since F1's fix increases background request volume | decide the owner of the boolean |
| 5 | N1 circuit reset on programmatic skip + integration test | P1 | ~10 lines (3 files) + test | yes, untouched by §12 | **product decision first** (§13.8.1) |
| 6 | N2 burst summary + `removeCached hadSpan` | P3 | ~10 lines | yes | — |
| 7 | N6 `fetched=` / `corpus=` split | P3 | 1 line | yes | — |
| 8 | N7 process-boundary lines | P3 | ~6 lines | yes | — |
| 9 | N9 stamp inside the lock | P3 | 2 lines moved | yes | — |
| 10 | A1 / A2 / A3 / A4 | — | evidence first | — | do not code |

**Execution order ≠ severity order.** Ship 1–4 first: they are observability and
branch-correctness, and without them the next device capture will be as ambiguous as this
one. N1 (row 5) is the only item that changes playback behaviour, and it now has a
product decision in front of it, so it goes after that decision and with the integration
test — not before. Rows 6–9 are one-liners that can ride along with any of the above.

### 13.6 What would falsify §13

- **N1** — a code path other than `MediaService.kt:1151-1155` that clears
  `sourceErrorCircuit`, or a `CIRCUIT_STOP` during a queue-advancing burst on build 26.
  In that case the reset is elsewhere and N1 is wrong about the *mechanism* (though not
  about the **58** skips). The media3 reason is no longer a falsifier: it is settled by
  the elimination proof in §13.3/N1 and by `DISCONTINUITY_REASON_SEEK = 1` in the 1.10.1
  AAR. A post-fix capture can only confirm.
- **N3** — a logcat capture showing `[writeCached] zero-length` for `40yUsifX…`
  confirms cause 1; `cache busy` confirms cause 2. Either way the *missing DiagnosticLog*
  part stands.
- **N5** — if a soft-stopped DELTA does advance `lastDeltaSyncMs` in some other path,
  the loop is not self-reinforcing. Grep says it does not (`:545-560` is the only
  writer), so this is low-risk.
- **A1** — a backup-restore marker or a different `firstInstallTime` resolves it; if
  neither, the monotonic assumption is wrong and that is a much bigger finding.

### 13.7 Cross-model challenge of §13 (2026-11-01)

A Mistral reviewer was asked to attack §13 against the sources, not to agree. Its
verdicts were internally inconsistent (three items were tagged `REJECT` and then restated
the claim as valid), so each item was adjudicated individually rather than accepted or
discarded wholesale.

| Challenge | Verdict after adjudication |
|---|---|
| C1 "media3 does not report `SEEK` for `seekToNextMediaItem()`, so N1's mechanism is wrong" | **Rejected — factually wrong, and N1 now carries a stronger proof.** Constants in media3 1.10.1: `DISCONTINUITY_REASON_SEEK = 1`, `SKIP = 3`. The export itself shows `transition … reason=2` = `MEDIA_ITEM_TRANSITION_REASON_SEEK` on every skip, and the elimination argument (§13.3/N1) shows `:1151-1155` is the only reachable reset. N1 keeps its mechanism and gained a "do not fix by switching command" warning. |
| C2 "N1 should be P0, not P1" | **Partially accepted, ranking unchanged.** P0 is reserved for crash / data loss / app-unusable. Here playback recovered on the next good track, and the user kept listening for 5 h. N1 is escalated to P0 only if a post-fix capture shows `evicted>0` in the N2 summary (i.e. the burst is destroying cache, not just skipping). That condition is now written into N2's fix. |
| C3 "`ReachabilityStateHolder` *is* debounced, but the harm is still material" | **Rejected as stated, claim unchanged.** There is no debounce: `onApiSuccess`/`onApiFailure` are edge-triggered (log only on change) and flip on the *first* qualifying event. `debounceAfterFirst` exists in `AppIssueAggregator`, not on the holder. N8 stands as written; C3's own conclusion ("material harm") agrees. |
| C4 "retry exhaustion is silent" | **Accepted — already N3.** Not a new defect, and its `REJECT` tag is a mislabel. |
| C5 "timestamp inversion is `currentTimeMillis()` cross-core inversion, not lock ordering" | **Accepted as a second cause; N9 rewritten.** Both causes produce the identical symptom and the artifact cannot separate them. The fix was narrowed to a promise it can keep (append order == lock order), with the monotonic-clock fallback named. |
| C6 (new) "`playerErrorCount` reset on transition can race `onPlayerError`" | **Rejected as new.** That reset (`:979`) is the mechanism already documented in N1, and the transition callback cannot interleave with `onPlayerError` on the same looper. |
| C7 (new) "`SourceErrorCircuit.onUserSeek()` itself defeats ADR-0111" | **Rejected.** Clearing on a genuine user seek is the intended ADR-0111 behaviour; the defect is the call site treating a programmatic skip as a user seek. |
| C8 (new) `STATUS_FAILED` not logged | **Accepted — duplicate of N3.** |

Net effect of the challenge: no finding was withdrawn; N1 gained an elimination proof
and a fragility warning, N9 was corrected to a two-cause statement, and N1's escalation
condition to P0 was made explicit. Nothing was implemented.

### 13.8 Re-validation record (2026-11-01, second pass)

Every claim in §13 was re-checked against the export *and* against the sources on
`HEAD`. The table records what changed and why; anything not listed survived unchanged.

| # | Claim as first written | Corrected to | Why it was wrong |
|---|---|---|---|
| 1 | 59 skips, "3.96 s" burst, queue parked at `idx=59` | **58** skips over **3.95 s** (`12:32:41.225 → 12:32:45.179`), failed `idx 0…57`, resumed at `idx=58` | The 58th transition lands *on* `idx=58`, which then played. Counting transitions as failures double-counts the landing point. |
| 2 | prefetch burst ≈ 0.9 s, "~50 `inserted` + ~25 `dedup`" | **0.128 s**, **49 `inserted` + 29 `dedup`** for **50 unique** tracks ⇒ 78 requests, **37 %** redundant | The burst was measured from the first `inserted` to the last `dedup`, not including the enqueue lines before it. |
| 3 | `seekToNextMediaItem()` at `:1481`; `playerErrorCount` reset at `:979`; reset block at `:1154` | `:1461`; `:977`; `:1151-1155` | Line numbers had drifted after the §12 edits; all three re-read from `HEAD`. |
| 4 | "`sync end phase=complete` is followed by `soft-stop: …`" | **`soft-stop: …` (`.143`) precedes `sync end phase=complete` (`.145`)** | The export ordering was inverted. The conclusion is unchanged and in fact sharpened: the *last* word the artifact has about the run is "complete", which is exactly the defect. |
| 5 | N5: the 11-day `lastDeltaSyncMs` proves the missing-stamp loop | N5 marked **confounded by A1**; the claim is now made from the code (`:545-560` is the only writer) | A1 shows the same key moving *backwards* between captures, so this value cannot be trusted as a freshness measurement. |
| 6 | §13.1 marker row "`saveQueueState … src=`" | replaced by two rows: `enqueue-pass src=…` (F2) and `save src=… gen=…` (F3) | The service-level `saveQueueState` line carries no `src=` even on HEAD; attribution appears one layer down, in `QueuePersistenceManager.save`. |
| 7 | "N9's timestamp inversion proves two concurrent callers" | N9 rewritten: inversion has **two** candidate causes (stamp-before-lock and `CLOCK_REALTIME` cross-core inversion); the promise is narrowed to "append order == lock order" | The artifact cannot separate the two. F2's concurrency claim now rests on the `inserted`/`dedup` pairs 1–3 ms apart, not on the inversion. |
| 8 | N1's gate: "confirm media3 reports `SEEK` before coding" | gate removed; replaced by an **elimination proof** (§13.3/N1) and by a **product decision** (§13.8.1) | Build-26 evidence alone excludes every other reset path, and the constants were extracted from `media3-common-1.10.1.aar`. |
| 9 | §13.5 ordered by severity | §13.5 now carries an explicit **execution order** (observability + branch correctness first, N1 last) | N1 is the only behaviour change and it is gated on a product decision; the others make the next capture interpretable. |

#### R1 — hypothesis tested and rejected: "`src` on `saveQueueState` is a dead parameter"

First pass suspected that the new `src=` plumbing was dead code, because the export has
no `src=` on the `saveQueueState` line. It is not: `saveQueueState(src)` forwards to
`PlaybackManager.enqueuePlayQueue(..., src)` and to
`QueuePersistenceManager.save(..., src)`, which logs
`save src=… gen=… tracks=…` (`QueuePersistenceManager.kt:74,170`). Attribution exists on
HEAD, one log layer below the line that was inspected. **Nothing to fix; nothing added
to §13.3.**

#### 13.8.1 N1 is now a product decision, not a bug fix

The mechanism is settled: the circuit is cleared by the very seek the error handler
issues. But "restore the circuit" is not a neutral repair — it changes what the app does
with a run of broken items:

| Option | Behaviour in *this* capture | Verdict |
|---|---|---|
| **A. Status quo** (item-level fast-skip) | 58 broken items skipped in 3.95 s; playback reached a working track at `idx=58`; 58 `removeCached` calls; sticky error banner | Reaches a playable track, but the systemic-outage guard (ADR-0111) does not exist, and N2 shows we cannot even tell what was deleted |
| **B. Restore the circuit as written** (3 consecutive errors ⇒ stop) | Would have stopped at **track 3**; the working track at `idx=58` would never have played | Correct for systemic failure, wrong for a run of individually broken items |
| **C. Restore the circuit *and* make it bounded + resumable** (recommended) | Trip after N consecutive **programmatic** skips (8 is a house precedent — `TRACK_CONSECUTIVE_FAIL_SOFT_STOP`), pause with a banner ("N tracks failed — tap to resume"), log an aggregate line, reset only on verified play (≥ 2 s) or an **explicit** user seek | Keeps the guard, bounds the blast radius, and does not reproduce either failure mode above |

Options B and C — and any repair at all — need the same first change: **the service must
not treat its own `seekToNextMediaItem()` as a user seek.** Most of the machinery already
exists:

- `onPlayerError` sets `PlayerHolder.playbackErrorAutoSkipInFlight = true` before the
  skip (`MediaService.kt:1382`) and clears it on the next transition (`:982`).
- `onMediaItemTransition` consumes that flag (`:979-983`) to keep the sticky error
  banner across the skip — so the service already distinguishes programmatic from user
  transitions.
- `onPositionDiscontinuity` never consults it, and unconditionally resets
  `sourceErrorCircuit` (`:1151-1155`).

So this is a consistency defect between two resets written for the same event, and the
guard needed for the minimal variant is already maintained.

For the cleaner variant (option 1 in §13.3/N1) there are **two** user-seek entry points,
and missing the second is the main implementation trap: in-app seeks arrive as
`PlaybackControl.SEEK_TO` (`PlaybackViewModel.seekTo` →
`MediaSessionPlaybackProvider.seekTo` → `handlePlaybackControl`, `:4552-4557`), while
external seeks (notification, lock screen, Android Auto, AVRCP, Cast) are handled by the
`MediaSession` itself, which calls `player.seekTo` directly because
`MediaSessionCallback` does not override `onSeekTo` and the session is built on
`exoPlayer` (`MediaModule.kt:108`). The fix must cover both, or a user seek from the
notification will no longer clear the circuit.

If the flag guard (fallback option 2) is used instead, its safety rests on dispatch
order, which was **verified rather than assumed**: ExoPlayer 1.10.1 queues listener
events in `ExoPlayerImpl.updatePlaybackInfo` as `onTimelineChanged` →
`onPositionDiscontinuity` → `onMediaItemTransition` (extracted with `javap -v` from
`media3-exoplayer-1.10.1.aar`: bootstrap entries #18/#19/#20 resolve to
`lambda$updatePlaybackInfo$17/18/19`, targeting those three callbacks, queued at
bytecode offsets 327 < 371 < 397). The flag is still `true` while the discontinuity
callback runs and is cleared immediately afterwards by the transition callback. That is
a media3 implementation detail, so the fallback needs a comment naming it — which is
why it is not the first choice.

Either way, the existing pure-state test suite (`SourceErrorCircuitTest`, 6 cases) cannot
catch this class of bug; the fix must ship with a `MediaService`-level test that drives
`error → skip → error → skip → error`.

**C, precisely** (the table hides these choices):

- Counts **consecutive `SKIP_NEXT` outcomes only** (the `reachable=true` branch).
  `CACHED_JUMP` and `STOP_NO_CACHED` are *not* counted, so an offline cache-hop chain
  behaves exactly as today and does not interact with the circuit.
- Threshold **8** is the existing house value for "give up on a failing drain"
  (`TRACK_CONSECUTIVE_FAIL_SOFT_STOP`, `MetadataSyncWorker.kt:217`), not a UX-researched
  number. 3 (ADR-0111) remains the threshold for errors that are *not* auto-skipped.
- On trip: **pause** at the failing item (`stop()` would discard index/position), show a
  resumable banner ("N tracks failed to play — tap to resume") and emit
  `autoSkip burst summary n=… fromIdx=… toIdx=… evicted=…` (this is N2's line).
- Resume = **user tap only** (`seekToNextMediaItem()` + counter reset). No timed
  auto-resume: that would re-create the 58-skip burst at a slower rate.

**Decision needed from the maintainer:** A, B, or C (and the threshold for C). Until then
N1 stays P1 and unimplemented. P0 escalation condition unchanged (§13.7/C2).

#### 13.8.2 What the A1 confound does and does not touch

A1 (`lastDeltaSyncMs` moved backwards between captures) is unresolved and must be read as
"this prefs file may not be continuous with the previous one". It does **not** move any
finding's mechanism, because those were re-derived from the code:

| Finding | Depends on the stale stamps? | Status |
|---|---|---|
| F1 (WorkManager dead) | no — no `Configuration.Provider`, no worker factory | stands |
| F2 (double prefetch) | no — `inserted`/`dedup` pairs are intra-window | stands |
| F3 (two writers) | no — two samples, same shape | stands |
| N3 (write-fail opaque) | no | stands |
| N4 (soft-stop cause-blind) | no — `phase=complete` after a soft-stop is source-verified | stands |
| N5 (header cannot show a soft-stopped run) | **yes for the number, no for the mechanism** | mechanism stands, evidence marked confounded |
| N8 (reachability flaps) | no | stands |

The "29 d / 11 d / 22 missed windows" arithmetic in §13.2 is annotated as
per-install and must not be quoted as a measurement until A1 is closed.

#### 13.8.3 Revised plan (supersedes §13.5's ordering; itself superseded by §14.7)

> Kept for the audit trail. §14.7 reorders this list after the deletion hypothesis:
> N11-1 + N3 → N4/N5 → N8 → one capture → N11-2 (N11-3 only if approved) → N1-C →
> N2/N6/N7/N9.
> The only substantive change is that **N1-C moves behind the classification**, because
> a burst caused by one deleted album should be collapsed by N11-2, not paused in
> groups of eight.

1. **N3** — write-fail reason + retry-exhaustion terminal line (observability).
2. **N4** — explicit soft-stop cause line *before* `phase=`, and stop calling a
   soft-stopped run "complete".
3. **N5** — `lastSyncAttemptMs` / `lastSyncOutcome` / `softStopCount` in the header
   (display only; ADR-0068 semantics untouched).
4. **N8** — one owner for the reachability boolean, with hysteresis; keep it out of
   single-request error paths.
5. **N1** — only after §13.8.1 is decided; ships with the integration test and the
   aggregate/bounded behaviour of the chosen option.
6. **N2, N6, N7, N9** — ride along: burst summary + `removeCached hadSpan`; `corpus=`
   vs `fetched=`; process-boundary lines; stamp inside the lock.
7. **A1** — evidence only (prefs mtime, `firstInstallTime`, `last_full_sync_ms`); do not
   code around it.

Exit criteria per item are unchanged: `:app:assembleDebug`, `:app:testDebugUnitTest`,
`ktlintCheck`, and a post-fix capture that shows the new line.

#### 13.8.4 Cross-model challenge of this re-validation

See §13.9 — the re-validated plan was challenged separately, because §13.7 only covers
the first-pass findings.

### 13.9 Cross-model challenge of §13.8 (2026-11-01)

A second hostile review was run on the re-validated plan (not on the findings), because
§13.7 predates §13.8. Its verdict was "reject until …"; that verdict is **not** accepted
as a whole — several of its items were generic (races, "no UX research") and one was
self-contradictory. What matters is which items survive contact with the code.

| Challenge | Verdict |
|---|---|
| C9 "the elimination proof misses service/player recreation, ExoPlayer rebuild, concurrent `onSourceError` and a stale `onSuccessfulPlay`" | **Rejected — and the proof was made explicit.** A rebuild inside the 3.95 s burst would emit `route=`/restore lines (absent) and would leave a *fresh* counter, which still trips at 3 within 15 s. `Player.Listener` callbacks are main-looper-serialized, so `onPlayerError` cannot race itself. The poller needs `isPlaying` **and** position ≥ 2 000 ms, impossible 68 ms after a seek-to-0. The four exclusions are now enumerated in §13.3/N1 so the next reviewer does not have to re-derive them. |
| C10 "option 1 is incomplete: notification / lock screen / Android Auto / AVRCP / Cast seeks never reach `PlaybackControl`" | **Accepted — material.** The session is built on `exoPlayer` (`MediaModule.kt:108`) and `MediaSessionCallback` does not override `onSeekTo`, so external seeks go straight to `player.seekTo`. Deleting the discontinuity reset without adding that hook would make the circuit *trip on a user seek from the notification*. Option 1 in §13.3/N1 now names both entry points (`SEEK_TO` + `onSeekTo` override). |
| C11 "`onSuccessfulPlay` can fire on a stale position after a skip" | **Rejected.** The poller reads `currentPosition` of the item that is playing; after a hop the position restarts at 0, so the 2 s gate cannot be met until the new track has actually played 2 s — which is the intended reset condition. |
| C12 "product option C is under-specified: threshold, meaning of 'resumable', conflict with `serverUnreachable` and `STOP_NO_CACHED`, double counting with option 3" | **Accepted — §13.8.1 gained a "C, precisely" block.** Threshold 8 is a house precedent (not UX research) and is stated as such; only `SKIP_NEXT` counts, so `CACHED_JUMP`/`STOP_NO_CACHED` and the offline path are untouched; trip = pause (not stop) + user-tap resume, no timed auto-resume; option 3 was **withdrawn** as double counting and replaced by a `circuit clear src=` diagnostic line. |
| C13 "N8 is over-engineered: no evidence reachability flaps too often" | **Rejected.** §13.3/N8 documents two sub-second `unreachable`→`reachable` pairs (`12:35:58.956`, `13:22:29.040`) and shows the boolean selects `CACHED_JUMP` vs `SKIP_NEXT`. The item stays P2 with its "decide the owner first" gate; it is not a speculative hardening. |
| C14 "N3/N4/N5 are safe but 'insufficient' because they do not fix the root cause" | **Noted, no change.** They are observability items by design and are ranked *before* N1 precisely because the next capture needs them; N1 is ranked after its product decision. "Insufficient" is not a defect of an observability fix. |
| C15 "A1 is speculation / unverifiable" | **Noted, no change.** A1 is already labelled as unresolved and evidence-only ("do not fix anything here"). The only claim made is that a *monotonic* writer cannot produce a backwards value — that is a contradiction in the artifact, not a hypothesis about the cause, and the two candidate causes are listed as candidates. |

Net effect of the second challenge: one material correctness catch on the N1 fix
(external seek entry point), one design catch (double counting / under-specified option C),
both applied; no finding withdrawn; no ranking changed. The plan in §13.8.3 stands as
amended.

---

## 14. Amendment — a second cause for the 12:32 burst: content removed on the server (2026-11-01)

§13 established *how* the burst spread (the circuit reset on a programmatic seek) and treated
the 58 errors as item-level failures of unspecified origin. It did not establish **why each
item failed**. This amendment examines the competing explanation — the tracks no longer exist
on the server — and finds that (a) it is at least as likely as cache corruption for *this*
burst, (b) the artifact cannot distinguish the two, and (c) nothing in the app currently
protects against it. A new finding, **N11**, records the gap.

### 14.1 Hypotheses

| # | Hypothesis | What must be true | Signature |
|---|---|---|---|
| **H1** | The 58 queue entries no longer exist on the server (album removed, or tracks removed from a playlist/album); they were not cached, so playback asked the server and got 404 | stale local rows + cache miss | contiguous block; HTTP 404/410; `errorCode=2004`/`2005` |
| **H2** | The 58 cached spans are corrupt (zero-length, truncated, garbage) | all 58 were cached *and* unreadable/unparseable | `errorCode=3003`/`4003`, or `2005` |
| **H3** | The network path failed for those items (DNS/route/server restart) | server up elsewhere in the same minute | `2001`/`2002`; usually not item-selective |
| **H4** | The circuit/auto-skip mechanics (§13/N1) | — | orthogonal: explains *propagation*, not *cause* |

H4 is settled and independent: whatever the cause, the recovery loop converted each failure
into a silent skip. H1/H2/H3 are the open question.

### 14.2 Why H1 is now the leading hypothesis for this burst

**1. The data-source stack makes a pure cache-corruption story unlikely.**
`MediaService.kt:1685-1700` builds playback around a cache-first stack:

```
CacheDataSource (key = streamCacheKey(uri) = the `id=` query = trackId)
  ├─ cache write: CacheDataSink, 1 MB fragments
  ├─ cache read
  └─ upstream: OfflineAwareHttpDataSource → DefaultHttpDataSource
     flags: FLAG_IGNORE_CACHE_ON_ERROR
```

Three consequences matter:

- a **fully cached** track is served from disk and never reaches HTTP, so a server-side
  removal cannot produce an error for it;
- a **partially cached** track (interrupted prefetch, eviction mid-span, a prior failed
  download) reads its prefix from disk and fetches the missing range upstream. A removal
  can therefore fail a track that *does* have a cache entry — so `cached_path`/`cached_size`
  being non-null does not by itself exonerate H1. §14.5's decisive check is "no playable
  span", not "no row";
- `FLAG_IGNORE_CACHE_ON_ERROR` means a **cache read error is retried upstream**, so a corrupt
  span normally self-heals instead of surfacing as a final error;
- therefore a *final* source error means (a) the upstream was asked and failed, (b) a payload
  was delivered but could not be parsed/decoded, or (c) — rare — a cache **write** failed
  inside `CacheDataSink` (`FLAG_IGNORE_CACHE_ON_ERROR` covers reads, not writes). Case (c) is
  a local disk problem, not a deleted track; without the cause chain it is indistinguishable
  from (a)/(b).

So 58 consecutive errors are much more consistent with "58 upstream failures" (H1/H3) than
with "58 corrupt cached files" (H2). H2 survives only in the narrow form "the span reads
without I/O error but contains non-audio bytes" — possible, but it would have to hit 58
contiguous entries. The exception is the partial-span case above, which is a *mix* of the
two: a local incomplete cache plus a remote failure.

**2. The queue is built from the local DB, and the local DB does not prune deletions.**
This is the structural half of the argument:

| Path | Deletion handling | Runs when |
|---|---|---|
| `DELTA` sync | `metadataDao.upsertAlbumsPreserveEnrich(...)` — **upsert only**, by design (`MetadataSyncWorker.kt:700-707`). The list source is `getAlbumList2?type=newest`, which cannot see removals at all. | whenever a sync runs |
| `FULL` sync | `replaceAlbumsDiffPreserveEnrich(uniqueAlbums)` → album ledger → `deleteOrphanedAlbumTracks()` (`:721-740`) — **the only place albums/tracks are deleted** | weekly cadence (`FULL_SYNC_INTERVAL_MS = 7 d`, `:184`), and per F1 not scheduled on this install |
| Album detail open | `if (tracks.isNotEmpty()) replaceAlbumTracks(...)` (`AlbumDetailViewModel.kt:256`). A deleted album returns no tracks → the guard skips the write → **stale rows survive**. The screen looks empty; the DB still has them. | user opens the album |
| Imported playlist refresh | `playlistDao.clearEntries(...)` + reinsert (`PlaylistRepository.kt:153,296`) — prunes correctly | when that refresh runs (part of sync) |
| Track removed from a surviving album | album re-fetch → `replaceAlbumTracksBatch` clears and reinserts per album (`Daos.kt:1615-1622`); `songCount`/`duration` drift already marks the album changed (`MetadataSyncWorker.kt:665-690`) | when that album is re-fetched |

Net effect: after a server-side deletion, the stale album survives in `cached_albums` /
`cached_album_tracks` / `tracks` until a FULL sync runs. Every queue built from Home, an
artist page, a genre or a playlist can therefore contain content that is gone, and with F1
the window is unbounded rather than seven days.

**3. The burst is contiguous.** 58 failures at `idx 0…57` of a queue set 69 ms earlier
(`12:32:41.156 transition reason=3 idx=0 count=755`), then playback resumed at `idx=58`.
A deleted album (or a deleted block of an imported playlist) produces exactly that shape.
Random corruption across 58 queue entries does not. **Caveat:** the queue's `src=` was not
recorded on build 26 (F2's `enqueue-pass src=` now does), so we cannot tell whether the queue
was album-ordered or shuffled; if it was shuffled, index contiguity says nothing about album
contiguity and this argument weakens.

**4. The 59th item played.** So this was not a server-wide outage or a dead route: the very
next request succeeded. Per-item failure is the signature of removed content (or of
per-file unavailability), not of a network fault. Note that the `reachable=true` field on the
skip lines is **not** independent evidence for this: ExoPlayer's stream path uses
`DefaultHttpDataSource` (`OfflineAwareDataSource.kt:44-52`), **not** the OkHttp
`BaseUrlInterceptor`, so stream responses never touch the reachability boolean. It is `true`
because unrelated Retrofit/cover-art calls succeeded — and `onApiSuccess()` treats *any* HTTP
response, including a 404, as success (N8). The 59th item actually playing is the evidence.

**5. Timing is compatible, not decisive.** 3.95 s / 58 items = 68 ms per item, including
`removeCached`, seek, prepare and the HTTP attempt. That is fast for a WAN 404 but normal for
a LAN/home server, and it is also fast for an instant local rejection. This number does not
separate H1 from H2; it only rules out slow timeouts.

### 14.3 What the artifact can and cannot prove

`MediaService.kt:1351-1357` logs `"player error: ${error.message}"` and passes the exception
to `DiagnosticLog`, whose suffix prints **only the top-level exception's class and message**
(`DiagnosticLog.kt:129-135`). Two facts then make the export undetermined:

- media3's message for a source failure is a **fixed literal**. In
  `media3-exoplayer-1.10.1.aar`, `ExoPlaybackException.createForSource(IOException, errorCode)`
  builds the message through `deriveMessage(...)`; the string `Source error` is a constant in
  that class. So `player error: Source error` is the same line for a 404, a timeout, a
  zero-length file and a decoder failure.
- the discriminators are present on the exception but are dropped. `PlaybackException` carries
  an `errorCode` (values verified from `media3-common-1.10.1.aar`) and the original
  `IOException` as `cause`:

| `errorCode` | Name | Interpretation here |
|---|---|---|
| `2000` | `IO_UNSPECIFIED` | unknown I/O |
| `2001` | `IO_NETWORK_CONNECTION_FAILED` | connect/DNS failure → H3 |
| `2002` | `IO_NETWORK_CONNECTION_TIMEOUT` | timeout → H3 |
| `2003` | `IO_INVALID_HTTP_CONTENT_TYPE` | HTML/XML error body served as audio → **H1** (Subsonic error page) |
| `2004` | `IO_BAD_HTTP_STATUS` | 404/410 → **H1**; 5xx → server fault |
| `2005` | `IO_FILE_NOT_FOUND` | local file missing → **H2** |
| `2006` | `IO_NO_PERMISSION` | local permission → H2 |
| `2007` | `IO_CLEARTEXT_NOT_PERMITTED` | configuration |
| `2008` | `IO_READ_POSITION_OUT_OF_RANGE` | truncated span → **H2** |
| `3003` | `PARSING_CONTAINER_UNSUPPORTED` | XML body **or** garbage span → H1/H2 |
| `4003` | `DECODING_FAILED` | garbage payload → **H2** |

`2006` (`NO_PERMISSION`) and `2007` (`CLEARTEXT_NOT_PERMITTED`) are local/configuration
faults: neither eviction nor skipping cures them, and they should never be treated as
"remove the cached file".

media3 derives `errorCode` from the cause, but the cause's class is not a complete
discriminator. `2004` carries `HttpDataSource.InvalidResponseCodeException`
(`responseCode` is the answer); `2003` carries `InvalidContentTypeException`; `2005`
carries `FileNotFoundException` — an HTTP 404 does **not** produce `2005`, it produces
`2004`. `3003` is the ambiguous one: its cause is a `ParserException`, which can come
either from a corrupt local span or from an HTTP **200** whose body is an XML/HTML error
page. Class alone cannot separate those two; the cache state can (no span at all ⇒ the bytes
came from upstream; an unplayable span ⇒ the bytes were local — see N11-2's `SpanState`).
None of this is logged today.

**Conclusion:** §13's mechanism claims are unaffected, but the *cause* of the 58 errors is
**not established** by either capture. Any statement that the burst "was" cache corruption is
unsupported.

### 14.4 N11 (P1) — content removed on the server is indistinguishable from local corruption, and nothing reconciles it

**Evidence.**

- 58 consecutive `autoSkip action=SKIP_NEXT` lines at `12:32:41.225-12:32:45.179` on a
  755-track queue. Every skip is produced by an `onPlayerError`, and that line is the
  indistinguishable literal `player error: Source error` (quoted at the head of the burst;
  `DiagnosticLog` has no de-duplication). Each skip is followed by `removeCached …
  result=ok` and a transition to the next index (details in §13.3/N1).
- The export contains **no** `errorCode`, no `cause`, no HTTP status — §14.3.
- The local library cannot have known about a deletion: the only automatic sync path on this
  build is the Home-id-change DELTA (§13.2/F1), which is upsert-only.
- A deleted album is still fully present in the local tables until a FULL drain
  (`MetadataSyncWorker.kt:721-740`), so a queue can be built from it at any time.
- The user reports the server did have tracks removed around this period. That is context,
  not proof, but it is consistent with every observable detail.

**Mechanism (open on HEAD).**

1. A queue is built from local `tracks` rows (`PlaybackManager.enqueuePlayQueue`), including
   rows whose albums no longer exist server-side (§14.2/2).
2. The entries are not cached (they would have played from disk otherwise), so ExoPlayer opens
   `OfflineAwareHttpDataSource` → `GET /rest/stream?id=<trackId>&…`.
3. The server answers 404 (Navidrome for a missing file) or a 200 with a Subsonic error body.
4. media3 reports a source-type `PlaybackException` with `errorCode` 2004/2005/2003/3003; the
   message is the literal `Source error`.
5. When the server is reachable — as in this burst, where every `autoSkip` line carries
   `reachable=true` — `onPlayerError` (`:1351-1461`) treats it exactly like corruption:
   `removeCached(trackId)` (a no-op when nothing is cached, but it also nulls
   `cachedFilePath`/`cacheSizeBytes` and deletes the waveform when a span does exist —
   `CacheService.kt:532-585`), then `seekToNextMediaItem()`. When reachability is false the
   branch is `CACHED_JUMP`/`STOP_NO_CACHED` instead, and `removeCached` is skipped
   (`:1383-1395`).
6. The stale rows are never removed; the persisted queue still contains them; the next replay
   reproduces the same run. With the circuit neutralised (N1) nothing bounds it.

**Why P1 — conditional on H1.** Not because it crashes, but because it is unrecoverable,
repeatable noise: a removed album can cost the user 58 silent skips on every play attempt,
indefinitely, and the app learns nothing. Under F1 there is no scheduled FULL drain to heal
it, so "indefinitely" is the default. If §14.5's checks instead confirm H2 (local corruption),
the cause-classification half of N11 stays useful but the reconciliation half is not a P1:
the severity drops to P2/diagnostics, and N3's `reason=` line becomes the priority. This is
the same falsifier recorded at the end of this section, restated here so the priority is not
read as unconditional.

**Distinct from the other findings.**

| Finding | Covers |
|---|---|
| N1 | the *propagation* (circuit neutralised) |
| N3 | the *write* path (prefetch/download failures) |
| N5 | the *display* of sync freshness |
| **N11** | the *cause and reconciliation* of missing content |

**N11 sub-items.**

| # | Gap |
|---|---|
| **N11a** | The error is unclassifiable in the export: no `errorCode`, no cause chain, no `trackId` on the error line (the trackId appears on the following `autoSkip` line only) |
| **N11b** | A `REMOTE_MISSING` failure is handled as `SKIP_NEXT` one item at a time; album-level identity is available (`mediaMetadata.extras["albumId"]`, written in `QueueManager.kt:71`) but unused |
| **N11c** | Nothing marks or reconciles a missing track/album: no `getSong`/`getAlbum` verification, no `missingAt` column, no purge, no queue repair |
| **N11d** | `removeCached` is called for failures that are not local (`:1391-1395`, guarded only by `!serverUnreachable`). For an uncached track it is a no-op, but it destroys the DB cache pointers and the waveform for any partial span, and the `result=ok` line cannot be told from a no-op (N2) |
| **N11e** | Even the on-demand path does not prune: a deleted album opened in `AlbumDetailViewModel` shows empty but keeps its rows (§14.2/2) |

**What would falsify N11 (or demote it to P2).** Any one of:

- the 58 `trackId=` values from the burst map to many different albums **and** every one has a
  complete, playable span (`AudioCacheValidation.PLAYABLE`, not merely a non-null
  `cached_path`) → H2 (local corruption) is the explanation, and N11's severity drops to
  "diagnostics only"; a non-null-but-partial span does not falsify H1;
- a post-fix capture shows `code=4003`/`3003` with a `ParserException` cause for the burst →
  local decode failure;
- a post-fix capture shows `code=2001/2002` for the burst → H3;
- the server confirms all 58 ids still exist and stream fine → H1 is dead.

### 14.5 Decisive checks available now (no new build)

The export already contains the candidate ids on the `autoSkip` lines
(`autoSkip action=SKIP_NEXT trackId=<id> idx=<n> count=755 errorCount=1 reachable=true`).

1. **Album grouping.** Take the 58 ids and count distinct album ids, either from the device DB
   or with two server calls:

   ```sql
   -- pull the DB first (debug build):
   --   adb exec-out run-as com.lucasdss.ftpmusic.app cat databases/ftpmusic.db > ftpmusic.db
   SELECT id, album_id, cached_path, is_downloaded
     FROM tracks
    WHERE id IN ('<id1>', '<id2>', …);
   ```

   or, per id, `GET /rest/getSong?id=<id>&u=…&t=…&s=…&v=1.16.1&c=ftpmusic&f=json`.

2. **Cache state.** In the same query: `cached_path IS NULL` for all 58 ⇒ they were never
   cached ⇒ the error came from the network path ⇒ H1/H3, not H2. The converse does **not**
   hold: a non-null `cached_path` can be a partial span that still reads its tail upstream
   (§14.2/1), so it cannot by itself prove H2. For that, check the span is complete/playable
   (`AudioCacheValidation`) or use the post-fix `code=` line.

3. **Server existence.** `getSong` returns `status=failed` / error 70 ("not found") for a
   removed id; `getAlbum?id=<albumId>` returning an error for the whole album confirms an
   album-level removal rather than a track-level one.

| Observation | Conclusion |
|---|---|
| one/two album ids; no playable span for any id; `getSong` fails | **H1 confirmed** — proceed with N11-2/3 |
| many album ids; every id has a **playable, complete** span | H2 — N11 stays diagnostics-only; prioritise N3's reason line |
| many album ids; some spans are partial/absent | **Inconclusive** — a partial span still fetches upstream (H1/H2 mix); use the post-fix `code=` line |
| `getSong` succeeds for all | H1 dead; look at H3 (and at the server logs for those requests) |

### 14.6 Fix plan for N11

**N11-1 — make the failure classifiable (no behaviour change).**

- `MediaService.kt:1351-1357`: log
  `player error: ${error.message} code=${error.errorCode} name=${error.errorCodeName} cause=${error.cause?.javaClass?.simpleName}:${error.cause?.message} idx=${ep.currentMediaItemIndex} trackId=$currentId`.
  `currentId` is currently read *after* the log call (`:1366`), so it must move above it.
  `errorCodeName` is public in media3 1.10.1 (`PlaybackException.getErrorCodeName()`), so the
  line is readable without the §14.3 table. Scrub URLs (the existing `DiagnosticLog.sanitize`
  already strips `https?://\S+`).
- `DiagnosticLog.kt:129-135`: include one level of `cause` in the suffix (bounded to ~120
  chars) instead of only the top-level exception. This benefits every call site, not just
  playback.
- Optionally log the HTTP status directly in `OfflineAwareHttpDataSource`/a small
  `EventListener`: `stream http trackId=… status=404` — the only place the status is known
  without parsing the cause. Media3's `InvalidResponseCodeException.responseCode` already
  carries it, so the cause-chain fix is sufficient; prefer it (fewer moving parts).

**N11-2 — classify, and do not act destructively on non-local failures.**

- Add `enum class SourceErrorKind { LOCAL_FAULT, REMOTE_MISSING, NETWORK, UNKNOWN }` and a
  pure mapper
  `classifySourceError(errorCode: Int, responseCode: Int?, cause: Throwable?, span: SpanState): SourceErrorKind`
  (unit-testable, no Android deps; `responseCode` is read from
  `HttpDataSource.InvalidResponseCodeException` when present).
  `SpanState` must be **three-valued**, not a boolean:
  `enum class SpanState { NONE, PLAYABLE, UNPLAYABLE }`, exposed as a new non-destructive
  `CacheService.spanState(trackId)` that uses the existing private `getSpanFile`
  (`CacheService.kt:414`) plus `AudioCacheValidation.classify` (`AudioCacheValidation.kt:39`).
  `hasPlayableSpan` (`:333`) collapses POISON/UNKNOWN into `false`, so it cannot be reused as
  is. The distinction matters: `NONE` means the unparseable bytes came from upstream (a 200
  error body), while `UNPLAYABLE` means there *is* a local span that is corrupt and should be
  evicted. A boolean would misclassify a poison span as remote and never heal it.
- Mapping (fixed after checking media3's error-code derivation):

  | Signal | Kind | Action |
  |---|---|---|
  | `2004`, HTTP 404/410 | `REMOTE_MISSING` | no `removeCached`; album short-circuit |
  | `2004`, HTTP 401/403 | `UNKNOWN` | no evict, no album skip; surface the banner (auth/config, not deletion) |
  | `2004`, HTTP 429/5xx | `NETWORK` | no evict; circuit path |
  | `2003` (`InvalidContentTypeException`) | `REMOTE_MISSING` | non-audio body from the server |
  | `2005` (`FileNotFoundException`) | `LOCAL_FAULT` | HTTP 404 is `2004`, not `2005`; eviction is appropriate |
  | `2001`/`2002` | `NETWORK` | no evict |
  | `2006`/`2007` | `UNKNOWN` | local/config; eviction cannot help |
  | `2008` | `UNPLAYABLE` ⇒ `LOCAL_FAULT`; `NONE` ⇒ `REMOTE_MISSING`; `PLAYABLE` ⇒ `UNKNOWN` | truncated span vs. server ignoring range |
  | `3003`/`4003` | `UNPLAYABLE` ⇒ `LOCAL_FAULT`; `NONE` ⇒ `REMOTE_MISSING`; `PLAYABLE` ⇒ `UNKNOWN` | see tie-breaker above |
  | `2000` | cause-based, else `UNKNOWN` | conservative: no evict, no album skip |

  Note: a `2005` on a row with `is_downloaded=1` hits `removeCached`'s pinned guard
  (`CacheService.kt:537-557`) and is **not** evicted; the stale row keeps failing. That is a
  download-integrity gap outside N11, but the next capture will label it
  `removeCached … result=refused_pinned`, which makes it visible.

- In `onPlayerError`:
  - `REMOTE_MISSING`: **do not** call `removeCached`; log
    `autoSkip action=SKIP_MISSING trackId=… albumId=… code=… http=…`.
  - `LOCAL_FAULT`: keep `removeCached` (the only case where deletion is correct).
  - `NETWORK` / `UNKNOWN`: keep the circuit path; do not evict.
- **Threading.** `onPlayerError` runs on the main looper (`Player.Listener`). Only the pure
  part of `classifySourceError` (errorCode/responseCode/cause) may run there. The `spanState`
  probe (it reads the span file — `AudioCacheValidation.classify`), `removeCached` and the
  album-jump index computation belong in the **existing** `scope.launch { … }`
  (`Dispatchers.IO`, `MediaService.kt:831-836`), which then returns to `Dispatchers.Main` for
  the seek. Do not put a file read on the error path's main thread.
- Album-level short-circuit — **only after the second same-album failure**, never on the
  first. Read `albumId` from
  `ep.currentMediaItem?.mediaMetadata?.extras?.getString("albumId")`. On the first
  `REMOTE_MISSING` for an album, take the ordinary one-item `SKIP_NEXT` and remember
  `(albumId, nowMs)`. If a second `REMOTE_MISSING` for the same album arrives within
  `MISSING_ALBUM_WINDOW_MS` (30 s) **and within `MISSING_ALBUM_MAX_GAP` (2) queue positions**
  — i.e. the next attempted items are also failing, not two unrelated one-offs later in the
  album — advance **in one step** to the first index whose
  `albumId` differs with `ep.seekTo(targetIdx, 0L)` (the same primitive as `CACHED_JUMP`,
  not a loop of `seekToNextMediaItem()` — a loop would emit one transition per skipped item
  and defeat the purpose). The index comes from a pure helper, testable:
  `findNextAlbumIndex(albums: List<String?>, fromIndex: Int): Int` (returns
  `albums.size` when the run reaches the end; the caller then stops rather than seeking past
  the queue); log
  `autoSkip action=SKIP_MISSING_ALBUM albumId=… fromIdx=… toIdx=… n=<skipped> after=2`.
  Requiring the second failure *on an adjacent position* is what makes this safe: a single
  transient 404 in an intact album — or two unrelated bad tracks five positions apart — does
  not skip the rest of that album. The streak is cleared on a successful
  `onMediaItemTransition` that is **not** an error skip, on verified play ≥ 2 s, on user seek,
  on queue set and on circuit reset. The error-skip exclusion matters: the transition emitted
  by the first auto-skip must not clear the streak we are trying to build. Two constraints on
  that hook: (a) read `PlayerHolder.playbackErrorAutoSkipInFlight` **before** the existing
  ADR-0095 block (`MediaService.kt:979-984`) clears it on the transition, i.e. capture it at
  the top of `onMediaItemTransition`; (b) the streak must be its own field — `playerErrorCount`
  is zeroed on every transition (`:978`) and is always `1` in this burst's `autoSkip` lines,
  which is why burst detection lives in `sourceErrorCircuit`, not in that counter.
  - `albumId == null` (Cast-created items, or a path that never wrote the extra) → always
    fall back to one-item `SKIP_NEXT`; a null must never be a group key, or unrelated items
    would be skipped together.
  - `findNextAlbumIndex` only ever skips **consecutive** entries with the same `albumId`.
    In a shuffled queue that is usually one item (the next entry has a different album), so
    it can never skip a different album; it also means a scattered album is not fully
    removed in one step — the remaining copies each need their own two failures.
  - multi-disc albums share one `albumId`, and all discs belong to the same album, so
    skipping them together is correct **once the album is confirmed gone by the second
    failure**.

  For a removed album this turns 58 errors into **two** failures + one album transition
  (2 for N1's counter, not 58). Track-level removals inside a surviving album are not
  album-skipped — they are bounded by N1-C's pause instead.

- **Tests.** `classifySourceError`: 404/410 ⇒ `REMOTE_MISSING`, 401/403 ⇒ `UNKNOWN`,
  429/5xx ⇒ `NETWORK`, `2005` ⇒ `LOCAL_FAULT`, `2003` ⇒ `REMOTE_MISSING`, `3003` with
  `UNPLAYABLE` ⇒ `LOCAL_FAULT`, with `NONE` ⇒ `REMOTE_MISSING`, with `PLAYABLE` ⇒
  `UNKNOWN`, `2001`/`2002` ⇒ `NETWORK`. `findNextAlbumIndex`: contiguous block, scattered
  ids, null at the head, all-same to the end, empty list. Second-failure rule: one failure ⇒
  no jump; two same-album failures at adjacent positions within 30 s ⇒ jump; two different
  albums ⇒ no jump; two same-album failures more than 2 positions apart ⇒ no jump; same album
  after the window ⇒ no jump; a successful transition between them ⇒ no jump. `SpanState`:
  absent file, poison file, playable file.

**N11-3 — reconcile (policy decision required).**

On the **second** same-album `REMOTE_MISSING` (the same trigger as N11-2's album jump — not
the first, so a single transient 404 never causes a network call or a persistent change),
one confirmation call. Use the same authenticated path as album detail
(`repository.getAlbum(albumId, username, password)`, `Repositories.kt:20`), not an
unauthenticated URL; an auth/network failure is **not** confirmation and must not purge.

- album gone → **only if the album is purgeable**:
  `trackDao.countDownloadedTracksInAlbum(albumId) == 0` (new one-line DAO query:
  `SELECT COUNT(*) FROM tracks WHERE album_id = :albumId AND is_downloaded = 1`) **and**
  the **ledger** row (`albums` table / `AlbumEntity`) is not
  starred/disliked/pending-unstar (the same intent-preserving rule as `pruneAlbumLedger`,
  `Daos.kt:1538`). This must read the ledger, not `CachedAlbumEntity`: `cached_albums` has
  no `starred_at`/`is_disliked`/`pending_unstar_at` columns. `deleteAlbumsByIds` itself
  performs no such check, so the guard must be at the call site. Then delete the album row
  with the existing `deleteAlbumsByIds(listOf(albumId))` (`Daos.kt:913-914`), then
  `deleteOrphanedAlbumTracks()` (`:1624`) and `pruneAlbumLedger()` (`:1538`), then
  `reconcileSearchCorpusAgainstCatalog()` (`:233-246`, ADR-0108) to drop densified `tracks`
  ghosts. That last query deliberately preserves local-weight rows (downloaded, cached,
  starred, disliked, played) — which is exactly why the purge must be **skipped** for an
  album that still has a downloaded track, or the user would lose the album from the
  library while the files remain on disk. For such an album use policy B (mark/hide).
  Finally remove the album's track ids from `DualQueueManager` and persist the pruned
  snapshot through `QueuePersistenceManager`/`QueueJournalDao`, so a restore does not
  reinsert them; log
  `reconcile albumId=… removed=true albumTracks=… corpus=… queueEntries=… cachedKept=…`;
- album present → mark the track only (`missing_tracks` table or a `TrackEntity.missingAt`
  column), and let the album re-fetch (`changedAlbumIds` path) correct the track list on the
  next sync;
- never purge on a single unconfirmed failure — a 404 can also come from a partially
  misconfigured server or a broken stream route.

Policy options for the user-facing part:

| Option | Behaviour | Trade-off |
|---|---|---|
| A. silent purge | rows disappear from the library immediately | simplest; surprising if the server is temporarily misconfigured |
| B. mark + hide, purge on FULL | show/hide with a "removed from server" marker; DB purge deferred | honest, reversible; needs a column + UI affordance |
| C. skip at play time only | keep rows, skip the album, no persistence | zero risk, but the failure repeats on every replay |

Recommendation: **B** for confirmed album removals, **C** for unconfirmed single tracks. B is
the only option that stops the repeat without risking user-visible loss during a server
maintenance window.

**N11-4 — keep deletion detection out of DELTA.** `getAlbumList2?type=newest` cannot see
deletions; making DELTA walk the full catalogue would turn every 12 h sync into a FULL.
Deletion detection stays FULL's job, plus the targeted per-album verification above.

### 14.7 Interaction with N1 option C, and revised sequencing

If the burst was H1, **pausing after 8 is the wrong first response**: the user taps resume,
gets 8 more, taps again — roughly seven resumes (58 failures / 8) to cross one deleted album,
with a banner every time. The N11-2 album short-circuit must run **before** the burst
counter, so one album costs two failures and one transition (the second failure triggers the
album jump). It requires the `albumId` extra; when that is absent (see N11-2's null guard),
the old one-by-one path is the fallback and option C's bound is the only protection left.
Option C remains the correct backstop for genuinely mixed failure runs (some items bad, some
network blips), and it is also what bounds a block of track-level removals inside an album
that still exists.

Revised execution order (supersedes §13.5 and §13.8.3):

| # | Item | Why now |
|---|---|---|
| 1 | **N11-1** errorCode/cause logging + **N3** write-fail reason | both are pure diagnostics and they decide H1/H2/H3 for the next capture |
| 2 | N4 soft-stop cause line, N5 header freshness | observability; unchanged from §13.8.3 |
| 3 | N8 reachability ownership | branch correctness; also makes N11-2's "reachable" evidence trustworthy |
| 4 | **one capture**, then classify the burst (§14.5) | no threshold or purge decision before this |
| 5 | N11-2 (classification + album skip) | behaviour; gated on the classification above. N11-3 (reconcile/policy B) is a **separate decision** after this, not part of the same change |
| 6 | N1 option C (bounded burst + resume) | backstop; threshold chosen after step 4 |
| 7 | N2/N6/N7/N9 one-liners | ride along |

### 14.8 Open questions recorded for the next session

- Was the queue album-ordered? (Post-fix `enqueue-pass src=` + `albumId` in the log answer it.)
- Does Navidrome answer a missing track with 404 or 200+error body? (Determines whether the
  first capture shows 2004 or 2003; both are `REMOTE_MISSING`.)
- If an album is removed, should the persisted queue drop it silently or show a tombstone?
- Do removed tracks remain in imported playlists after a refresh? (Refresh prunes, but it only
  runs with sync — another F1 dependency.)

### 14.9 Cross-model challenge of §14–§15 (2026-11-01)

Mistral reviewed §13.3/N1, §14 and §15 against the code and media3 1.10.1. Every challenge was
re-checked here; the table records what changed and what was rejected.

| # | Challenge | Verdict | Resolution |
|---|---|---|---|
| C1 | "A final source error can also be an unparseable payload, not only a failed upstream request" | **Already covered** | §14.2's bullets say exactly this (upstream asked *or* delivered-but-unparseable). No change. |
| C2 | "The mapping is wrong: an HTTP 404 produces `2004`, not `2005`" | **Rejected — misreading** | §14.3 already says this in the same sentence: "an HTTP 404 does **not** produce `2005`, it produces `2004`". The table and N11-2 agree. No change. |
| C3 | "Album short-circuit is unsafe: a transient 404 for one track could skip a healthy album; shuffled/compilation queues make it worse" | **Accepted in part** | The jump now requires a **second same-album `REMOTE_MISSING` within 30 s and within 2 queue positions**; the first always takes one-item `SKIP_NEXT`. `findNextAlbumIndex` only skips *consecutive* equal ids, so it cannot skip a different album; in a shuffled queue it degrades to one item. Multi-disc sharing is correct once the album is confirmed gone. `getAlbum` confirmation remains for the *persistent* purge only. |
| C4 | "`deleteAlbumsByIds` does not check `is_downloaded`; the purge guard is only prose" | **Accepted** | §14.6 now names the call-site query `countDownloadedTracksInAlbum(albumId)` and the starred/disliked/pending guard; the plain DAO delete must never be called without them. |
| C5 | "N11-1 logging is insufficient — it drops `errorCode` and the cause chain" | **Rejected — misreading** | N11-1 is precisely the change that adds `code=` and the one-level cause suffix. The challenge describes the *current* behaviour, not the proposal. |
| C6 | "The counter order/reset points are under-specified" | **Accepted as clarity** | §15.5 now spells out: `playerErrorCount++` stays first; `baseAction` is computed from that count; `onSourceError` is called next with the action-dependent threshold; `PAUSE_BURST` resets both the circuit and `playerErrorCount`; `burstPaused` is cleared on queue set, `STOP` and explicit item select. |
| C7 | "N11 is P1 over-engineering; policy B is premature; the real issue is N1" | **Partial** | N1 and N11 are different axes (propagation vs cause/reconciliation) and both stay. N11's P1 is **conditional on H1**: if the classification shows H2, §14.4's falsifier demotes it to diagnostics-only. The classifier is a pure ~20-line function, not over-engineering. **Policy B/N11-3 is explicitly deferred** until the classification capture; the first implementation is N11-1 + N11-2 only. |

Net effect on the plan: N11-2's first-failure behaviour changed (one-item skip), N11-3 gained an
enforceable purge guard and moved behind the capture, and §15.5's reset semantics were made
explicit. Nothing in §14.7's ordering changed.

### 14.10 Re-validation of the §14–§15 amendment (2026-11-01, third pass)

Because §14.9 accepted changes, the amended text was re-verified line by line against `HEAD`
and against media3 1.10.1 (class files inspected, not docs). Nine imprecisions were found and
corrected in place. None changes the verdict or the ordering; all narrow a claim to what the
code actually does.

| # | Where | What was wrong | Correction |
|---|---|---|---|
| V1 | §14.2 | Listed only two causes of a *final* source error. | Added the third: a `CacheDataSink` **write** failure. `FLAG_IGNORE_CACHE_ON_ERROR` covers cache reads, not writes, so a full disk surfaces as a source error. It is local, not remote. |
| V2 | §14.2, §14.4, N8, §15.4 | Used the burst's `reachable=true` as evidence about stream health. | ExoPlayer's `/rest/stream` path is `DefaultHttpDataSource` (`OfflineAwareDataSource.kt:44-52`), not the OkHttp `BaseUrlInterceptor`, so streaming never touches the boolean; and `onApiSuccess()` counts *any* HTTP response, including 404. `reachable=true` says only that unrelated Retrofit calls got responses. The evidence that item 59 played is unaffected. |
| V3 | §14.4, mechanism step 5 | Said `onPlayerError` always does `removeCached` + `seekToNextMediaItem()`. | Only when reachable. When unreachable the branch is `CACHED_JUMP`/`STOP_NO_CACHED` and `removeCached` is skipped (`MediaService.kt:1383-1395`). |
| V4 | N11-1 (§14.4) | Log line lacked the symbolic code; `currentId` was read after the log. | Add `name=${error.errorCodeName}` (`PlaybackException.getErrorCodeName()` is public in 1.10.1 — verified with `javap`) and hoist `currentId` above the log call. |
| V5 | N11-2 (§14.4) | "Cleared on any successful media transition" would be cleared by the first auto-skip's own transition. | Clear only on `onMediaItemTransition` when `!PlayerHolder.playbackErrorAutoSkipInFlight`, plus verified play ≥ 2 s, user seek, queue set, circuit reset. |
| V6 | N11-3 (§14.4) | Purge guard implied `cached_albums` and an unauthenticated fetch; queue pruning was unnamed. | Guard must read the **ledger** `AlbumEntity` (`starred_at`/`is_disliked`/`pending_unstar_at` exist only there, not on `CachedAlbumEntity`, `Entities.kt:194-213`); fetch through `repository.getAlbum(...)` (auth failure ≠ confirmation); prune via `DualQueueManager` + `QueuePersistenceManager`/`QueueJournalDao`. |
| V7 | §14.3 table | Unstated consequence of the pinned guard. | A `2005` on an `is_downloaded=1` row hits `removeCached`'s pinned guard (`CacheService.kt:537-557`) and is *not* evicted; the row keeps failing. Next capture will log `result=refused_pinned`. |
| V8 | §15.4 | Claimed the interceptor sees `/rest/stream` and cover art, and proposed a soft class for `/rest/getCoverArt`. | `SubsonicApi` declares **no** stream and **no** cover-art endpoint; both use separate clients, so the interceptor sees JSON API calls only and the proposed soft class would be dead code. Stream interceptor stays rejected. |
| V9 | §15.4, reference list | Cited `SyncScheduleWorker.kt:39` for `Result.retry()`. | Actual line is `:43` (`:39` is the `doWork` signature). |

Two claims were re-checked and left alone because they are load-bearing and correct: a non-null
`cached_path` does **not** prove H2 (a partial span still fetches its missing range upstream),
and HTTP 404 maps to `2004`, not `2005` (`2005` is `FileNotFoundException`).

**Second challenge round (Mistral, same day).** The nine corrections were then attacked
independently. Four challenge verdicts were mislabelled (V1, V2, V5, V9 were marked WRONG while
the supplied evidence supported the claim), and three substantive objections were raised. They
resolve as follows:

- V3 ("`removeCached` runs before the reachability check") — **rejected after reading
  `:1389-1395`**: the call is inside `if (!serverUnreachable && !failedId.isNullOrEmpty())`,
  in the launch, before the branch. The amended text is right.
- V4 ("`getErrorCodeName()` may not exist in 1.10.1") — **rejected**: `javap` on the 1.10.1
  class shows `public final java.lang.String getErrorCodeName()`. Hoisting `currentId` is
  still required (it is read at `:1366`, after the log at `:1352-1357`).
- V5 (ordering hazard) — **accepted as a clarification, now in N11-2**: the existing
  ADR-0095 block clears `playbackErrorAutoSkipInFlight` during the very transition the streak
  hook would observe, so the flag must be captured at method entry; and `playerErrorCount` is
  zeroed at `:978`, so the streak must be separate state.
- Its answer to "could a removed track still play?" cited `FLAG_IGNORE_CACHE_ON_ERROR` as
  permitting playback from a partial span — **rejected**: the flag ignores cache *read*
  errors and falls back upstream, it does not serve partial data when the upstream fails. The
  one true case is a **fully** cached track, which never contacts the server at all and is
  already the expected offline behaviour, not a counter-example to H1.

---

## 15. Implementation specification — what each item would change (nothing implemented at the time of writing)

> **Implementation note (2026-11-02).** This section is the approved design; §16 records what
> was built and the deliberate deviations (notably: media3 1.10.1 has no
> `MediaSession.Callback.onSeekTo`, the keepalive probe stays trusted, `REMOTE_MISSING` is
> excluded from the systemic circuit, and N11-3 was implemented in this pass).

This section records the design worked out after §13.9, so that implementation can be reviewed
before coding. No code has been written. Line numbers are `HEAD` as of 2026-11-01.

### 15.1 N3 — download/retry diagnostics

**Current.** `CacheService.writeCachedTrackFromFile` returns `Boolean`; five failure modes
collapse into `false` (`CacheService.kt:193-246`, `importIntoCache` `:265-310`):
zero-length (`:194-198`, logcat only), non-audio (`:199-212`, has a DiagnosticLog line),
locked (`:269-273`, logcat only), busy (`:282-288`, logcat only — note `busy && isCached`
returns **true**), and rename/copy/commit exception (`:303-310`, logcat only).
`DownloadManager` logs `worker write fail trackId=… priority=…` with no reason (`:411-417`)
and `handleRetry` writes `STATUS_FAILED` on the third attempt with no line at all (`:470-484`).

**Change.**

1. Add a sibling with a typed result, keep the Boolean wrapper (3 call sites:
   `CacheService.kt:179`, `CacheService.kt:256`, `DownloadManager.kt:396`):
   ```kotlin
   internal enum class CacheWriteOutcome { OK, OK_ALREADY_CACHED, ZERO_LENGTH, NON_AUDIO, LOCKED, BUSY, IO }
   suspend fun writeCachedTrackFromFileDetailed(trackId: String, sourceFile: File, isDownload: Boolean): CacheWriteOutcome
   suspend fun writeCachedTrackFromFile(...) = writeCachedTrackFromFileDetailed(...) == OK
   ```
   `importIntoCache` returns the outcome (it already distinguishes the cases).
2. Failure line: `worker write fail trackId=… priority=… reason=locked|busy|zero_length|non_audio|io attempt=<n>/3`
3. Exhaustion line: `worker terminal trackId=… priority=… reason=retry_exhausted attempts=3 last=<reason>`
4. Promote the zero-length branch to DiagnosticLog (`writeCached reject … reason=zero_length`).

**Unchanged.** Retry policy, exponential backoff, `STATUS_FAILED`/`STATUS_FAILED_CT`
semantics, `isTerminalFailed`, ADR-0117.

**Tests.** `CacheWriteOutcome` mapping (pure); `handleRetry` terminal line via static
`DiagnosticLog` mock (pattern already used in the repo).

### 15.2 N4 — a soft-stopped run must not read as "complete"

**Current.** Soft-stop sets `softStopped` and logs to logcat only (`MetadataSyncWorker.kt:1484-1492`);
`phase="complete"` is still assigned (`:528-536`); the only export line is
`soft-stop: cadence+metadata_version skipped` (`:558-560`); `finally` prints
`sync end phase=complete …` (`:572-576`).

**Change (log-only).**

- At the trip: `sync soft-stop trackDrain streak=8 albumId=… failed=… pending=… fetched=…`
- At completion: `sync end phase=complete drain=complete|soft-stopped failed=… pending=… elapsed=…`
- Replace the vague line with `sync watermark skip cadence=true metadataVersion=true reason=soft-stop`.

**Deliberately excluded.** A new `phase="complete_partial"`: it would require editing
`SyncingScreen.kt:221-222` and `:271-272` and every `phase == "complete"` consumer for no
diagnostic gain. UI wording is a separate UX decision.

**Tests.** None needed beyond a compile check; the strings are asserted only by inspection.

### 15.3 N5 — header freshness after a soft-stop

**Current.** Header prints `lastFullSyncMs`/`lastDeltaSyncMs` (`DiagnosticLog.kt:81`), written
only on a complete drain (`MetadataSyncWorker.kt:545-560`). A soft-stopped run moves nothing.

**Change.** Four display-only prefs keys, written by `MetadataSyncWorker`
(`:174-179` for the constants, the completion block at `:534-560`, plus the error/abort
paths):

| Key | Written | Value |
|---|---|---|
| `PREF_LAST_SYNC_ATTEMPT_MS` | sync start | `now` |
| `PREF_LAST_SYNC_OUTCOME` | completion/error/abort | `complete` \| `soft-stopped` \| `error` |
| `PREF_SOFT_STOP_STREAK` | soft-stop `++`, complete `0` | int |
| `PREF_LAST_DRAIN_COMPLETE_MS` | `tracksDrainComplete` | `now` (mode-independent twin of the cadence stamp) |

Header line: `syncFreshness lastAttemptMs=… lastOutcome=soft-stopped softStopStreak=2 lastDrainCompleteMs=…`,
printed next to the existing `syncMode mode=… skip=…` line. `DiagnosticLog.snapshot` gains
defaulted parameters, so no other call site changes. ADR-0068 untouched.

**Tests.** Extract `formatSyncFreshness(...)` as an internal pure function; assert the four
branches (never run, complete, soft-stopped, error).

### 15.4 N8 — one owner for the reachability boolean

**Current.** `BaseUrlInterceptor` flips on every **OkHttp** response
(`NetworkModule.kt:159-179`). `SubsonicApi` declares only JSON endpoints — no
`/rest/stream` and no `/rest/getCoverArt` — so today the only traffic it sees is Retrofit API
calls; playback (`DefaultHttpDataSource`, `OfflineAwareDataSource.kt:44-52`) and Coil cover
art use separate clients. Stream success/failure therefore never flips the boolean.
`ReachabilityStateHolder` flips on the first event (`ReachabilityStateHolder.kt:14-38`).
Consumers that *act* on it: `MediaService.kt:1383` (`CACHED_JUMP` vs `SKIP_NEXT`),
`MetadataSyncWorker.kt:324` (skips the whole run), `SyncScheduleWorker.kt:43`
(`Result.retry()`), `OfflineAwareHttpDataSource` (blocked sockets).

**Change.**

1. Keep the interceptor's scope as-is: it only sees the JSON API, so there is **no soft class
   to add today** (a `/rest/getCoverArt` rule would be dead code). Leave a one-line comment
   saying non-JSON endpoints must never be added to this client. Do **not** add a stream
   interceptor: `/rest/stream` bypasses OkHttp entirely, and letting playback flip
   reachability would re-introduce exactly the coupling that `MediaService.kt:1383` already
   suffers from.
2. Hysteresis: a hard failure flips false only after **2 failures within 10 s with no success
   in between**; any success flips true immediately and clears the counter. Extract the policy
   into a pure `ReachabilityPolicy` (mirrors `SourceErrorCircuit`).
3. `ServerReachabilityMonitor` unchanged: it keeps probing at 15 s/60 s and calls the same two
   methods, becoming a third evidence source rather than the only authority.
4. `isReachable: StateFlow<Boolean>` and every consumer stay unchanged.

**Tests.** Pure policy: one soft failure ⇒ no flip; 2 hard failures in 10 s ⇒ flip; success
between ⇒ no flip; failures 11 s apart ⇒ no flip; success ⇒ immediate true.

### 15.5 N1 — option C (bounded, resumable), as decided

**Current flow** (`onPlayerError`, `:1351-1461`): increment `playerErrorCount` →
`sourceErrorCircuit.onSourceError(now)` (threshold 3/15 s) → `decideErrorSkipAction` →
`circuitTripped` overrides the action → on `SKIP_NEXT`, set
`playbackErrorAutoSkipInFlight`, `removeCached`, `seekToNextMediaItem()`.

**Change.**

1. **Remove the symptom-based reset.** Delete `onUserSeek()` from
   `onPositionDiscontinuity` (`:1151-1155`). Reset instead from:
   - `PlaybackControl.SEEK_TO` (`:4552-4557`; `handlePlaybackControl` at `:4488`), before the
     coalescer check;
   - a new `onSeekTo` override in `MediaSessionCallback` (`:86`), because the session is built
     on `exoPlayer` (`MediaModule.kt:108`) and notification/lock screen/Auto/AVRCP/Cast seeks
     currently bypass `PlaybackControl`. Wire it through a lambda like the existing
     `PlayerHolder` hooks.
2. **Count per action.** Compute the *base* action with the unchanged
   `decideErrorSkipAction(...)` first, then pick the threshold from it:
   ```kotlin
   val baseAction = decideErrorSkipAction(playerErrorCount, currentId, idx, mediaItemCount)
   val reachable = ReachabilityStateHolder.isReachable.value
   val advancing = reachable && baseAction == ErrorSkipAction.SKIP_NEXT
   val threshold = if (advancing) AUTO_SKIP_BURST_THRESHOLD else SOURCE_ERROR_CIRCUIT_THRESHOLD
   val (nextCircuit, tripped) = sourceErrorCircuit.onSourceError(
       nowMs,
       windowMs = if (advancing) AUTO_SKIP_BURST_WINDOW_MS else SOURCE_ERROR_CIRCUIT_WINDOW_MS,
       threshold = threshold,
   )
   sourceErrorCircuit = nextCircuit
   val action = when {
       !tripped -> baseAction
       advancing -> ErrorSkipAction.PAUSE_BURST
       else -> ErrorSkipAction.CIRCUIT_STOP
   }
   ```
   `AUTO_SKIP_BURST_THRESHOLD = 8` (house precedent: `TRACK_CONSECUTIVE_FAIL_SOFT_STOP`,
   `MetadataSyncWorker.kt:217`), `AUTO_SKIP_BURST_WINDOW_MS = 120_000`. The 15 s circuit window
   is deliberately kept for non-advancing errors; with 8 skips it would expire mid-burst at
   realistic per-item failure times.
   **Do not** add a second increment in the `SKIP_NEXT` branch: `onSourceError` already counts
   each failure once, and doubling would trip at 4. After N11-2, `REMOTE_MISSING` does not
   reach the `SKIP_NEXT` branch at all (it takes the album jump), so it cannot inflate this
   counter.
3. **New outcome.** `ErrorSkipAction.PAUSE_BURST`, handled beside `CIRCUIT_STOP` (`:1496-1513`):
   log `autoSkip action=PAUSE_BURST n=$threshold fromIdx=… trackId=… reachable=true`,
   `ep.pause()` (not `stop()` — index/position survive),
   `PlayerHolder.setPlaybackError("$threshold tracks failed to play — tap play to resume")`,
   reset **both** `sourceErrorCircuit` and `playerErrorCount` (the reset is what makes the next
   run start from zero), and set `burstPaused = true`.
4. **Resume.** In `PLAY_PAUSE` (`:4491`): if `burstPaused`, `seekToNextMediaItem()`, reset
   `playerErrorCount`, clear `burstPaused`, then play. If there is no next item, fall through
   to the normal end-of-queue stop instead of resuming the failed one. No timed auto-resume.
   `burstPaused` is also cleared whenever a new queue is set, on `STOP`, and on an explicit
   item select — otherwise a stale flag would skip a track on the next unrelated play.
5. **Diagnostics.** `circuit clear src=user-seek|verified-play|burst|circuit-stop` on every
   reset, so the next capture proves who cleared it. Expect `errorCount=1` on every burst line
   even after the change: `playerErrorCount` is zeroed by `onMediaItemTransition` (`:978`), so
   the numbers that bound a burst are the circuit's, not that field's. This is also why the
   current burst shows `errorCount=1` 58 times.

**Interaction with N11.** When N11-2 lands, `REMOTE_MISSING` takes the album jump on its
**second** same-album failure, so one deleted album contributes two failures, not 58. The
first failure still reaches this counter (and is cheap); C remains the backstop for mixed
runs and for track-level removals inside a surviving album.

**Tests.**

1. `SourceErrorCircuitTest`: 8 advancing errors trip at 8; 7 do not; verified play resets;
   non-advancing still trips at 3; 120 s expiry.
2. New pure `decideErrorRecovery(...)` (extracted from the handler body) covering the action
   matrix; keeps `decideErrorSkipAction`'s signature intact.
3. Service-level (`spyk(MediaService())`, as in `MediaServiceCastQueueTest`): set the circuit
   to 2 via reflection, call `handlePlaybackControl(SEEK_TO(0.5f))`, assert 0; assert
   `onPositionDiscontinuity` no longer clears it.

### 15.6 Verification and exit criteria

Per item, from `compose/`:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest` (last green: 3963 tests, 0 failures, 6 skipped)
- `./gradlew :app:ktlintCheck`
- a post-fix capture showing the new line, plus a manual check that no existing line's format
  changed in a way the diagnostics reader depends on.

### 15.7 Risk register

| Risk | Where | Mitigation |
|---|---|---|
| `onPlayerError` reorder changes the action for the first error | `:1360-1381` | pure-decision tests over `(count, idx, itemCount, reachable)` |
| deleting the discontinuity reset loses a real user-seek path | `:1151-1155` | the two explicit hooks (§15.5/1) cover in-app and external seeks; test both |
| burst threshold too low/high | `SourceErrorCircuit` | one constant; the outcome is a resume-able pause, not a stop |
| album short-circuit skips a healthy album after one transient 404 | N11-2 | **second same-album `REMOTE_MISSING` required** before the jump; a single failure always takes one-item `SKIP_NEXT` |
| album short-circuit cannot help a shuffled queue | N11-2 | `findNextAlbumIndex` only skips consecutive equal ids, so it degrades to one item; N1-C bounds the run |
| album purge deletes a downloaded album from the library while its files remain | N11-3 | guard: never purge an album containing a downloaded/pinned track; use policy B (mark/hide) for those |
| N11-2's classifier is wrong for a 200-error-body served as audio | N11-2 | three-state `SpanState`: `NONE` ⇒ remote, `UNPLAYABLE` ⇒ local (evict), `PLAYABLE` ⇒ `UNKNOWN`; never evict a track with no local span |
| `spanState` file read lands on the main thread in `onPlayerError` | N11-2 | pure classification on main; span probe + eviction + album-index computation inside the existing `scope.launch` (`Dispatchers.IO`), seek back on Main |
| hysteresis makes a dead server look alive | N8 | two failures within 10 s, plus the 15 s monitor; worst case one extra probe interval |
| `removeCached` no longer runs on `NETWORK`/`UNKNOWN`/`REMOTE_MISSING` failures | N11-2 | intended: eviction is for `LOCAL_FAULT` only; a remote failure must not destroy a usable cache span |

### 15.8 What does not change

- ADR-0068 cadence semantics and `metadata_version` (N5 is display-only).
- ADR-0111's "3 consecutive source errors ⇒ stop" for **non-advancing** errors (single-track
  and last-track cases observed at `12:29:36` keep their behaviour).
- ADR-0117 terminal-failure semantics (N3 adds lines only).
- ADR-0139 soft-stop/phase="error" contract (N4 adds lines, does not change `phase`).
- The `isReachable` public shape and every consumer (N8 internal only).
- The persisted queue schema. The **first implementation** (N11-1 + N11-2) adds no table and
  no column: it reuses `deleteAlbumsByIds`/`deleteOrphanedAlbumTracks`/`pruneAlbumLedger`/
  `reconcileSearchCorpusAgainstCatalog` for the later purge. N11-3/policy B, **if and when
  approved after the classification capture**, would add one `missingAt` column or a small
  table plus a Room migration; that is deliberately not part of this pass.
- Downloaded/pinned or starred/disliked albums are never purged by N11-3 — they can only be
  marked (policy B).
  **Superseded by §16.2/4 (2026-11-02):** N11-3 was implemented in this pass with the same
  guards; a clean absent album is purged without a schema change, and policy B is not used.

## 16. Implementation record (2026-11-02)

§15 is the approved design. This section records what was actually built, the four deliberate
deviations, the new diagnostics lines, and the verification numbers. Everything here was
implemented in one pass; no Room schema change and no data migration were needed.

### 16.1 Item → change map

| Item | Implemented change | Where |
|---|---|---|
| N1 | Base action is computed first; an *advancing* run uses `AUTO_SKIP_BURST_THRESHOLD=8`/`AUTO_SKIP_BURST_WINDOW_MS=120 s`, a stuck run keeps 3/15 s. A trip while advancing is `PAUSE_BURST` (pause, not stop); the next `PLAY_PAUSE` resumes past the failed item. `chooseErrorSkipAction` holds the action matrix. The trip and the `PAUSE_BURST n=` line both use `disruptive` (album/cached jumps are recoveries and only inflate the aggregate). | `playback/SourceErrorClassification.kt`, `playback/MediaService.kt` |
| N2 | `AutoSkipBurst` aggregates count/items/indices/duration/evicted/disruptive and emits one `autoSkip burst …` line at the natural end of a run or at `PAUSE_BURST`. `removeCached` adds `hadSpan=`/`bytes=` so a call is distinguishable from an eviction. | `SourceErrorClassification.kt`, `CacheService.kt` |
| N3 | Typed `CacheWriteOutcome` (7 values) + `writeCachedTrackFromFileDetailed`; the Boolean wrapper stays. DownloadManager logs `worker write fail … reason=… bytes=… attempt=n/3` and `worker terminal … reason=retry_exhausted attempts=3 last=…`. | `data/cache/CacheWriteOutcome.kt`, `CacheService.kt`, `DownloadManager.kt` |
| N4 | `sync soft-stop trackDrain streak=… albumId=… reason=get_album_null failed=… pending=… fetched=…` at the trip, and `sync end phase=… drain=complete|soft-stopped failed=… pending=…`. | `data/db/MetadataSyncWorker.kt` |
| N5 | `PREF_LAST_SYNC_ATTEMPT_MS`, `PREF_LAST_SYNC_OUTCOME`, `PREF_SOFT_STOP_STREAK`, `PREF_LAST_DRAIN_COMPLETE_MS`, written independently of the ADR-0068 cadence stamps, rendered as `syncFreshness lastAttemptMs=… lastOutcome=… softStopStreak=… lastDrainCompleteMs=…`. `sync watermark skip cadence=true metadataVersion=true reason=soft-stop` added. A run cancelled mid-flight keeps the previous outcome value (display-only). | `MetadataSyncWorker.kt`, `DiagnosticLog.kt`, `SettingsViewModel.kt` |
| N6 | `phase=tracks fetched=<this run> corpus=<trackCountAll>` (the old line reused the corpus for both). | `MetadataSyncWorker.kt` |
| N7 | `app start pid=… ver=…` on `Application.onCreate`; `service teardown reason=destroy|pid=…` and `reason=task_removed pid=…`. | `FtpmusicApp.kt`, `MediaService.kt` |
| N8 | Pure `ReachabilityPolicy`: interceptor failures flip false only on the 2nd within 10 s; any success flips true immediately and clears the run. The dedicated keepalive probe is trusted (see 16.2). Consumers and `StateFlow<Boolean>` unchanged. | `di/ReachabilityPolicy.kt`, `di/ReachabilityStateHolder.kt`, `data/repository/ServerReachabilityMonitor.kt` |
| N9 | Timestamp is taken inside the ring lock, so append order equals timestamp order. Throwable suffix carries one level of cause (`A caused_by B`, two frames max, URLs scrubbed). | `data/diagnostics/DiagnosticLog.kt` |
| N11-1 | `player error trackId=… idx=… code=… name=… http=… cause=… msg=…`; HTTP status is unwrapped from `HttpDataSource.InvalidResponseCodeException` up to four cause levels; album id is read from `MediaMetadata.extras`. | `MediaService.kt` |
| N11-2 | Pure `classifySourceError` (LOCAL_FAULT / REMOTE_MISSING / NETWORK / UNKNOWN), three-valued `CacheSpanState`, IO-side span probe, eviction only on LOCAL_FAULT. `REMOTE_MISSING` logs `SKIP_MISSING …`; the 2nd same-album failure within 30 s and ≤2 positions logs `SKIP_MISSING_ALBUM … after=2` and jumps the consecutive album run (the queue is re-read on main, so a timeline that shrank after the IO hop cannot index past the end). | `SourceErrorClassification.kt`, `CacheService.kt`, `MediaService.kt` |
| N11-3 | Authenticated `getAlbum` verification (`AlbumPresence` PRESENT/ABSENT/UNKNOWN; only Subsonic `code=70` is ABSENT), 60 s per-album cooldown, and a guarded purge: no pinned download, no starred/disliked/pending-unstar album, then delete cached albums, prune orphaned album tracks, prune the ledger, reconcile the search corpus, and remove the album's Dual-queue entries + persist the queue. | `playback/AlbumReconciler.kt`, `data/repository/Repositories.kt`, `data/db/Daos.kt`, `playback/DualQueueManager.kt` |

### 16.2 Deliberate deviations from §15

1. **No `MediaSession.Callback.onSeekTo` in media3 1.10.1.** The class has no such callback
   (verified with `javap` against the resolved artifact), so §15.5/1 cannot be implemented as
   written. The replacement is a *guarded* reset in `onPositionDiscontinuity`: it clears the
   trackers unless `PlayerHolder.playbackErrorAutoSkipInFlight` is set, so the programmatic
   skip no longer neutralises the circuit, while notification/Auto/AVRCP/Cast seeks that arrive
   as position discontinuities still reset. In-app `PlaybackControl.SEEK_TO` resets explicitly
   before the coalescer, and verified play / queue set reset too. Residual: a non-error
   internal seek now clears the circuit (it is user- or queue-mediated); the burst aggregate
   has already logged by then, so no evidence is lost.
2. **The keepalive probe is trusted immediately.** §15.4/3 implied the monitor would share the
   interceptor hysteresis. `onProbeFailure` flips false on the first verdict instead: the probe
   is single-flight with its own 5 s timeout, it is the only signal while the app is idle, and
   the 15 s/60 s cadence bounds re-flapping. Consequence: one probe timeout can flip the
   boolean false; any API success or the next probe flips it back.
3. **`REMOTE_MISSING` is excluded from the systemic circuit.** A deleted album must not be
   reported as a systemic source failure: the run is bounded instead by `MissingAlbumRun`
   (2 failures ⇒ one album jump) and by `AutoSkipBurst` (8 disruptive skips / 120 s ⇒ pause).
   NETWORK/UNKNOWN/LOCAL_FAULT keep the 3/15 s stuck-run semantics.
4. **N11-3 is implemented in this pass**, superseding the §15.8 note that it was deferred until
   after a classification capture. The decision to implement it was explicit, and the purge is
   gated so a wrong classification can only *fail to purge*, never delete user data:
   authenticated verification, only `code=70` ⇒ ABSENT, no pinned downloads, no
   starred/disliked/pending-unstar, and a cooldown so a shuffled queue cannot re-verify
   continuously. Policy B (hide/mark) is not used.
5. **The burst aggregate and the bound share one object.** `AutoSkipBurst` is both the summary
   and the threshold owner; `disruptive` entries count toward the bound, while album jumps and
   cached jumps are recorded as recoveries (`disruptiveEntry=false`). This avoids a second
   counter that could drift from the summary. Consequence: a mixed queue where every album
   contributes one disruptive first-failure and one recovery can skip up to `2 × threshold`
   items before the pause; the `PAUSE_BURST n=` line reports the disruptive count (the bound
   that fired), while the aggregate `count` stays in the `autoSkip burst` summary.
6. **`playerErrorCount` remains zeroed on every transition.** As §15.5/5 predicted, burst lines
   still show `errorCount=1`; the bound lives in the circuit/burst counters.

### 16.3 New diagnostics lines (exact prefixes)

- Playback: `player error trackId=… idx=… code=… name=… http=… cause=… msg=…`,
  `autoSkip action=SKIP_NEXT|SKIP_MISSING|SKIP_MISSING_ALBUM|CACHED_JUMP|STOP_NO_CACHED|PAUSE_BURST|BURST_RESUME|…`
  (`PAUSE_BURST` carries both `n=<disruptive>` and `disruptive=<n>`),
  `autoSkip burst count=… items=… firstIdx=… lastIdx=… durationMs=… evicted=… disruptive=…`,
  `circuit clear src=user-seek|verified-play|queue-set|burst|circuit-stop|stop-no-cached|album-end|…`.
- Cache: `cacheImport trackId=… bytes=… result=locked|busy|already_cached`,
  `writeCached reject … reason=zero_length|non_audio`,
  `removeCached trackId=… result=ok|fail|refused_pinned|heal_poison_pinned hadSpan=… bytes=…`.
- Download: `worker write fail … reason=… bytes=… attempt=n/3`,
  `worker terminal … reason=retry_exhausted attempts=3 last=…`, `worker terminal … reason=non_audio_content_type`.
- Sync: `sync soft-stop trackDrain …`, `sync end … drain=…`, `sync watermark skip … reason=soft-stop`,
  `phase=tracks fetched=… corpus=…`, header `syncFreshness …`.
- Reachability: `server unreachable` / `server reachable again` (tag `ftpmusic-reach`).
- Lifecycle: `app start pid=… ver=…`, `service teardown reason=destroy|task_removed pid=…`.
- Reconcile: `reconcile albumId=… presence=… removed=false reason=…` and
  `reconcile albumId=… removed=true albumTracks=… orphanedTracks=… corpus=… queueEntries=… cachedKept=…`.

### 16.4 Verification (2026-11-02, from `compose/`)

- `./gradlew :app:assembleDebug` — **pass**.
- `./gradlew :app:ktlintCheck` — **pass**.
- `./gradlew :app:testDebugUnitTest` — **4040 tests, 0 failures, 6 skipped**.
- `./gradlew :app:jacocoTestReport` — project line coverage **64.23 %** (26492/41245). The new
  pure logic is covered directly: `SourceErrorClassificationKt` 97.5 %, `AutoSkipBurst` 90.0 %,
  `MissingAlbumRun` 100 %, `AlbumReconciler` 100 %, `DiagnosticLog` 98.3 %,
  `DownloadManager` 92.1 %, `CacheService` 84.0 %, `MetadataSyncWorker` 91.1 %. `MediaService`
  stays at 35.5 % (Android service; unchanged test strategy — its decision logic is exercised
  through the pure helpers above).
- New tests: `SourceErrorClassificationTest` (classifier matrix, album run, burst, action
  matrix, recoveries-vs-bound), `AlbumReconcilerTest` (every guard + purge + cooldown),
  `AlbumPurgeBlockTest`, `AlbumPresenceParseTest`, `CacheWriteOutcomeTest`,
  `ReachabilityPolicyTest`, `ReachabilityStateHolderTest` (hysteresis/probe), and
  `DiagnosticLogTest` (freshness + cause chain + field cap).

### 16.5 What the next capture must show

- `player error …` with a non-`-` `http=`/`cause=` on the failure that starts a run; if a 404
  burst still logs `http=-`, H1 stays unproven (the classifier could not see the status).
- `SKIP_MISSING_ALBUM … after=2` for a deleted album, followed by
  `reconcile … presence=ABSENT` and `removed=true` only when the guards pass.
- `PAUSE_BURST n=8 disruptive=8 …` instead of 58 silent skips; the next play logs `BURST_RESUME`
  and does not retry the failed index. In a mixed-album removal run the pause can come after up
  to 16 items (one disruptive first-failure + one recovery per album); `n` still reads 8.
- `circuit clear src=…` on every reset; no cancellation caused by an auto-skip seek.
- `sync end … drain=soft-stopped` plus a `syncFreshness lastAttemptMs=… lastOutcome=soft-stopped`
  header on a soft-stopped run, without advancing the ADR-0068 cadence stamps.

### 16.6 Phase-5 cross-model audit record (2026-11-02)

DeepSeek audited the implemented diff against §15/§16 and returned three blocking claims plus
non-blocking risks. Each blocking claim was checked against the code before rework:

| Claim | Verdict | Action |
|---|---|---|
| B1 — `applyRemoteMissingSkip` indexes a stale `itemCount` and can throw on the media3 looper | **Partly valid.** `ep` is the captured `ExoPlayer` (not the session/Cast player), the scan runs on Main, and `runCatching` per index keeps `albums` exactly `itemCount` long — so the crash as described cannot happen. The stale-size concern is real if the timeline shrank after the IO hop. | Re-read `ep.mediaItemCount` on main and use it for the loop and the end-of-queue test. |
| B2 — `pauseAfterBurst` sets `burstPaused`, then any transition clears it, so resume retries the failed item | **Invalid.** `pause()` emits no item transition; only a real advance (or a genuine queue replacement) clears `burstPaused`, and `PLAY_PAUSE` clears it before seeking past the failed index. The audit retracted this itself. | No code change. |
| B3 — `PAUSE_BURST n=` logs the aggregate `count`, not the bound (`disruptive`) | **Valid.** Album/cached recoveries inflate `count`, so a mixed run could log `n=11` for a threshold of 8. | `n` now reports `disruptive`; the line also carries `disruptive=`; §16.2/5 and §16.5 state the up-to-`2 × threshold` item worst case. |

Non-blocking items accepted or fixed:

- **R2 (span `NONE` ⇒ `REMOTE_MISSING` for a partial span):** accepted. `getSpanFile` returns
  null for a hole/locked/partial span, so a mid-file decode failure can look like a removal.
  The blast radius is one album jump; N11-3's authenticated `getAlbum` then returns PRESENT and
  nothing is deleted. The next capture distinguishes the case via `http=`/`cause=`.
- **R3 (last-track burst logs a summary without a pause):** accepted; matches ADR-0111.
- **R4 (cause-chain field length):** fixed — the joined chain is capped at `MAX_FIELD_LEN`, so
  one line cannot carry two 200-char messages.
- **R5 (cancelled purge between album delete and orphan prune):** fixed — the mutation sequence
  runs in `NonCancellable`, so cancellation is observed only after the prune and queue persist.
- **R6 (no service-level test of the new orchestration):** accepted as a follow-up. The pure
  decision logic (`classifySourceError`, `MissingAlbumRun`, `AutoSkipBurst`,
  `chooseErrorSkipAction`, `albumPurgeBlock`, `parseAlbumPresence`, `ReachabilityPolicy`) is
  covered directly; `MediaService` remains a thin Android shell at 35.6 %.
- **R7 (`SyncScheduleWorkerTest` now sets the holder directly):** accepted — that test asserts
  the consumer contract; the hysteresis itself is covered by `ReachabilityPolicyTest` and
  `ReachabilityStateHolderTest`.
- **N5 cancellation leaves the previous outcome:** accepted (display-only) and documented in
  §16.1.
