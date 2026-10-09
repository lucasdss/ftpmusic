# LIST_CHROME — Behavior Report (caveman)

**ADR:** 0104  
**Defaults:** reactions ON · duration ON  
**NP / mini:** thumbs always (ignore prefs)

## Prefs

| Key | Storage | Default | Settings label |
|---|---|---|---|
| `showListReactions` | `list_show_reactions` | true | Like and dislike on track lists |
| `showListDuration` | `list_show_duration` | true | Duration on track lists |

SoT: `SettingsViewModel` → `FtpmusicTheme` → `LocalListChromePrefs`.

## SongListRow layout

```
title (weight) | duration?     ← title line
subtitle?
meta: cache? · like? · dislike?
trailing ⋮ / custom
```

- Reactions OFF → ignore `isLiked`/`onLike` (title space wins).
- Duration OFF → omit duration even if `durationLabel` set.

## Surfaces

| Surface | Reactions wired | Notes |
|---|---|---|
| Album / Artist / Mix | yes (existing) | meta thumbs |
| Home `TrackRow` | yes (VM sets) | row helper ready |
| Search songs | yes | coordinator |
| Playlist detail | yes | coordinator |
| Downloads | yes | coordinator |
| Favorites tracks | trailing thumb | gated by `showListReactions` |
| Add Songs sheet | no | picker |
| Queue sheet | no | not SongListRow |

## Edge

- Process death → SecureStorage restore.
- Optimistic toggle + Room watch merge via `FavoritePendingStore` inside coordinator.
- Reactions OFF on Favorites → trailing action gone; unlike/undislike via NP or re-enable pref.

## Design

- Duration `Muted` / `textLabelM`, centerVertically with title.
- Reaction hits via existing `ReactionGlyphButton` (≥40dp min).
