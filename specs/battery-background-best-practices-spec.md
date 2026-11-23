# Spec: Battery / background best practices

## Goal

Reduce idle radio / Wi‑Fi / CPU drain while preserving playback reachability,
Cast discoverability, sync correctness, and Play policy posture.

## Contracts

### WifiLock (track drain)

- Acquire `WIFI_MODE_FULL_LOW_LATENCY` only around each album-track **network**
  batch (`awaitAll` / getAlbum fetches).
- Release before Room batch writes and inter-batch delays.
- Do not acquire when pending album set is empty.
- Cancel / soft-stop mid-batch must still release (per-batch `finally`).

### Cast discovery

- Default MediaRouter flags: `REQUEST_DISCOVERY | UNFILTERED_EVENTS` (no
  continuous `PERFORM_ACTIVE_SCAN`).
- `PERFORM_ACTIVE_SCAN` only while Cast picker is open (`showDialog`) or during
  an explicit force-scan window (~5s), then revert to passive.
- Cast affordance remains visible even with zero discovered devices so the
  picker (and ACTIVE_SCAN) can be opened.

### Reachability keepalive

- Keep `NetworkCallback` registered while the monitor is started.
- Foreground: 15s unreachable / 60s reachable.
- Background (`ProcessLifecycleOwner` ON_STOP): stretch keepalive to 15 minutes;
  do **not** call full `stop()`.
- ON_START: resume normal intervals + immediate probe.

### Playlist flush

- Failure backoff unchanged: 60s → 5m → 30m.
- When pending queue empty and failures == 0: idle interval **5 minutes**
  (not 15s).
- `flushNow` / `flushNowAndAwait` remain independent of the idle loop.

### Download worker

- Empty-queue wait uses a conflated wake signal from enqueue paths that create
  or requeue **pending** work.
- Offline / cellular hard-local constraint spins keep a short timed delay.
- Existing &lt;20% battery gate for non-urgent priorities unchanged.

### WorkManager

- Periodic metadata sync and enrich one-shot: `requiresBatteryNotLow = true`
  plus existing network constraints.
- In-process SyncHealthGuard / SyncingScreen / Resync / force FULL bypass WM
  and are **not** batteryNotLow-gated.

### Age Signals

- Legal hold only — no SDK until counsel decides (see Play policy doc).

## Plan challenge (failure modes)

1. **WifiLock leak on cancel** — mitigated by per-batch `finally { release }`.
2. **Cast button invisible under passive-only** — mitigated by always-visible
   Cast affordance so picker can open and request ACTIVE_SCAN.
3. **Reachability `stop()` on background** — rejected; would unregister
   NetworkCallback and stale-out background playback consumers. Keepalive
   pause/slow only.
4. **`batteryNotLow` starvation** — SyncHealthGuard still fires in-process
   DELTA on foreground so low-battery users who open the app still sync.

## Validation

- Unit tests for WifiLock pairing, reachability foreground intervals,
  playlist idle backoff, download wake-on-enqueue, WM batteryNotLow.
- `./gradlew :app:assembleDebug` + targeted unit tests green.
