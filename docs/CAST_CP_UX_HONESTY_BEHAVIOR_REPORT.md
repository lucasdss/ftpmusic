# Cast Continuous Play — UX Honesty Behavior Report

Caveman. Follow-up step 1 after Round 2. Gate unchanged (ADR-0031).

## Ship

| Surface | Behavior |
|---------|----------|
| Autoplay Switch | `enabled = false` when `isCasting` |
| Caption | “Unavailable while casting” (`queue_autoplay_cast_unavailable`) |
| Section chrome | Muted (`NavUnselected`) while casting |
| Preference | Still shown checked if CP on; applies after Cast ends |
| Clear Autoplay | Kept for existing Dual autoplay rows |
| `ContinuousPlayGate` | Still `isCasting → false` |

## Why

Switch ON while Cast never loads CP was dishonest. Spotify-Cast-aligned: leave
CP off on Cast; fix UI. Apple-like Cast CP enable = later step.

## Coverage

`PlayerSurfacesUxTest`: casting → caption + switch click no-op; non-cast toggle still works.

## Follow-up

**Done (ADR-0093 / CAST_CP_STEP2):** Cast CP append enabled; switch interactive
while casting. See `docs/CAST_CP_STEP2_BEHAVIOR_REPORT.md`.
