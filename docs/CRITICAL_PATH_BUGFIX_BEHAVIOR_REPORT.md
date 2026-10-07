# Critical Path Bugfix — Behavior Report

Caveman. ADR-0095 (+ post-ship revision).

## Fixes (initial ship + revision)

| Bug | Fix |
|-----|-----|
| lyricsList unparsed | parse structuredLyrics prefer synced |
| lyrics start String | parseLyricStartMs Number\|String |
| Cast CP CastPlayer gate | Dual SoT via resolveTimeline |
| Cast ENDED pre-gate | Dual resolveTimeline before maybeLoad |
| Cast Dual index miss → 0 | fail closed (−1) while casting |
| NP error clear-on-skip | sticky 8s + autoSkip flag + dismiss |
| NP sticky forever / dismiss resurrect | Handler sticky expiry + provider clearPlaybackError |
| heal offline lie | isPlayableOffline = path non-blank |
| Downloads TOCTOU | AtomicBoolean + Mutex |
| Downloads loading stuck | finally loading=false |
| Downloads clearAll race | mutex + loadGeneration discard |
| Auto null URI playable | isPlayable=false when no stream |
| Auto file:// URI | stream URI only (CacheDataSource) |
| similar song single Map | List\|Map normalize + Track enrich |
| QueueScreen delete coverage | ports in PlayerSurfacesUxTest |

## Coverage

LyricsFetcher lyricsList + String start. ContinuousPlayGate Dual ENDED.
PlayerHolderPlaybackErrorTest sticky scheduler. ContinuousPlayLoader path.
AutoBrowseCatalog stream-uri / empty playlist. DownloadsViewModel exception /
clearAll. ScrobbleContinuous single-song Map. PlayerSurfacesUxTest sheet ports.
