# Navigation Dismiss Behavior Report

Caveman terse. ADR-0063.

## Verdict

Dual dismiss = market-correct (Spotify / Apple Music / YT Music).
Not one-size-fits-all. Surface type picks affordance.

## Route → dismiss matrix

| Route / surface | Class | Dismiss |
|-----------------|-------|---------|
| `home` / `search` / `library` / `favorites` | TAB | none |
| `album/` `artist/` `playlist/` `genre/` `mix/` | PUSH_WITH_BACK | `DetailBackButton` → `popBackStack` |
| `profile` / `customMixes` | PUSH_WITH_BACK | `DetailBackButton` → `popBackStack` |
| `settings` | PUSH_WITH_BACK | `DetailBackButton` + system Back → `popBackStack`; **no** AppHeader |
| `nowplaying` | PLAYER_SWIPE_MINIMIZE | swipe-down **or** ↓ chevron → minimize (mini bar) |
| Mini PlayerBar | expand half | tap → `navigate("nowplaying")` |
| Queue sheet (in player) | SHEET | swipe-down / handle — closes sheet, not route |
| Lyrics overlay | OTHER | Close X; blocks player swipe while open |
| ModalBottomSheet actions | SHEET | M3 swipe / scrim |
| splash / connect / syncing / rebuildmix | OTHER | auto-nav / complete |

## Market note

- Content stack: top back chrome. Match.
- Full player: demote overlay via swipe-down. Match.
- Unifying both → anti-pattern vs peers.

## Edge cases

- Queue / lyrics open → player vertical dismiss disabled.
- Settings: gear from any tab → stack push; Back returns prior route.
- Profile from Settings → Back → Settings (not tab).
- System Back ≡ DetailBackButton on stack routes.
- Expand asymmetric: mini = tap only; collapse = swipe + ↓ (peer apps same).

## Consistency fix (this pass)

Settings was AppHeader + no chevron. Now Profile pattern:
hide AppHeader, title row `DetailBackButton` + "Settings".
