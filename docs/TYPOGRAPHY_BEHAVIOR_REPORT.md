# Typography Behavior Report

Caveman. Post ADR-0057 + ADR-0058.

## System

| Role | Token / M3 | Family | Weight |
|------|------------|--------|--------|
| Display / hero | `textDisplay` / display* | Outfit | Bold |
| Section title | `textHeadingM` / titleLarge | Outfit | Bold/SemiBold |
| **Track primary** | **`textHeadingS` (~16)** / titleMedium | Outfit | **Medium** |
| Body | `textBodyM` / bodyMedium | Outfit | Regular |
| Track subtitle | `textLabelM` | Outfit | Regular |
| Nav / chip label | `textLabelM` | Outfit | Medium |
| Time / meta | `textMicro`–`textLabelM` / labelSmall | **Inter** | Normal |

Theme: `ftpTypography()` → `buildFtpTypography` (remembered on families + width).
**No baked `TextStyle.color`** (ADR-0058) — LocalContentColor wins (nav teal).
LineHeight = `fontBase * LINE_HEIGHT_MULT * widthFactor`.

## Before → After (tracks / correctness)

| Surface | Before | After |
|---------|--------|-------|
| Theme styles | Foreground/Muted baked | Unspecified color |
| Home TrackRow | textHeadingS Medium | same |
| Profile Recently Played | textBodyL | textHeadingS Medium + labelM |
| PlayerBar / Mini / Cast mini | textHeadingS (Normal) | textHeadingS Medium |
| Library artist/playlist/radio | textHeadingS | + Medium |
| Search track/album/artist/playlist | headingS / bodyM subtitle | Medium + labelM subtitle |
| Genre artist rows | textHeadingS | + Medium |
| Mix / Artist sheets | bodyM subtitles | textLabelM |
| Album / Artist / Favorites / Mix / Playlist / Queue | textHeadingS Medium | same |

## Remaining exceptions

- Syncing decorative ♫ may use large raw sp.
- Exhaustive gray hex for muted color still out of scope.
- Settings value token hierarchy polish (P3 product).
