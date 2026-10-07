# SEARCH_RECENT_COMMIT — Behavior Report

Status: Delivered  
Date: 2026-10-08  
Related: ADR-0098

## Symptom

Typing in Search felt like every character ran search and filled Recent with
partials (`be`, `bea`, `beat`…).

## Root cause

- Typeahead already debounced **300ms** + min len **2** — live results OK.
- Bug: shared `search()` always called `saveRecentSearch` on online success.
- Typeahead used that path → each pause while typing wrote history.
- Local-only path never saved recents even on intentional search.

## YT Music parity

| Behavior | Target |
|---|---|
| Live results while typing | keep (debounce) |
| Recent history | **commit only** (IME Search, chip, recent tap, Home initialQuery) |
| Typeahead | no history write |
| Local-only commit | write history |

## Fix

`search(commitRecent: Boolean = false)`  
- Typeahead / retry / offline flip → `false`  
- IME / chips / recent / initialQuery → `true`  
- Save on online **and** local-only success when `commitRecent`

## Verification

- Unit: typeahead leaves recent empty until commit; local-only commit persists
- Cap / dedupe tests use `commitRecent = true`
