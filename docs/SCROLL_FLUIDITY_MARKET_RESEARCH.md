# Scroll Fluidity Market Research — Compose Music Lists

Date: 2026-10-10  
Status: Research (no app code changes)  
Related: ADR-0096, ADR-0097, ADR-0102, ADR-0037; `docs/SCROLL_FPS_BEHAVIOR_REPORT.md`, `docs/HOME_SCROLL_PERF_BEHAVIOR_REPORT.md`, `docs/LIBRARY_SCROLL_PERF_BEHAVIOR_REPORT.md`

## Goal

Explain how market music apps feel fluid at 60/120 Hz, what official Compose / AndroidX guidance actually says, what open-source players do, and which **high-ROI next fixes** fit FTPMusic after Passes 1–3 (Coil crossfade off, sized covers, `contentType`/keys, EQ pause on fling, enterAlways header).

## Honesty boundary (black box vs public)

| Claim class | Status |
|-------------|--------|
| Compose Lazy / nested scroll / phases / prefetch APIs | **Primary** — Android Developers docs, AndroidX API refs, Android Developers Blog, Google I/O talk transcripts |
| Coil / bitmap config / pause-on-scroll | **Primary** — Coil docs + maintainer GitHub; Android bitmap optimization guide |
| Spotify Liked Songs architecture (windowed metadata) | **First-party** Spotify Engineering — **not** scroll-toolkit disclosure |
| YouTube Music / Spotify Android list toolkit (RV vs Compose vs hybrid) | **Black box** — no public first-party “we use LazyColumn for Home” post found; infer only from Google interop docs + industry migration patterns |
| Symfonium (often misspelled “Synfonium”) list virtualization | **Black box** — closed source; public site/Play listing confirm **Android-only** player, not UWP/Windows-native |
| Auxio / Vinyl Music Player | **Open source** — View/`RecyclerView` stacks (not Compose Lazy) |
| Strawberry | **Not Android** — Qt desktop (Linux/macOS/Windows); irrelevant to Compose scroll |

---

## 0. This app’s starting point (already shipped)

| Pass | What landed | Doc |
|------|-------------|-----|
| 1 | Coil `crossfade(false)`; sized `CoverArtImage`; `SongListRow`; scoped cover invalidation | ADR-0096 |
| 2 | enterAlways `AppHeader` via `NestedScrollConnection` + isolated `mutableFloatStateOf` | ADR-0097 |
| 3 | Home/Library `contentType`; decode≈display; fixed Text on scroll paths; `ScrollAwareEqBars` pauses while `isScrollInProgress` | ADR-0102 |
| Deferred | Library full-catalog dump / Paging 3 | ADR-0037 |

**Current Compose BOM:** `androidx.compose:compose-bom:2024.12.01` (`compose/app/build.gradle.kts`).  
Google’s “Compose matches Views scroll jank” claim and the stable `LazyLayoutCacheWindow` prefetch story land with **Compose 1.9 / BOM ~2025.08.00** — see §1 and §11.

**No Macrobenchmark / Baseline Profile modules found** in-repo at research time.

---

## 1. LazyColumn / LazyRow — 60/120 Hz best practices

### Claims → sources

