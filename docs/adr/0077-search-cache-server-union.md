# ADR 0077 — Search cache ∪ server union

Date: 2026-10-06
Status: Accepted
Related: ADR 0051, docs/SEARCH_BEHAVIOR_REPORT.md

## Context

Global Search showed Phase-1 Room artist hits, then replaced them with Subsonic
`search3` when the server returned any artists (`ifEmpty`). Library Artists
filter kept full `cached_artists` LIKE matches — same query, different UI.

## Decision

1. Merge server + local by **id union** (`SearchResultMerger.unionById`):
   server order first, append local-only ids.
2. Rank merged lists: exact → prefix → contains (`SearchQueryNormalizer.fold`).
3. Page size 50; `loadMoreSearchResults` advances artist, album, and song offsets.
4. Keep ADR 0051 local-only playable path unchanged (no server merge).

## Consequences

- Artists present in Room survive partial `search3` pages.
- Larger result lists until user scrolls; still capped per page.
- Ranking is in-memory (cheap for ≤~200 rows).
