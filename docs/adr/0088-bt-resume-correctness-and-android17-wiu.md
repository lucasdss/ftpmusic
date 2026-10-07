# ADR 0088 — BT Resume Correctness + Android 17 WIU

Date: 2026-10-07
Status: Accepted
Related: ADR-0087 (playback resumption), ADR-0072 (A2DP resume), ADR-0019 (FGS)
Toolchain: compileSdk/targetSdk 36; runtime Android 17 via SDK_INT guards

## Context

ADR-0087 closed the missing `onPlaybackResumption` hole but left correctness gaps
that still produce “BT connects → no media → open phone” on Pixel / Android 17:

1. Eager seat was still **async** — AVRCP could bind to an empty session.
2. Android 17 [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio):
   A2DP-started FGS is typically BFSL **without WIU** → `play()` may silence with
   no exception (`AudioHardening` in logcat). Media-key / notif tap / open-app get WIU.
3. DI-singleton `MediaSessionCallback` / `PlaybackManager` scopes were `cancel()`’d
   on service destroy → subsequent resumption futures dead until process death.
4. `BtResumePolicy` rejected null MAC even in **ANY** mode (missing CONNECT perm).
5. Resumption used raw Room URLs (no legacy proxy unwrap) and mutated player off Main.

## Decision

1. **BT cold start:** Room restore + seat on Main before external controllers
   reliably observe emptiness; `onGetSession` may wait briefly (hard cap) while
   restore in flight — fail-open on timeout.
2. **API ≥ 37:** After A2DP seat, do **not** rely solely on background `play()`;
   post/keep high-pri Resume notification (user-initiated FGS PendingIntent = WIU).
   API ≤ 36 keeps autoplay `play()` after seat.
3. **Never permanently cancel** process-scoped callback/PM coroutine scopes from
   `MediaService` teardown; reset SupervisorJob instead if needed.
4. **ANY + null MAC** uses synthetic debounce key `"*"`; SELECTED still needs MAC.
5. Resumption: Main-thread transport extras; shared URL migration; skip overwrite
   when player already has items.
6. Early `startForeground` whenever BT autoplay or media-session FGS wake is
   indicated (`foregroundRequested` or `btAutoplayRequested`).

## Consequences

- HU more likely to see metadata without opening the phone.
- On Android 17, autoplay may require Play on headset or Resume notif tap when
  WIU is denied — documented, not a silent no-op with empty session.
- `targetSdk` stays 36 until a dedicated A17 retarget pass.
