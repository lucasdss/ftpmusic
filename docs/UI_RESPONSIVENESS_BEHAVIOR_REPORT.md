# UI Responsiveness Behavior Report

Caveman terse. Phone-first shell. Post ADR-0049 harden.

## Shell

- Root: `MainActivity` → `FtpmusicNavHost` Scaffold.
- Bottom `NavigationBar` only. No NavigationRail / WindowSizeClass.
- Tabs: home, library, favorites, search, settings.
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
- Use: Row+weight, nav labels, chips, badges, titles beside icons.
- One-word unbreakable strings: shrink first, then ellipsis. No clip.

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

## Out of scope

- strings.xml i18n.
- Tablet NavigationRail / multi-pane.
