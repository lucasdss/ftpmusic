# SCROLL_PERF_PASS8 — Behavior Report

Status: Delivered (pass 8)
Date: 2026-10-18
Related: ADR-0147, ADR-0107, ADR-0096, ADR-0102

## Goal

Deep review every scroll surface. Consistent smooth perf. No glitch. No stuck
UI. Likes (artists/albums/radio) fast. Do not break design or features.

## Findings (audit)

- Home + Library already Pass 1–7 tuned (cacheWindow, contentType, key, decode
  size, EQ pause, plain Row for ≤10 recent, allowFallbackFetch on fling).
- Gaps: Favorites / Artist / Playlist / Mix / Genre / Downloads / Search lists
  missing `LazyLayoutCacheWindow`. Likes rows over-decode cover (request 200,
  display 48). FittingText still on scroll-path rows (ADR-0096 violation).
  loadMore dedup O(n*m). loadMore snapshotFlow no `distinctUntilChanged`.

## Changes

### 1. Consistent viewport prefetch (ADR-0107 Pass 5 pattern)

Add `LazyLayoutCacheWindow(aheadFraction=0.5f, behindFraction=0.2f)` to:

- Favorites: Desired / Liked / Disliked lists.
- ArtistDetail: TracksTab + AlbumsTab grid (new grid state).
- PlaylistDetail, MixDetail, GenreDetail (grid + artists), Downloads, Search.

Same values Home/Library already use. Uniform prefetch → uniform fling.

### 2. Likes listing fast

- Favorites `rememberCoverArtUrl(..., 200)` → `48` (track + album row). Decode
  matches displayed 48.dp — half RAM, no oversize decode.
- Downloads `rememberCoverArtUrl(...)` → `48`.
- Artist/Genre album grid `rememberCoverArtUrl(..., 300)` → `160` (matches
  decodeSize 160.dp).
- Home starred-albums inline LazyRow: request size = cardDecode px + pass
  `allowFallbackFetch = !scrollInProgress`.

### 3. Remove FittingText from scroll-path rows (ADR-0096)

Fixed `Text(maxLines=1, overflow=Ellipsis)` replaces FittingText on repeated
list rows:

- Favorites `ArtistFavoriteRow` name; `RadioFavoriteRow` name + streamUrl.
- Search genre rows (name + counts); artist rows (name + albums).

Same fontSize/color kept. Design unchanged (ellipsis vs shrink). Non-lazy
chrome FittingText (Search filter chip, section headers) left as-is.

### 4. loadMore correctness + cost

- Favorites loadMoreLiked/loadMoreDisliked: id dedup set hoisted out of the
  per-element filter (O(n*m) → O(n+m)).
- `distinctUntilChanged()` added to Favorites liked/disliked, Downloads, Search
  near-end snapshotFlow — stop redundant near-end re-fires.

## Thread / coroutine architecture

Verified sound. No change needed:

- DAO reads = `suspend` / `Flow`; Room auto-dispatches to its query executor.
- No blocking (non-suspend) DB call from ViewModels on main thread.
- ViewModels use `viewModelScope.launch` (Main.immediate); suspend DAO hops to
  IO internally. Only explicit `Dispatchers.IO` = DailyMix generation (correct).
- Room entities all-stable data classes → Compose strong-skipping infers stable;
  `items(key=)` diff per row. No stability config needed.

Decision: leave threading as-is (parallelizing DAO reads would serialize on
Room's single query executor — no win, added risk).

## Not changed (intentional)

- PlayerBar queue: reorderable drag semantics + bounded size → cacheWindow
  skipped (risk > reward).
- CustomDailyMixes (settings): low scroll volume → skipped.
- Album/Playlist AddFromServer + AddToPlaylist sheets (dialogs): skipped.

## Edge cases

- Long names: ellipsis (was shrink-to-fit) — same 1-line clamp, no clip.
- Radio streamUrl empty → `Text` empty line (was FittingText empty) — same.
- Overlap append: dedup set hoisted, behavior identical, covered by new test.
- Empty lists: `LazyLayoutCacheWindow` no-op on empty content.

## Verification

- `./gradlew :app:compileDebugKotlin` clean.
- `testDebugUnitTest` — Favorites, Home, Mix, Genre, Downloads, Playlist,
  Artist suites pass (incl. new `loadMoreLiked dedups overlapping page append`).
- `ktlintFormat` clean.

## Design impact

- No color/typography/size changes. Only scroll-path FittingText→fixed Text
  (matches app's own ADR-0096 rule for song/list rows). Hit areas, radius,
  semantic tokens untouched.
