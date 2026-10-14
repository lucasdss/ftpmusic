# POST 1.13.0 upgrade-safety review → 1.13.1 (caveman)

**Baseline:** `1.12.0` (v19, Room 64) + `1.13.0` (v20, Room 65)  
**Target:** `1.13.1` (v21, Room 67)  
**Lock:** path C — both field installs must upgrade cleanly

## Ship blockers fixed

1. **FULL album-list fail-open wipe** — zero-new / max-pages now
   `listIncomplete` → `AlbumListIncompleteException`; no
   `replaceAlbumsDiffPreserveEnrich` on truncated walk.
2. **Playlist track REPLACE wipe** — inbound uses
   `upsertTracksPreserveCache` (import + silent refresh).
3. **Malformed entry wipe** — `songCount > 0` + missing entry → abort write
   (no `clearEntries`).

## Migration coverage

- Mock tests: 65→66, 66→67, chain 64→67, chain 65→67
- Completeness assert includes `ALL_MIGRATIONS_66/67`
- `is_late` entity `defaultValue = "0"` aligned with migration

## Residual (documented, not blockers)

- Soft-stop consecutive streak race under parallel batches
- `onTimeout` stops FGS only (Job not cancelled)
- Genre densify bounded loosely by `songCount`
- Evicted skip albums with existing tracks wait for force/meta-change

## Gates

```bash
./gradlew :app:testDebugUnitTest --tests '*MetadataSyncWorkerTest*' \
  --tests '*PlaylistRepositoryTest*' --tests '*AppDatabaseMigrationsTest*'
./gradlew :app:assembleDebug
```

## Vibe audit notes

Headless Vibe QA (read-only, max-turns constrained) inspected the diff mid-stream:
ship blockers read as correct; called out that migration tests are MockK
`execSQL` verifies (repo convention) — they will not catch Room post-migration
`TableInfo` default mismatches the way `MigrationTestHelper` would. Residual
low: unparseable album ids on a final full-size page could still truncate a
FULL walk (Navidrome ids are strings — low risk). Local gates green.
