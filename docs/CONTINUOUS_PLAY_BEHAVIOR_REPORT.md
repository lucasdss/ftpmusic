# Continuous Play — Behavior Report

Date: 2026-10-01
Related: ADR-0052, ADR-0053, `docs/SURPRISE_ME_BEHAVIOR_REPORT.md`, ADR-0039

## Scope

When playback reaches the **last** timeline item (or `STATE_ENDED` on last) and
Continuous Play is ON, append up to 10 journal-selected tracks to **CONTEXT**
stamped as **Autoplay** (`is_autoplay`). Never Priority. Separate from Surprise Me.

## Settings + Queue UI

| Control | Storage | Runtime |
|---|---|---|
| Continuous Play toggle | `KEY_CONTINUOUS_PLAY_ENABLED` | `PlaybackManager.continuousPlayEnabled` |
| Journal history size | `KEY_QUEUE_JOURNAL_CAP` | `PlaybackManager.setJournalCap` |
| Queue · Autoplay Switch | same key | `PlaybackViewModel.setContinuousPlayEnabled` |
| Clear Autoplay | — | `clearAutoplayQueue()` removes `is_autoplay` rows only |

Hydrated at boot via `PreferenceBootstrap.hydrateJournalAndContinuousPlay()`.

## Journal write

Upsert on sourced context start when **both** `sourceType` + `sourceId` set:
`playAlbum`, `shuffleAlbum`, `pushContext`, `playSingleTrack`, Surprise Me,
**genre mixes** (`genremix` / mix id).

**Not journaled:** add-to-queue, play-next, radio, Continuous Play appends.

## Trigger (MediaService)

`maybeLoadContinuousPlay`:

1. Gate via `ContinuousPlayGate` (!cast, enabled, last index, !already loaded).
2. IO: journal → `JournalTrackSelector` → `ContinuousPlayLoader.resolve`
   (offline: downloaded/cached only).
3. Empty candidates → **do not** set `hasLoadedContinuation` (retry on
   STATE_ENDED / later transition).
4. Main: one `appendToContext(..., asAutoplay=true)` → then set flag.

## Queue sections (ADR-0053)

1. **Queue** — Priority  
2. **Continue Playing** — context `!isAutoplay`  
3. **Autoplay** — context `isAutoplay` + toggle

## Cast

Continuous Play skipped while casting (`ContinuousPlayGate`).

**UI honesty (2026-10):** Queue sheet Autoplay Switch is disabled while casting
with caption “Unavailable while casting” so the preference ON state does not
imply CP will extend the Cast timeline. Preference still applies after Cast ends.

## Tests

`ContinuousPlayLoaderTest`, `ContinuousPlayGateTest`, `ContinuousPlayTest`,
`QueueJournalTest`, QueueProjection autoplay flags.
