# Cast Continuous Play Step 2 — Behavior Report

Caveman. ADR-0093. Supersedes Step 1 honesty disable.

## Ship

| Surface | Behavior |
|---------|----------|
| ContinuousPlayGate | Cast allowed |
| Queue Autoplay switch | Enabled while casting |
| Caption unavailable | Removed |
| appendToContext | Dual + Cast Add (existing) |
| Flatten notice | Kept |
| Gate timeline (ADR-0095) | Dual index/count while casting (not CastPlayer) |

## Edge

Empty journal → no append, flag unset (retry). Offline Cast → localOnly filter.
Disconnect mid-append → existing Cast restore path.
