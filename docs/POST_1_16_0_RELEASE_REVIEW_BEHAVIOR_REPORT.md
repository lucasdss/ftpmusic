# POST 1.16.0 → 1.16.1 release review (caveman)

**Baseline:** `v1.16.0` (versionCode 24)  
**Target:** `1.16.1` (versionCode 25)  
**Range:** 8 commits after tag + review fixes  
**Room:** 68 (unchanged)

## Committed themes since 1.16.0

| Area | Commits | Notes |
|------|---------|-------|
| Android Auto | `e97f0f9` | `automotive_app_desc` + manifest; browse not phone UI |
| Connect | `c4673ae` | Subsonic `requireOk` before persist/sync |
| Playlist | `5d79145` | repeated query params + fail-closed full-replace |
| Metrics | `97c4bd2` | `upsertTracksPreserveCache` + Profile polish |
| Reachability | `1095401` | probe try/catch vs config-read races |
| DB | `d41b7ab` | genre populate preserves disc/track/enrichment |
| Scroll | `9b941d5` | pass 8 prefetch / decode / loadMore hygiene |
| BT | `85ca2a0` | A17 seat-only; no happy-path Resume (ADR-0148) |

## Confirmed bugs → fixed (ship in 1.16.1)

### Stale Resume beside info notif — High (P1)

1. **`offline_empty` / empty Room left Resume `1002`** — seat-only + info
   `1003` paths posted without `cancelResumeNotif`; prior null-mac / FGS-deny
   Resume could sit next to open-app info.  
   **Fix:** cancel Resume before info on offline_empty + both empty-queue paths.  
   **Test:** `MediaServiceFgsReassertTest` offline_empty `cancelled == 1`.

### Info notif honesty — Med (P2)

2. **Info notif skipped enable check** — Resume path logged `notif_disabled`;
   info path silent when OS notifs off.  
   **Fix:** mirror gate + `info_notif_disabled` telemetry.  
   **Test:** `BtAutoplayStarterTest` notifs-off records event.

### Reachability auth death — Med (P2)

3. **Keepalive ping treated HTTP-ok + `status=failed` as reachable** — connect
   already `requireOk`; probe did not.  
   **Fix:** `auth.requireOk(response)` inside `probeOnce` success path.  
   **Test:** `ServerReachabilityMonitorTest` status-failed → unreachable.

## Looks correct (no code change)

- Auto car media declaration + library-root Robolectric
- Playlist sync fail-closed + repeated params (large-N URL deferred)
- Genre COALESCE preserve + album disc→track order
- Metrics weight preserve via `upsertTracksPreserveCache`
- Scroll pass 8 prefetch windows / likes decode / loadMore dedup
- Connect auth gate before persist

## Document-only

- BT call graphs aligned to ADR-0148 (seat-only; Resume only null-mac / FGS-deny)
- A17 HU AVRCP / media-notif Play = device matrix (tester checklist)

## Deferred residuals (not blockers)

- Playlist large-N GET URL length → chunk / `formPost`
- `ensureTrackRow` TOCTOU under concurrent scrobble
- Genre populate invents `disc_number=1` when existing NULL
- Scroll prefetch Compose unit coverage thin

## Gates (must green before tag)

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```
