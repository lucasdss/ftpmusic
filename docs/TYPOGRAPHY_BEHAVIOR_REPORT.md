# Typography Behavior Report

Caveman. Post ADR-0057.

## System

| Role | Token / M3 | Family | Weight |
|------|------------|--------|--------|
| Display / hero | `textDisplay` / display* | Outfit | Bold |
| Section title | `textHeadingM` / titleLarge | Outfit | Bold/SemiBold |
| **Track primary** | **`textHeadingS` (~16)** / titleMedium | Outfit | Medium |
| Body | `textBodyM` / bodyMedium | Outfit | Regular |
| Track subtitle | `textLabelM` | Outfit | Regular |
| Nav / chip label | `textLabelM` | Outfit | Medium |
| Time / meta | `textMicro`–`textLabelM` / labelSmall | **Inter** | Normal |

Theme: `ftpTypography()` in `FtpmusicTheme`.

## Before → After (tracks)

| Surface | Before | After |
|---------|--------|-------|
| Home TrackRow | textHeadingS | textHeadingS Medium + subtitle labelM |
| Album | textHeadingS Medium | same |
| Artist | textBodyM Text | FittingText textHeadingS Medium |
| Favorites | textBodyM | textHeadingS Medium |
| Mix | 15.sp / 13.sp | textHeadingS / textLabelM |
| Playlist ListItem | M3 default | FittingText textHeadingS |
| PlayerBar / Queue rows | textBodyM | textHeadingS Medium |

## Remaining exceptions

- Syncing decorative ♫ may use large raw sp.
- Exhaustive gray hex for muted color still out of scope.
