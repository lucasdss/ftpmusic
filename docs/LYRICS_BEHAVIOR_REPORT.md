# Lyrics Behavior Report

Status: hardened on-demand path (getLyrics). No getLyricsBySongId this pass.

## E2E flow

1. Now Playing mounts → `LaunchedEffect(artist, title, trackId)`.
2. Missing artist/title → clear lines/text, stop loading.
3. Cache lookup by `trackId` (Room `lyrics_cache`):
   - `cacheVersion < CURRENT (2)` → delete → treat miss.
   - Hit → show synced lines or unstructured text. No `touch()` (fetchedAt = fetch time only).
   - Hit + `rawJson` + no synced → `reparseFromRaw` may upgrade to synced.
   - Hit age > 24h → child coroutine refresh (cancellable with effect); UI keeps stale until next open.
4. Miss → `getLyrics(artist, title)` → parse → cache → UI.
5. Overlay: LYRICS chip → `LyricsContent` (synced LazyColumn or plain scroll).

## Formats

| Input | Result |
|-------|--------|
| Structured `line[]` with real `start` ms | Synced lines |
| Structured all `start=0` + LRC in values | LRC → synced |
| Structured all `start=0` + no LRC | Unstructured (no fake sync) |
| `value` / `text` plain | Unstructured or LRC if stamps present |
| Empty / missing lyrics + trackId | Negative cache (empty unstructured + rawJson) |

Not supported: OpenSubsonic `getLyricsBySongId`, embedded USLT/sidecar `.lrc` client-side, word karaoke, `[hh:mm:ss]`.

## Cache contract

- PK: `trackId`. API key still artist+title (wrong lyrics possible for same title multi-disc).
- `fetchedAt`: set on network put only. TTL = 24h from fetch, not access.
- No LRU eviction / prune / quota.
- Prefs: `last_lyrics_fetch_ms` metrics only. No enable toggle.

## UI

- Timed lines → label "Synced lyrics"; plain → "Lyrics".
- Auto-scroll follows active line; pauses ~3s after user scroll.
- List state keyed by `trackId`.
- Lines cleaned at parse time (no per-tick HTML strip on synced rows).

## Edge map (hardened)

| Case | Behavior |
|------|----------|
| Cancel mid-fetch | Rethrow `CancellationException` |
| Network fail | Empty UI; log warn |
| Offline + cache hit | Show cache |
| Offline + miss | Empty UI |
| Null trackId | Fetch OK; no cache write |
| Process death | Overlay closed; cache hit on remount |
| All-zero timestamps | Unstructured fallback |

## Gaps (deferred)

- `getLyricsBySongId` / songId-keyed API
- Settings enable toggle + clear-cache UI
- Cache prune
- RTL / hours LRC / enhanced word tags
- Dedicated LyricsRepository / ViewModel

## Tests

- `LyricsTest` — parse / clean / binsearch
- `LyricsFetcherTest` — fetch/cache/TTL/reparse/negative/all-zero
- Gate: Jacoco ≥80% on modified lyrics production files
