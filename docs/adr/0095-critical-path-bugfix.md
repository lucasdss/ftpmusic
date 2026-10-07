# ADR 0095 — Critical Path Bugfix

Date: 2026-10-07
Status: Accepted
Related: ADR-0091 (Auto browse), ADR-0092 (Downloads), ADR-0093 (Cast CP), ADR-0094 (hygiene)

## Context

Review of uncommitted ADRs 0091–0094 found: NP error cleared on auto-skip;
OpenSubsonic `lyricsList` unparsed; Cast CP gated on CastPlayer timeline;
healed downloads treated offline-playable; Downloads `loadMore` TOCTOU;
Auto MediaItems with null URI marked playable.

## Decision

1. **Lyrics** — `parseResponse` accepts classic `lyrics` and OpenSubsonic
   `lyricsList.structuredLyrics` (prefer synced).
2. **Cast CP** — `ContinuousPlayGate.resolveTimeline` uses Dual index/count
   while casting; CP de-dupe uses Dual media ids.
3. **NP error** — sticky window + `playbackErrorAutoSkipInFlight`; dismiss
   affordance; clear only when settled.
4. **Offline playable** — `ContinuousPlayLoader.isPlayableOffline` requires
   non-blank `cachedFilePath` (healed pin alone insufficient).
5. **Downloads** — AtomicBoolean + Mutex around page fetch.
6. **Auto** — local file URI when path present; `isPlayable=false` when no URI.

## Consequences

- Song-id lyrics work on Navidrome OpenSubsonic responses.
- Cast Autoplay can append when Dual is on last item even if CastPlayer empty.
- Users see skip-failure reason briefly; can dismiss.
- Offline CP no longer queues healed ghosts.
