# ADR 0098 — Search Recent History On Commit Only

Date: 2026-10-08  
Status: Accepted  
Related: docs/SEARCH_RECENT_COMMIT_BEHAVIOR_REPORT.md

## Context

Debounced typeahead correctly refreshes live results, but `search()` always
persisted the query to `recent_searches` on online success. Pausing mid-word
wrote partials into history. Local-only searches never wrote history at all.

YouTube Music: typeahead updates results; recent history is written on commit
(keyboard Search, suggestion/chip/history selection), not every settled partial.

## Decision

1. `SearchViewModel.search(commitRecent: Boolean = false)`.
2. Typeahead debounce → `commitRecent = false`.
3. Commit (`true`): IME Search, decade/tag/mood chips, `onRecentTap`, Home
   `initialQuery`.
4. `retrySearch` and offline/local-only mode flip → `false` (no history churn).
5. `saveRecentSearch` when `commitRecent` on both online success and local-only
   success.

## Consequences

- Recent list stays intentional and YT-like.
- Live search-as-you-type unchanged (300ms, min length 2).
- Existing cap (10) and dedupe-to-front behavior unchanged for commits.
