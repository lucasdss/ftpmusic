# UI Responsiveness Behavior Report

Caveman terse. Phone-first shell. Post ADR-0049.

## Shell

- Root: `MainActivity` → `FtpmusicNavHost` Scaffold.
- Bottom `NavigationBar` only. No NavigationRail / WindowSizeClass.
- Tabs: home, library, favorites, search, settings.
- Hide labels pref: `KEY_NAV_HIDE_LABELS` (default OFF). ON → `label = null`; icon `contentDescription` stays.

## Scale

- Ref width 360dp.
- Width factor: `screenWidthDp/360` clamp **0.85..1.25** (`adp`).
- Text factor: width × fontCompensation (`asp`).
  - `fontCompensation = (1/fontScale.coerceIn(1..1.4)).coerceIn(0.75..1)`
- Narrow phone → downscale. Large a11y font → partial absorb (not full cancel).

## FittingText

- Start token size → shrink to min → Ellipsis.
- Use: Row+weight, nav labels, chips, badges, titles beside icons.
- One-word unbreakable strings: shrink first, then ellipsis. No clip.

## Settings DISPLAY

| Control | Persist | Consumer |
|---------|---------|----------|
| Hide navigation labels | `KEY_NAV_HIDE_LABELS` | `FtpmusicNavHost` via activity-scoped SettingsVM |

## Edge cases

- 320dp width → factor ~0.89.
- FontScale 1.3–1.5 → asp absorb + FittingText min floor.
- Process death restores hide-labels.
- Toggle live while Settings + bar visible.
- TalkBack: labels hidden still announce via icon CD.

## Out of scope this pass

- strings.xml i18n.
- Tablet NavigationRail / multi-pane.
