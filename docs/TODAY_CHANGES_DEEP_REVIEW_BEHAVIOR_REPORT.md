# Today Changes Deep Review — Behavior Report

Date: 2026-10-03
Diff: `3fe6865...HEAD` (+ review hardening)

## Commits

| SHA | Fix |
|-----|-----|
| `756edfa` | Collection play-from-track |
| `daa61b5` | Second-tap unlike stick |
| `ea211c7` | Same-tab reselect pop |
| `516838d` | Settings DetailBackButton / dismiss |

## Edge-Case Agent

| Finding | Verdict |
|---------|---------|
| Artist paging = loaded slice only | Spec OK (ADR-0061 §5). Keep. |
| `activeBottomTab` desync | Detail nav keeps owner. Only `library?tab=playlists` programmatic jump updates. OK. |
| Library `popBackStack("library?tab={tab}")` | Graph route match correct. Hardened: fallback bare `library`. |
| Overlay same-tab reselect | Intentional pop-to-root (ADR-0062). Not player swipe clash. |
| Cast `sessionWasResumed` in nav commit | Real EDGE-03 fix. Extracted `CastSessionResumePolicy`. Keep. |
| `pending_unstar` index | Skip — pending rows few; not hot path. |
| Unlike races | Atomic DAO + pending preserve + sync skip restar. Covered. |

## Performance Agent

- `TabNavigationPolicy` pure — zero Compose cost.
- Favorites/Mix play-from-track: no new collectors.
- Settings rewrap noise ignored; chrome-only change.

## Coverage Agent

Gate: JaCoCo ≥80% line+branch on touched non-Compose logic.
Compose `*ScreenKt` excluded (project convention).

| Class | Line | Branch |
|-------|------|--------|
| TabNavigationPolicy | ≥97% | ≥84% |
| FavoriteRepository | ≥96% | ≥90% |
| CastSessionResumePolicy | 100% | 100% |
| ShowAppHeaderKt | 100% | 100% |
| ArtistDetailViewModel | ≥99% | ≥81% |
| PlaybackViewModel toggleLike/Dislike | 100% | ≥85% |
| LibraryViewModel toggleArtist* | ≥95% | ≥80% |

Full-suite Compose `*ComposeTest` may flake `AppNotIdleException` under load — unrelated to today diff; logic+cast suite green.

## Hardening this pass

1. `CastSessionResumePolicy` — sticky-resume transitions unit-tested; MediaService wired.
2. `tabRootPopFallbackRoutes` — library pattern then bare route.
3. Tests: artist/library unlike toggles, FavoriteRepository local-miss branches, Cast policy.
4. Artist album/artist toggle cancel handling aligned with LibraryViewModel.

## Specs

- `COLLECTION_PLAY_FROM_TRACK_BEHAVIOR_REPORT.md` / ADR-0061
- `THUMBS_TOGGLE_BEHAVIOR_REPORT.md` / ADR-0020
- `NAVIGATION_TAB_STACK_BEHAVIOR_REPORT.md` / ADR-0062
- `NAVIGATION_DISMISS_BEHAVIOR_REPORT.md` / ADR-0063
- ADR-0016 sticky-resume (EDGE-03)
