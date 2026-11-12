# Android Auto Voice + In-Auto Search Review

Date: 2026-11-12
Related: ADR-0131 (browse ≠ voice), ADR-0132, ADR-0136

## Paths (unchanged split)

| Path | Entry | Behavior |
|------|-------|----------|
| In-Auto browse search | `onSearch` / `onGetSearchResult` | Local-first list; optional `search3` tracks when empty + online |
| Voice | `MEDIA_PLAY_FROM_SEARCH` → `searchAndNavidromeExpand` | Top hit → album expand (or artist expand); Dual adopt as Voice search |

## Hardening shipped

1. Parse Assistant extras: `EXTRA_MEDIA_ARTIST` / `ALBUM` / `TITLE` / `FOCUS`.
2. Artist-only focus → expand artist albums/tracks (local then network).
3. Empty query+extras → log and ignore (no silent success).
4. No-hit voice → explicit warn log (was silent empty).
5. LocalOnly still skips `search3`.

## Explicit non-goals

- Do not converge voice album-expand with browse search result lists (ADR-0131).
- No Car App `SearchTemplate` / Search browse folder (media templates use HU search UI).

## Tester checklist

- [ ] “Play \<song\>” → album context starts
- [ ] “Play \<artist\>” / artist focus → artist tracks start
- [ ] Offline LocalOnly + unknown query → no network; no crash
- [ ] In-Auto typed search still returns list results
