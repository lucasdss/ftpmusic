# ADR 0095 — Critical Path Bugfix

Date: 2026-10-07
Status: Accepted (revised same day)
Related: ADR-0091 (Auto browse), ADR-0092 (Downloads), ADR-0093 (Cast CP), ADR-0094 (hygiene)

## Context

Review of uncommitted ADRs 0091–0094 found: NP error cleared on auto-skip;
OpenSubsonic `lyricsList` unparsed; Cast CP gated on CastPlayer timeline;
healed downloads treated offline-playable; Downloads `loadMore` TOCTOU;
Auto MediaItems with null URI marked playable.

Post-ship review of `34e2116` found residual bugs in those fixes (file:// Auto
URI, sticky error never clearing / dismiss resurrect, STATE_ENDED CastPlayer
pre-gate, Downloads loading stuck + clearAll race, similar-songs single Map).

## Decision

1. **Lyrics** — `parseResponse` accepts classic `lyrics` and OpenSubsonic
   `lyricsList.structuredLyrics` (prefer synced). Line `start` accepts Number
   or numeric String.
2. **Cast CP** — `ContinuousPlayGate.resolveTimeline` uses Dual index/count
   while casting (including STATE_ENDED pre-gate); CP de-dupe uses Dual media
   ids. Casting mediaId miss → Dual index `-1` (fail closed), never coerce to `0`.
3. **NP error** — sticky window + `playbackErrorAutoSkipInFlight`; dismiss
   affordance; Handler auto-clear at sticky expiry without requiring READY;
   dismiss/expiry patches provider StateFlow so lightweight poll cannot resurrect.
4. **Offline playable** — `ContinuousPlayLoader.isPlayableOffline` requires
   non-blank `cachedFilePath` (healed pin alone insufficient).
5. **Downloads** — AtomicBoolean + Mutex around page fetch; `loading=false` in
   `finally`; `clearAll`/`remove` under mutex + generation discard; pending refresh.
6. **Auto** — **stream URI only** (CacheDataSource / `streamCacheKey`); never
   `file://`. `isPlayable=false` when no stream URI. Empty playlists not playable.

## Consequences

- Song-id lyrics work on Navidrome OpenSubsonic responses (and String timestamps).
- Cast Autoplay can append when Dual is on last item even if CastPlayer empty,
  including ENDED retry path.
- Users see skip-failure reason briefly; dismiss sticks; banner expires without READY.
- Offline CP no longer queues healed ghosts.
- Auto play hits SimpleCache via stream URLs like in-app playback.
