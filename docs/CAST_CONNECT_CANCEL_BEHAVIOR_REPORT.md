# Cast Connect Cancel — Behavior Report

Caveman. ADR-0124. Market-aligned abort. Supersedes ADR-0026 Cancel = flags-only.

## Market

| App | Abort control |
|-----|---------------|
| Spotify | Stop cast / system notification Stop; not wait timeout |
| YouTube | Disconnect / pick phone |
| YouTube Music | This phone / Stop casting / Disconnect |
| Apple Music | AirPlay sheet → iPhone = instant local |
| Symfonium | Local device row always |

Consensus: always exit to phone/local. Never trap on Connecting until timeout.

## Our contract

| Action | Behavior |
|--------|----------|
| Stop connecting | Hard abort: clear flags + endCurrentCastSession + defaultRoute |
| This phone | Same hard abort (+ disconnect if was casting) |
| Cancel / swipe dismiss | Same hard abort + close sheet |
| Mid-connect other device | Abort current → connect new |
| Late onSessionStarted after abort | Reject: end unwanted session, stay local |
| 10s timeout | Safety net only if user never aborts |

## UX

- Connecting row + **Stop connecting** (sheet stays open).
- Sticky **This phone** at list top.
- Timeout message unchanged.
