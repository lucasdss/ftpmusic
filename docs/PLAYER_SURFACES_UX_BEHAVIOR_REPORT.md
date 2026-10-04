# Player Surfaces UX Behavior Report

Caveman. Scope: Now Playing · Mini · Queue sheet. Gold = Spotify / Apple Music / YT Music.
Queue Dual semantics = ADR 0039 + `PLAYBACK_QUEUE_MARKET_DRIFT_BEHAVIOR_REPORT.md` (not rewritten here).

## Ship architecture

```
Tab Scaffold.bottomBar
  → PlayerBar(expanded=false)   // mini; gated on PlaybackState.isVisible
  → NavigationBar
Tap mini → navigate("nowplaying")
  → PlayerBar(expanded=true)    // full NP
       → PlayerQueuePanel       // peek + queue_sheet (SoT queue UI)
       → PlayerLyricsOverlay
QueueScreen.kt = test harness only (not in NavHost)
```

State: `PlaybackViewModel.state` / `stateWithoutPosition` / `positionMs` → `PlayerBarState`.
Queue edits: `removeFromQueue` / `moveQueueItem` / `beginQueueReorder` / `commitQueueReorder` / `clearPriorityQueue`.

## Market matrix (UI choreography)

| Pattern | Spotify | Apple | YT Music | FTPMusic (this cycle) |
|---------|---------|-------|----------|------------------------|
| Mini | Art + title + play/next; thin progress; no seek | Same | Same | Progress-only; KDoc honest |
| Expand | Tap → full NP | Same | Same | `nowplaying` route |
| Queue | Sheet; NP pinned; drag reorder; swipe/remove | Playing Next; reorder | Up next; Automix | Sheet SoT; real reorder + remove |
| Sections | Queue / Next from | Playing Next / Autoplay | Up next / Automix | Queue · Next from · Autoplay |
| Idle mini | Hidden | Hidden | Hidden | `isVisible` gate |
| Buffering | Spinner / pause morph | Spinner | Spinner | Indeterminate on play control |

## Lexicon lock

| Surface | Copy |
|---------|------|
| NP title | Now Playing |
| Peek next | Up Next / Queue Next (priority) / End of queue |
| PRIORITY section | Queue · {n} |
| CONTEXT section | Next from · {source} · {n} (or Next from · {n}) |
| Autoplay section | Autoplay · {n} |
| Empty | Queue is empty + Play Next / Add to Queue CTA |
| Shuffle (sheet + transport) | Shuffle (= playback shuffle mode) |

## Gaps fixed this cycle

1. Prod sheet drag handle decorative → real `reorderable` + Dual commit
2. Remove hit target micro → ≥48dp + swipe dismiss
3. "Shuffle Queue" lie → Shuffle (= `onShuffleToggle`)
4. Mini always shown idle → gate `playbackState.isVisible`
5. Sleep timer callback dead → header Timer entry
6. Hardcoded EN → `strings_player.xml` + `stringResource`
7. "Continue Playing" → "Next from" (Spotify-aligned)
8. Local buffering false pause feel → `isBuffering` + CircularProgressIndicator on transport/mini
9. Raw dp / grey soup → spacing tokens + `NavUnselected` / onSurfaceVariant where contrast holds

## Gaps deferred

| Gap | Why |
|-----|-----|
| Playback error banner on NP | No error field on `PlaybackState` — no new pipeline |
| Delete QueueScreen | Backlog (market-drift P1) |
| Mini interactive seek | Anti-market |
| Full i18n locales | EN stringResource only |
| Cast flatten copy redesign | Token/string only |

## Edge Agent

| Edge | Behavior |
|------|----------|
| Empty queue | Sheet empty CTA; mini hidden if title null |
| Single track | No art peeks; End of queue peek |
| Cast + reorder | `begin`/`commit` Dual + Cast Move |
| Sheet open + skip | Sheet stays; list keys by entryId/index |
| Process death | Restore via existing persist; UI rebinds VM |

## Perf Agent

- Position tick still hoisted (`positionMs` outside `PlayerBarState`)
- Reorderable LazyColumn: only visible rows
- Buffering flag on state — no extra polling

## Coverage Agent

Gate: player UI compose suites + `PlaybackStateTest` + `PlayerSurfacesUxTest` green
(`assembleDebug` OK). Pure helper `queueRowKey` exercised by unit asserts;
`isBuffering` / `isVisible` covered in `PlaybackStateTest`. Compose/Robolectric
JaCoCo attribution for `PlayerBarKt` historically under-counts — behavioral
gate = targeted suites pass (not whole-file PlayerBar line %). Error strip
deferred (no `PlaybackState` error field).
