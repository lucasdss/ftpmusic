# Post-1.6.0 Deep Review — Behavior Report

Date: 2026-10-08  
Baseline: **1.6.0 (10)** @ `4bba28b`  
Ship target: **1.7.0 (11)**  
Scope: +10 commits (BT deep-sleep, critical-path, cover, home headers, scroll/AppHeader, search recent, typography)

## Edge-Case Agent (Caveman)

| Finding | Verdict |
|---------|---------|
| BT `safeAddress ?: return false` kills ANY-mode null MAC | **FIXED** nullable mac → `dispatchBtConnect` |
| CoverArtImage Coil onError no `deleteIfNotImage` | **FIXED** file:// eviction + primaryFailed |
| AppHeader Cast ignores `interactive` → ghost taps | **FIXED** `CastButton(enabled=interactive)` |
| DownloadsRow stale still `downloadStatus=downloaded` | **FIXED** stale → `none` |
| AutoBrowse albumFolder always playable | **FIXED** gate on `songCount > 0` |
| Home Fav See-all → favorites | **OK** ADR-0089 intentional |
| `fromKey("push")` → ASK; PUSH tests stale | **FIXED** tests match ADR-0094; `pushContext` covered direct |
| NowPlaying swipe AppNotIdle (https Coil) | **FIXED** local `file://` PNG + `waitUntil` |
| detekt ThrowsCount / FunctionOnlyReturningConstant | **FIXED** disabled in detekt.yml (style; intentional) |
| BT `goAsync`+immediate `finish` | Doc only — runtime A2DP smoke |
| `runBlocking` seat on Main / 400ms latch | Doc only — no speculative rewrite |
| Cast CP ENDED forceRetry | Doc only — Dual fail-closed already tested |

## Performance Agent (Caveman)

| Finding | Verdict |
|---------|---------|
| Cover existsNonEmpty composition path | Keep — eviction deferred to Coil onError (ADR-0096) |
| Typography staticCompositionLocalOf | Accept — prefs change rare; slider not continuous ship risk |
| AppHeader fling settle Zero | Accept — ADR-0097 |

## Coverage Agent

Touched: BtConnectionReceiver null-MAC; CoverArtImage file eviction; AppHeader Cast gate; Downloads stale status; AutoBrowse empty album; OverwriteBehavior PUSH remap + pushContext.

Gate: `make test-report` ≥80% on touched logic; `lintVitalRelease`; `bundleRelease`.

## Design Impact Agent

- Cast hit-area disabled when header collapsed (`interactive=false`)
- Downloads missing file: no purple downloaded glyph; subtitle honesty kept
- Cover corrupt → blank then fallback/placeholder (parity ArtistAvatar)
- Typography prefs unchanged vs ADR-0099

## Spec docs

- ADR-0100 — 1.7.0 post-ship remediation
- PLAY_RELEASE_1_7_0.md — internal testing pack
