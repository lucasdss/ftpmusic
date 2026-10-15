# POST 1.15.0 → 1.16.0 release review (caveman)

**Baseline:** `v1.15.0` (versionCode 23)  
**Target:** `1.16.0` (versionCode 24)  
**Range:** 3 commits after tag + review fixes

## Committed themes since 1.15.0

| Area | Commits | Notes |
|------|---------|-------|
| Artist Play All | `0a2d055` | track-id membership (ADR-0145); shuffle albums default |
| Desired / Discover | `813b439` | origin, bio, apostrophe fold, alias match |
| Home chrome | `7b8b7b9` | riding opaque bg under AppHeader reveal |

## Confirmed bugs → fixed (shipped in 1.16.0)

### Desired apostrophe merge — High (P1)

1. **Curly→straight duplicate Desired rows** — `ArtistNameNormalizer` folds
   apostrophes into `nm:` keys; title-bridge SQL used raw `LOWER(TRIM(title))`
   so pre-upgrade curly rows missed → second insert.  
   **Fix:** SQL REPLACE fold on title/artist bridge + probe with folded values.  
   **Test:** `DesiredRepositoryTest` curly title-bridge + identityKey fold.

### Alias tribute false positive — High (P1)

2. **“Pink Floyd” matches “Australian Pink Floyd Show”** — space-joined
   `search_aliases` + token-sequence `aliasMatches` + DAO `LIMIT 1`.  
   **Fix:** delimiter-joined aliases (`|`), whole-alias equality match,
   multi-candidate DAO (`LIMIT 32`) filter in Kotlin.  
   **Test:** `ArtistNameNormalizerTest` + `DiscoverLibraryResolverTest` tribute.

### SQL fold / Last.fm / stub ids — Med (P2)

3. **SQL name fold missing `\u02BC` / `\uFF07`** vs Kotlin fold — fixed.  
4. **Last.fm bio** kept “Read more…”; MBID-only fetch no name fallback — fixed.  
5. **`ext:` stub** hashed raw name → curly/straight split — fixed.

## Looks correct (no code change)

- Artist track membership `t3.id = cat.id` (ADR-0145) + DaosRoomTest
- ArtistPlayOrderer album blocks; album modes → `tryStartContext`
- Settings Play All radios/chips
- Home riding chrome (ADR-0114/0143)

## Intentional but surprising

- Unset Play All pref → **shuffle albums** (Apple parity)
- Legacy stored `"shuffle"` stays **song** shuffle

## Gates (must green before tag)

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```
