# Critical Path Bugfix — Behavior Report

Caveman. ADR-0095.

## Fixes

| Bug | Fix |
|-----|-----|
| lyricsList unparsed | parse structuredLyrics prefer synced |
| Cast CP CastPlayer gate | Dual SoT via resolveTimeline |
| NP error clear-on-skip | sticky 8s + autoSkip flag + dismiss |
| heal offline lie | isPlayableOffline = path non-blank |
| Downloads TOCTOU | AtomicBoolean + Mutex |
| Auto null URI playable | file URI / isPlayable=false |

## Coverage

LyricsFetcher lyricsList tests. ContinuousPlayGate resolveTimeline.
PlayerHolderPlaybackErrorTest. ContinuousPlayLoader path check.
AutoBrowseCatalog null-uri / file-uri.
