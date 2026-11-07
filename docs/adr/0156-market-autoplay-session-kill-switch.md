# ADR 0156 — Market Autoplay timing + session kill switch

Date: 2026-11-07
Status: Accepted
Related: ADR-0052, ADR-0053, ADR-0094,
docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md

## Context

Diagnostics (v2.1.0) showed Continuous Play appending `getSimilarSongs2`
results while album/playlist rows remained (`remaining ≤ 3` low-water, and
1-track CONTEXT starts). Users reported Next playing “random” music before
the playlist ended. Spotify Autoplay and YouTube Music Autoplay start **after**
the selected queue ends; YTM also exposes an easy Queue toggle for the current
listen.

## Decision

1. **Master Settings** Continuous Play stays **ON by default** (market).
2. **First Autoplay burst** only when the playhead is on the last
   **non-autoplay** timeline row and no Autoplay tail is already queued.
   Mid-album `remaining ≤ 3` no longer arms the first burst.
3. **Radio re-arm:** once Autoplay rows exist, further bursts may use
   `LOW_WATER_MARK` (3) near the end of the Autoplay tail.
4. **Session kill switch:** Queue · Autoplay switch sets
   `sessionAutoplayEnabled` only (does not rewrite Settings). OFF clears the
   Autoplay section and bumps `continuousPlayEpoch` so in-flight fetches drop.
   Each new `playAlbum` / `shuffleAlbum` / `playSingleTrack` / `pushContext`
   re-inits session from the master flag.
5. Master OFF forces session OFF and clears Autoplay.

## Consequences

- Nov 5/7 failure modes (radio before finite end) are gated closed by default
  timing; users who want no radio for one album turn Queue Autoplay off.
- Amends ADR-0053 decision #1 (CP-5 low-water applies to Autoplay re-arm only)
  and #3 (in-queue toggle is session-scoped, not Settings).
