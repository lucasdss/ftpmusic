# ADR 0028 — Cast Connect Timeout Contract & GMS Dynamite Failure Messaging

- Status: Accepted
- Date: 2026-08-28
- Deciders: App architect
- Context: [CAST_CONNECT_BEHAVIOR_REPORT.md](../CAST_CONNECT_BEHAVIOR_REPORT.md);
  extends [0026-cast-media-session-attachment-and-connect-serialization](0026-cast-media-session-attachment-and-connect-serialization.md)

## Decision

Keep the deterministic 10s + 1s grace connect timeout
(`MediaService.castConnectTimeoutMs`, `startCastConnectTimeout`) as the single
authoritative failure detector for manual Cast connects, and surface
actionable recovery text when it fires (`buildCastConnectTimeoutMessage`).
Do NOT attempt to detect GMS-side failures (dynamite corruption, phantom
sessions) from app code beyond what SessionManager callbacks already provide.

## Why (rationale)

1. **GMS failures are invisible by contract.** A corrupted Cast dynamite
   module ("Module APK has been modified", DynamiteLoaderV2Impl) makes
   `CastSession.<init>` throw inside GMS; the app's SessionManagerListener
   receives NO onSessionStarted/onSessionStartFailed/remote=true. There is no
   API to read that exception (it never crosses the Binder). The timeout is
   the only reliable signal.
2. **Determinism beats guessing.** Route.select() -> session-establishment
   latency varies (slow receivers, proxy start, queue load). The 10s timeout
   + 1s grace re-check + `hasLiveCastSessionOn(target)` gate fails exactly
   when the TARGET session never became live — no false positives on slow
   healthy connects (Edge-03).
3. **Message is the product surface.** For invisible GMS failures the app can
   only tell the user what to check: same-Wi-Fi (network isolation) and
   Google Play Services repair path (Storage -> Clear cache / update). That is
   exactly what the timeout message now says.
4. **Version pin unchanged.** cast-framework stays forced at 21.5.0
   (build.gradle.kts): the 22.1.0 auto-resume regression (error 2152,
   googlecast/CastVideos-android#144) is a real runtime bug; the 21.5.0 pin is
   NOT implicated in this failure (the log shows an integrity failure inside
   the device-side dynamite, not a client/dynamite version negotiation error).

## Consequences

- Timeout dialog text changes to include the GMS-repair + same-Wi-Fi hint.
- Timeout log line adds a DynamiteModule/DynamiteLoaderV2Impl diagnostic.
- Two new unit tests pin the message shape (device name verbatim, hint
  present) in `CastConnectFailureTest`.
- Retrying a connect while GMS is broken remains futile until GMS is repaired
  (device-side); the app correctly fails fast and tells the user why.

## Alternatives considered

- **Probe GMS health before connect** — rejected: no API exposes dynamite
  integrity; GoogleApiAvailability only reports play-services presence.
- **Shorter timeout (5s)** — rejected: slow-but-healthy receivers would
  false-fail; the 1s grace already covers mid-establishment sessions.
- **Surface "GMS broken" specifically** — rejected: cannot distinguish from
  network isolation/phantom-session cases; combined hint is honest.
- **Bump to cast-framework 22.1.0 to "match" media3** — rejected: reintroduces
  the 2152 resume regression; not the cause of this failure.
