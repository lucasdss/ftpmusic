# UI Responsiveness Behavior Report

Caveman terse. Phone-first shell. Post ADR-0049 harden + nav label center fix.

## Shell

- Root: `MainActivity` → `FtpmusicNavHost` Scaffold.
- Bottom `NavigationBar` only. No NavigationRail / WindowSizeClass.
- Tabs: home, search, library, favorites (Settings = AppHeader gear; ADR-0055).
- Hide labels pref: `KEY_NAV_HIDE_LABELS` (default OFF). ON → `label = null`; icon `contentDescription = tab.label`. OFF → labels shown; icon CD null (avoid double announce).

## Scale

- Ref width 360dp.
- Width factor: `screenWidthDp/360` clamp **0.85..1.25** (`adp` + `asp`).
- `asp` = width factor only + `.sp` → **system a11y fontScale honored**.
- Layout overflow owned by `FittingText` (shrink → ellipsis), not by canceling fontScale.

## FittingText

- Start token size → shrink to min → Ellipsis.
- Zero-width (first frame) → use min size (no oversize flash).
- Measure merges `LocalTextStyle` so fit matches paint.
- Default `fillMaxWidth = true` + default `textAlign = Start`.
- **Centered chrome rule:** any slot where glyphs must sit under a centered icon/chip
  MUST pass `textAlign = TextAlign.Center` when `fillMaxWidth` is true (or use
  `fillMaxWidth = false` so M3/Column centers intrinsic width).
- Use: Row+weight (Start OK), nav labels (Center), chips, badges, titles beside icons.
- One-word unbreakable strings: shrink first, then ellipsis. No clip.

## FittingText design audit (centered chrome)

| Site | Align | Risk | Action |
|------|-------|------|--------|
| Bottom nav label `NavHost` | Center + fillMaxWidth | Was Start → left skew under icon | Fixed |
| Profile PeriodChip / StatCard | Center + bounded width | Was fixed earlier | OK |
| OverwriteModal body | Center | Intentional | OK |
| Settings value row | End | Intentional | OK |
| TypeBadge | fillMaxWidth=false | Intrinsic | OK |
| Home section headers / genre chips | fillMaxWidth=false or Start row | Content, not chrome center | OK |
| Search/Library/Player row titles | Start + weight/fillMaxWidth | Intentional LTR rows | OK |
| Syncing progress labels | Start | Content | OK |

## Settings APPEARANCE

| Control | Persist | Consumer |
|---------|---------|----------|
| Hide navigation labels | `KEY_NAV_HIDE_LABELS` | `FtpmusicNavHost` via activity-scoped SettingsVM |

## Edge cases

- 320dp width → factor ~0.89.
- FontScale 1.3–2.0 → tokens grow with a11y; FittingText shrinks constrained slots.
- Process death restores hide-labels (VM init).
- Toggle live while Settings + bar visible.
- TalkBack: icon-only announces via icon CD; labeled mode uses label.
- Nav label vs icon centerX must match within 2dp (`NavBarLabelsTest`).

## Out of scope

- strings.xml i18n.
- Tablet NavigationRail / multi-pane.
- Changing FittingText global `fillMaxWidth` default.