| Claim | Source |
|-------|--------|
| Lazy layouts only compose/layout visible items; same principles as `RecyclerView` | [Lazy lists and lazy grids](https://developer.android.com/develop/ui/compose/lists) |
| Provide stable unique `key` so identity survives reorder/insert (avoids full-list recomposition) | [Follow best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices); lists doc |
| Provide `contentType` so composition reuse only happens across same-shaped items (Compose 1.2+) | Lists doc; `LazyListScope` API |
| Measure Lazy scroll only in **release + R8**; debug looks worse | Lists doc; [Compose performance](https://developer.android.com/develop/ui/compose/performance) |
| Prefetch exists; historically ~1 item ahead; **`LazyLayoutCacheWindow`** tunes ahead/behind (Compose 1.9 / Aug ’25 release) | [Android Developers Blog — Aug ’25](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html); [`LazyLayoutCacheWindow`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutCacheWindow); [`LazyListDefaults.cacheWindow`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListDefaults) |
| Default cache window: prefetch ahead ~10–50% of viewport from avg item size; **no behind retain**; **no cache while idle** | `LazyListDefaults` API (updated 2026-10-07) |
| Public `LazyColumn` parameter is `cacheWindow`; older `rememberLazyListState(cacheWindow=…)` / `LazyListPrefetchStrategy` marked deprecated in favor of composable arg | [`rememberLazyListState`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/rememberLazyListState.composable); [`LazyColumn`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyColumn.composable) |
| `beyondBoundsItemCount` | **Internal** LazyList measure path in AndroidX source (`LazyList.kt` beyond-bounds modifier). **Not** a documented public `LazyColumn(...)` knob in current API pages — use `cacheWindow` for product tuning |
| Do not use composition side-effects (`LaunchedEffect`) as “visible” / impression signals when prefetch expands | Aug ’25 blog note; prefer `onFirstVisible` / `onVisibilityChanged` (Compose 1.9) |
| Compose **1.9+** hero benchmarks: scroll jank rate matches Views (Pokedex) | [Compose performance](https://developer.android.com/develop/ui/compose/performance) |
| I/O ’24: customizable lazy prefetch APIs; indication rewrite for scroll | [What’s new at I/O ’24](https://android-developers.googleblog.com/2024/05/whats-new-in-jetpack-compose-at-io-24.html) |
| Google I/O talk: unique keys; avoid same-direction nesting; avoid 0-size items; release-mode measurement | [Lazy layouts in Compose](https://www.youtube.com/watch?v=1ANt65eoNhQ) |

### Action for FTPMusic

- Keys + `contentType` on Home / Favorites / Queue are already aligned with docs (Pass 3).
- **Upgrade Compose BOM** toward 1.9+ to pick up pausable composition + smarter prefetch defaults, then optionally widen Home outer `LazyColumn` / Library grid `cacheWindow` if Macrobenchmark still shows compose-on-fling spikes.
- Keep measuring fling in **release** with R8; do not trust debug FPS.

---

## 2. Nested scroll — vertical LazyColumn + horizontal LazyRow

### Claims → sources

| Claim | Source |
|-------|--------|
| Nesting **different** scroll directions is allowed (e.g. vertical parent + horizontal child) | Lists doc — “Avoid nesting components scrollable in the same direction” |
| Same-direction nested scrollables without fixed size are unsupported / problematic | Lists doc |
| Each nested Lazy uses `SubcomposeLayout`; visible as multiple compose chunks + `compose:lazylist:prefetch` in traces | [Practical performance problem solving codelab](https://developer.android.com/codelabs/jetpack-compose-performance) |
| Codelab fix for **small** per-row tag lists: replace inner `LazyRow` with plain `Row` when item count is tiny (Lazy overhead dominates) | Same codelab |
| Nested LazyRows for Home carousels are a valid product pattern; cost is composing several horizontal lists as vertical sections enter the viewport | Inference from Home structure + lists/nested-scroll docs (not a ban) |

### Known jank causes (official + app-specific)

1. **Subcompose tax** — each Home section `item { LazyRow { … } }` is a nested lazy layout; vertical fling that reveals several carousels composes many covers at once (codelab tracing model).
2. **Same-direction nesting** — avoid vertical-in-vertical without fixed height (lists doc). FTPMusic Home pattern is orthogonal → OK.
3. **Header nested scroll** competing with list — see §7.
4. **Reading scroll state in composition** every frame — see §4.

### Mitigations (official)

- Keep carousels as LazyRow when cards are many / unknown length; use `Row` only for tiny fixed chip strips (codelab).
- Stable `key` + `contentType` on **both** outer items and inner items (lists / migrate-RV tip equates `contentType` to RV viewType).
- Tune **outer** list prefetch/`cacheWindow` so the next carousel’s first cards are ready before they enter view (Aug ’25 blog).
- Size covers to card width (already ADR-0102) so nested prefetch doesn’t decode 300.dp bitmaps.

### Action for FTPMusic Home

- Structure already matches the allowed pattern (`docs/HOME_SCROLL_PERF_BEHAVIOR_REPORT.md`).
- Next levers: BOM upgrade + outer `cacheWindow`; ensure section headers are cheap `item`s; avoid putting `BoxWithConstraints` / heavy measure inside carousel cards (codelab warns SubcomposeLayout in every item).

---

## 3. Image loading during fling (Coil)

### Claims → sources

| Claim | Source |
|-------|--------|
| Prefer Coil/Glide; they cache, downsample, recycle | [Optimizing bitmap images](https://developer.android.com/develop/ui/compose/graphics/images/optimization) |
| Downsample to target size; avoid unconstrained / wrapContent image hosts that force full-res decode | Same |
| Prefer **server-side** size when API allows | Same |
| `RGB_565` ≈ half memory of `ARGB_8888` when alpha unused; Coil via `bitmapConfig` | Same; [Coil `bitmapConfig`](https://coil-kt.github.io/coil/api/coil-core/coil3.request/bitmap-config.html) |
| Single shared `ImageLoader`; configure memory + disk cache | [Coil Image Loaders](https://coil-kt.github.io/coil/image_loaders/) |
| Coil has **no** built-in Glide-style `pauseAllRequests()`; maintainer suggests custom `Interceptor` + `StateFlow` (blocks **new** requests; does not pause in-flight) | [coil-kt/coil#580](https://github.com/coil-kt/coil/issues/580) |
| Pass URL / res id into composables, not unstable `Painter` parameters | Bitmap optimization guide |
| Crossfade during fling adds animation work — FTPMusic already disables app-wide | ADR-0096 (local decision; Coil default often enables crossfade) |

### Action for FTPMusic

Already done: crossfade off, sized requests, composition path avoids heavy disk validation (ADR-0096).

**Still high-ROI candidates:**

1. List-row `bitmapConfig(RGB_565)` for opaque album art (covers rarely need alpha) — official memory guidance.
2. Optional Coil `PauseInterceptor` gated on `LazyListState.isScrollInProgress` / fling velocity — **prototype carefully**; can increase blank cells after fling; not a Coil first-class API.
3. Prefer Subsonic/Navidrome cover URLs with size params when available (server-side resize).
4. Keep disk cache warm; avoid thrashing memory cache with oversized decodes (Home Pass 3 already shrunk decode).

---

## 4. Recomposition isolation

### Claims → sources

| Claim | Source |
|-------|--------|
| `remember` expensive list transforms outside `items { }` | Best practices |
| `derivedStateOf` for thresholds derived from scroll (not every pixel) | Best practices |
| Defer scroll reads: pass `() -> Int` / use `Modifier.offset { }` / `graphicsLayer { }` so composition skips | Best practices; [Compose phases](https://developer.android.com/develop/ui/compose/phases); [Modifier phases](https://developer.android.com/develop/ui/compose/performance/modifier-phases) |
| Reading `firstVisibleItemScrollOffset` in composition recomposes parents every frame | Phases / best practices (Jetsnack collapse example) |
| Avoid backwards writes (state write after read in composition) | Best practices |
| Prefer stable / immutable item models so skips work | [Compose performance — Stability](https://developer.android.com/develop/ui/compose/performance) |
| Unstable lambdas / unstable params force child recomposition | Stability docs (general); pass stable callbacks via remember where needed |

### Action for FTPMusic

- Home already hoists `take(10)` / playlist filters with `remember` (ADR-0102 / HomeScreen).
- `AppHeaderScrollState.offsetPx` is isolated `mutableFloatStateOf` — good; ensure shell layout applies offset via **lambda** `graphicsLayer` / height read that doesn’t recompose the whole `NavHost` every pixel (phases guidance). ADR-0097 claims sync offset without per-frame coroutine — keep that contract.
- For “scroll to top” / chrome reactions: use `derivedStateOf { firstVisibleItemIndex > N }` or ADR-0104 list-chrome patterns — never raw offset in composition.
- `SongListRow`: keep params stable (ids, strings, fixed callbacks); avoid allocating new lambda/list per item in `items {}` without `remember`.

---

## 5. GraphicsLayer / overscroll / stretch cost

### Claims → sources

| Claim | Source |
|-------|--------|
| `Modifier.graphicsLayer { }` reads state in **draw**; skips composition + layout when only layer props change | Modifier phases; [`graphicsLayer`](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/graphicsLayer.modifier) |
| Minimize work inside `graphicsLayer` block (may run often / before effects) | `graphicsLayer` API note |
| `CompositingStrategy.Offscreen` / heavy `RenderEffect` adds offscreen buffers — use only when needed | `graphicsLayer` API |
| Lazy lists attach default `OverscrollEffect` via `rememberOverscrollEffect()` | [`LazyColumn` API](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyColumn.composable); [`OverscrollEffect`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/OverscrollEffect) |
| Scroll pipeline: overscroll participates pre- and post-scroll (stretch/glow) | [Understand scroll phases](https://developer.android.com/develop/ui/compose/touch-input/scroll/scroll-phases) |

### Action for FTPMusic

- Prefer `graphicsLayer { translationY = -offset }` (or equivalent) for header motion over recomposing chrome.
- Treat stretch overscroll as **usually fine**; only A/B-disable (`overscrollEffect = null` where API allows) if Perfetto shows overscroll/animation dominating jank — not a first fix.
- Avoid per-row shadows / blur / `RenderEffect` on `SongListRow` and Home cards during fling.

---

## 6. enterAlways / collapsing headers / nestedScroll hitch patterns

### Claims → sources

| Claim | Source |
|-------|--------|
| Material3 `enterAlwaysScrollBehavior` + `Modifier.nestedScroll(connection)` on Scaffold is the official pattern | [Display an app bar](https://developer.android.com/develop/ui/compose/quick-guides/content/display-app-bar); [Migrate CoordinatorLayout](https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/coordinator-layout); [`TopAppBarScrollBehavior`](https://developer.android.com/reference/kotlin/androidx/compose/material3/TopAppBarScrollBehavior) |
| Custom collapse: own `NestedScrollConnection` + manual offset (parallax samples) | CoordinatorLayout migration doc |
| Defer scroll offset reads to layout/draw or parent scopes explode | Best practices / phases (Jetsnack) |
| FTPMusic: custom enterAlways in shell; finger-follow `onPreScroll`; ignore horizontal; snap on `onPostFling` | ADR-0097; `AppHeaderScrollState.kt` |

### Hitch patterns to watch

1. **Layout height change every frame** (`height = header - offset`) forces relayout of content below — intentional for “no gap” YT-like reclaim, but costlier than translation-only overlay. If residual hitch correlates with header motion, compare translation-only vs height-reclaim in release traces.
2. **Writing header state that invalidates LazyColumn items** — header offset must not sit in the same state object as list data.
3. **Settle spring fighting fling** — ADR-0097 snaps only on post-fling; mid-drag must not animate against finger (already decided).
4. **Double nestedScroll** — Material scrollBehavior + custom connection both consuming can cause rubber-banding; FTPMusic uses custom only — keep single owner.

### Concrete residual in this shell (`NavHost.kt`)

After Pass 3, the strongest structural hitch candidate is **already wired**:

- `graphicsLayer { translationY = -headerScroll.offsetPx }` — draw-phase friendly.
- Sibling `Box(Modifier.height(visibleDp))` where `visibleDp` is derived from
  `headerScroll.visibleHeightPx` / `offsetPx` — **composition + layout every nested-scroll
  delta**, which remeasures the weighted `NavHost` / LazyColumn viewport continuously.

That matches hitch pattern (1). Treat as Pass 4 A/B target before more row micro-opts.

### Action for FTPMusic

- Keep offset isolated (already).
- Prefer overlay/inset (stable list viewport) or snapped collapsed/expanded reclaim over
  continuous `.height(visibleDp)` during drag.
- If Pass 3 residual is “header + list”, Macrobenchmark Home fling with header locked expanded vs enterAlways to isolate.

---

## 7. RecyclerView vs Compose — market apps & lessons

### What is public

| Claim | Source |
|-------|--------|
| Lazy lists are the Compose equivalent of RV recycling | Lists doc; [Migrate RecyclerView to Lazy list](https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/recycler-view) |
| `contentType` ≈ RV view types | Migrate RV doc tip |
| Hybrid: Compose items inside RV need pooling-aware disposal; Compose UI 1.2 + RV 1.3+ keep compositions in pool during fling | [Android Developers — Compose in RecyclerView](https://medium.com/androiddevelopers/jetpack-compose-interop-using-compose-in-a-recyclerview-569c7ec7a583); [Compose in Views](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views) |
| RV prefetch moves bind work to previous frame gaps | [Google Developers — RecyclerView prefetch](https://medium.com/google-developers/recyclerview-prefetch-c2f269075710) (Chris Craik / Android UI Toolkit) |
| Compose 1.9+ scroll jank rate matches Views in Google hero benchmarks | Compose performance page |
| Spotify Engineering (2020): Liked Songs moved to **batched metadata + on-disk pre-sorted tables** — about catalog scale / startup, **not** RV vs Compose | [Spotify Engineering](https://engineering.atspotify.com/2020/5/spotify-modernizes-client-side-architecture-to-accelerate-service-on-all-devices) |

### Black box (do not fake certainty)

- **YouTube Music** and **Spotify** Android production list stacks (pure RV, pure Compose, or hybrid) are **not** documented in first-party scroll posts found for this research.
- LinkedIn / secondary claims about Compose Navigation at Spotify are **not** list-virtualization proof — treat as weak.
- Historical industry default for large media lists was **RecyclerView** with viewTypes, prefetch, and Glide pause — lessons still transfer: viewType→`contentType`, prefetch→`cacheWindow`, bind cost→item composition cost, image pause→Coil interceptor experiment.

### Open-source music apps (inferable)

| App | Platform / UI | Scroll relevance |
|-----|---------------|------------------|
| **Auxio** | Android; changelog “new RecyclerView framework” | Fluid feel from mature RV + Media3; not a Compose Lazy reference | [OxygenCobalt/Auxio](https://github.com/OxygenCobalt/Auxio) |
| **Vinyl Music Player** | Android Java; `RecyclerView` adapters (`SongAdapter`) | Classic RV recycling + cover bind on `onBindViewHolder` | [VinylMusicPlayer](https://github.com/VinylMusicPlayer/VinylMusicPlayer) |
| **Strawberry** | Desktop Qt — **not Android** | Out of scope | [strawberrymusicplayer.org](https://www.strawberrymusicplayer.org/) |

**Recommendation for FTPMusic:** stay Compose-first; do **not** hybridize Home to RV unless Macrobenchmark proves Lazy nested carousels unfixable after BOM 1.9 + paging. Hybrid is an escape hatch Google supports, not a market requirement you can cite.

---

## 8. Variable refresh rate / vsync / Choreographer / FrameMetrics

### Claims → sources

| Claim | Source |
|-------|--------|
| Frame budget is not fixed 16.6 ms — 90/120 Hz and **VRR** change expected duration per frame | [Compose performance codelab](https://developer.android.com/codelabs/jetpack-compose-performance) |
| Work appears under `Choreographer#doFrame`; use Perfetto FrameTimeline Expected vs Actual | Codelab; [Slow rendering](https://developer.android.com/topic/performance/issues/render) |
| `FrameMetricsAggregator` records per-frame duration histograms (API 24+) | [`FrameMetricsAggregator`](https://developer.android.com/reference/androidx/core/app/FrameMetricsAggregator) |
| `Choreographer.FrameTimeline` exposes deadline / expected presentation / vsync id (API 33+) | [`FrameTimeline`](https://developer.android.com/reference/kotlin/android/view/Choreographer.FrameTimeline) |
| Avoid frequent `getRefreshRate()` — can trigger binder transactions | Slow rendering guide |
| Macrobenchmark `FrameTimingMetric` + UI Automator fling is the productized measurement path | [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview); [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) |

### Action for FTPMusic

- Add Macrobenchmark scroll journeys: Home vertical fling, Library albums fling, song list fling — `FrameTimingMetric`, warm start, release.
- Generate **app Baseline Profile** including those journeys (Compose library profile alone is insufficient per Google).
- Profile on a **120 Hz** device; interpret jank relative to that timeline, not “must beat 16 ms.”

---

## 9. Paging3 / windowed catalogs (10k+ tracks)

### Claims → sources

| Claim | Source |
|-------|--------|
| Paging 3 + `paging-compose` + `collectAsLazyPagingItems` for large/unknown lists | [Paging overview](https://developer.android.com/topic/libraries/architecture/paging/v3-overview); lists doc “Large data-sets”; [Lazily load with Paging](https://developer.android.com/develop/ui/compose/quick-guides/content/lazily-load-list) |
| Use `itemKey { }` / avoid calling `items[index]` inside custom keys (triggers loads for all indices) | Paging Compose API semantics; community + docs warn on `get` notifying Paging |
| Spotify: don’t hold entire large libraries sorted in RAM; batch to disk; stream windows into UI | Spotify Engineering Liked Songs post |
| FTPMusic: Room full dump deferred; suggested `LIMIT/OFFSET` DAO | ADR-0037 |

### Action for FTPMusic

- **Highest structural ROI for Library / all-songs:** windowed Room (Paging3 or manual LIMIT/OFFSET) so Lazy never holds 10k row models + cover requests in one snapshot.
- Home carousels stay small (`take(10)` etc.) — paging less critical there than Library.
- Pair paging with placeholders (`null` item → shimmer) per lists doc.

---

## 10. Symfonium (not “Synfonium”) — what is publicly known

| Fact | Source |
|------|--------|
| Product name **Symfonium**; premium Android music / cast client | [symfonium.app](https://symfonium.app/); [Play Store listing](https://play.google.com/store/apps/details?id=app.symfonik.music.player) |
| Aggregates local, cloud, Plex/Emby/Jellyfin/Subsonic/OpenSubsonic/Navidrome, etc.; Hi-Res / DSP focus | symfonium.app |
| **No native Windows/UWP app** — desktop use is Android-on-Windows (WSA / Waydroid) per community + support threads | [support.symfonium.app desktop thread](https://support.symfonium.app/t/desktop-app-of-symfonium/4190) |
| UI toolkit (Compose vs Views/RV), list virtualization, image pipeline | **Not publicly documented** — closed source; treat scroll quality as **black box** |

Do not reverse-engineer the APK for this research. Infer only: Symfonium’s reputation for snappy browsing is consistent with mature Android list virtualization + aggressive caching, but **no citable architecture**.

---

## 11. Prioritized checklist for FTPMusic (high → lower ROI)

Context surfaces: **Home** nested carousels, **Library** full-catalog grids/lists, **`SongListRow`**, **`AppHeader` enterAlways**.

### P0 — measure & platform (unblocks honest comparison)

1. **Release + R8 Macrobenchmarks** with `FrameTimingMetric` on Home / Library / song list flings.  
   Source: Macrobenchmark + Compose performance docs.  
2. **App-specific Baseline Profile** including those flings.  
   Source: Baseline Profiles overview / create guide.  
   *Gap today: none in repo.*

### P1 — structural (market parity with Spotify-scale libraries)

3. **Window Library (and all-songs) catalogs** — Paging3 or Room `LIMIT/OFFSET` per ADR-0037; stop full alpha dumps into one Compose state list.  
   Sources: Paging Compose docs; Spotify Liked Songs architecture; ADR-0037.  
4. **Upgrade Compose BOM** from `2024.12.01` → **1.9+ / BOM 2025.08+** for scroll-jank parity claims, pausable composition, `LazyLayoutCacheWindow` defaults.  
   Sources: Compose performance page; Aug ’25 blog.

### P2 — nested Home + images (residual fling glitch)

5. After BOM upgrade, **tune outer Home `LazyColumn` `cacheWindow`** (ahead fraction or ~150.dp) so next carousel’s covers prefetch; optionally retain small behind window for scroll-back.  
   Source: `LazyLayoutCacheWindow` / Aug ’25 blog.  
6. **List-art `RGB_565`** + keep decode size == display; server-sized cover URLs when API allows.  
   Source: bitmap optimization guide; Coil `bitmapConfig`.  
7. Experiment **Coil pause-new-requests while `isScrollInProgress`** (custom interceptor) — measure blanking vs jank.  
   Source: coil#580 (not first-class).

### P3 — header / recomposition hygiene

8. Ensure **enterAlways offset** only invalidates header chrome via `graphicsLayer` / deferred layout — never list item composition.  
   Sources: phases; modifier phases; ADR-0097; see `NavHost.kt` height-reclaim note in §6.  
9. A/B **height-reclaim vs translation-only** (or snapped reclaim) if traces show layout thrash during collapse — **first code suspect for residual glitch after Pass 3**.  
10. Keep using **`derivedStateOf` / `isScrollInProgress`** for EQ and list chrome (already Pass 3) — extend to any remaining scroll-derived UI.

### P4 — only if still behind after P0–P2

11. Replace tiny nested LazyRows with `Row` where card counts are tiny (codelab).  
12. Hybrid RV for a single pathological screen — Google-supported escape hatch; **not** justified by public Spotify/YT docs.

### Already done (do not redo)

- Coil crossfade off; sized covers; `SongListRow` fixed text; Home/Library `contentType`/keys; EQ pause on scroll; enterAlways header.

---

## 12. Suggested verification plan

| Step | Method |
|------|--------|
| Establish baseline | Macrobenchmark FrameTiming on device @ 90/120 Hz, release |
| Trace residual | Perfetto: `Choreographer#doFrame`, `compose:lazylist:prefetch`, Coil decode, header nested scroll |
| Prove paging win | Library 10k albums: memory + frame metrics before/after windowing |
| Prove BOM win | Same benchmarks on 2024.12 vs 2025.08 BOM |
| Header isolation | Fling with header forced expanded vs enterAlways |

---

## 13. Source index (primary / first-party)

- https://developer.android.com/develop/ui/compose/lists  
- https://developer.android.com/develop/ui/compose/performance  
- https://developer.android.com/develop/ui/compose/performance/bestpractices  
- https://developer.android.com/develop/ui/compose/phases  
- https://developer.android.com/develop/ui/compose/performance/modifier-phases  
- https://developer.android.com/develop/ui/compose/graphics/images/optimization  
- https://developer.android.com/codelabs/jetpack-compose-performance  
- https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/recycler-view  
- https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/coordinator-layout  
- https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views  
- https://developer.android.com/develop/ui/compose/quick-guides/content/display-app-bar  
- https://developer.android.com/develop/ui/compose/touch-input/scroll/scroll-phases  
- https://developer.android.com/topic/libraries/architecture/paging/v3-overview  
- https://developer.android.com/topic/performance/baselineprofiles/overview  
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview  
- https://developer.android.com/topic/performance/issues/render  
- https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutCacheWindow  
- https://android-developers.googleblog.com/2024/05/whats-new-in-jetpack-compose-at-io-24.html  
- https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html  
- https://android-developers.googleblog.com/2025/05/whats-new-in-jetpack-compose.html  
- https://www.youtube.com/watch?v=1ANt65eoNhQ (Lazy layouts in Compose)  
- https://medium.com/androiddevelopers/jetpack-compose-interop-using-compose-in-a-recyclerview-569c7ec7a583  
- https://medium.com/google-developers/recyclerview-prefetch-c2f269075710  
- https://engineering.atspotify.com/2020/5/spotify-modernizes-client-side-architecture-to-accelerate-service-on-all-devices  
- https://coil-kt.github.io/coil/image_loaders/  
- https://github.com/coil-kt/coil/issues/580  
- https://symfonium.app/  
- https://github.com/OxygenCobalt/Auxio  
- https://github.com/VinylMusicPlayer/VinylMusicPlayer  
- Local: ADR-0096, ADR-0097, ADR-0102, ADR-0037; SCROLL/HOME/LIBRARY scroll behavior reports  

---

## 14. One-line verdict

Market fluidity is mostly **virtualized lists + cheap bind/decode + windowed catalogs + measured release builds**; Compose 1.9 claims parity with Views on scroll jank, but FTPMusic is still on BOM 2024.12 without Baseline Profiles or Library paging — those gaps explain residual lag better than missing “secret” Symfonium/YT scroll tricks.
