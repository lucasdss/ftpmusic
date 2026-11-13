# POST 2.1.7 → 2.1.8 release review

**Baseline:** testing cut `2.1.7` / `03fd3cb` (versionCode 34)  
**Delta:** `858f141` (artist bio/chrome) + `1241da6` (Android Auto Home / alert / reactions / voice)  
**Target:** `2.1.8` (versionCode 35)

## Cross-model

| Pass | Role |
|------|------|
| Discover | Diff walk `03fd3cb..HEAD` |
| Challenge | Attack severity / must-fix set |
| Audit | MediaService / Auto / artist deep review |

## Confirmed → fixed in 2.1.8

1. **Connect alert burns fingerprint when notify fails** — mark seen only after successful `notify`.
2. **Artist collapsing chrome measure loop** — unbounded chrome measure (NavHost / ADR-0107).
3. **Auto Surprise Me LocalOnly** — presence + expand use `getRandomCachedTracks` when LocalOnly.
4. **Surprise Me browsable empty folder** — `isBrowsable=false` (playable-only leaf).
5. **Favorite radio item/expand miss** — fall back to `RadioFavoriteDao`.
6. **Voice album extras** — album focus expands album locally / `search3.album` before track path.

## Next iteration (deferred P2)

Track these for a later cut — **not** in 2.1.8:

| ID | Topic | Notes |
|----|-------|-------|
| N1 | Fingerprint copy (“new car”) | Settings/ADR copy implies per-car; fingerprint is controller **package** (usually Gearhead) → once per Auto app. Align strings/docs or accept as intentional. |
| N2 | Connect-path EncryptedSharedPreferences I/O | `onConnect` → sync `SecureStorage` + notify can slow Auto attach. Move persist/notify off binder path. |
| N3 | Starred artist `playable=true` always | Home Favorite artists **section** already omits when empty; individual rows should gate playable like library `artistFolder` (`albumCount > 0`). |
| N4 | Home `ORDER BY RANDOM()` latency | `getRandomTracks` on full `tracks` table for Surprise presence/expand — large libraries may jank Auto Home browse. |

Also deferred (unchanged): SLOT_FORWARD/BACK OEM variance (HU checklist), broader Auto offline cache filtering, numeric HTML entities beyond `&#39;`.

## Fatal

None.
