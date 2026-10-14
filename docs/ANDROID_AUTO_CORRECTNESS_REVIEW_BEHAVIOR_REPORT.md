# Android Auto Correctness Review — Behavior Report

Caveman. Baseline `v1.13.0`. Scope 1 = correctness + gap map (no market features).
ADR-0136.

## Confirmed bugs → fixed

| Bug | Severity | Fix |
|-----|----------|-----|
| Error/offline helpers only match `radio:` | P0 | `isRadioMediaId` |
| Playlist/mix page offset on raw entries | P1 | Page playable ids |
| LocalOnly still hits `search3` | P1 | Gate network search |
| Resumption forces `type=music` + migrate on radio | P1 | Preserve radio URI/type |

## Peer pack (already shipped)

9-node Room-first tree, in-Auto search, voice expand + single-track fallback,
Dual adopt, radio reactions gated, local-only radio bookmarks.

## Market gap appendix

| Gap | Severity | Notes |
|-----|----------|-------|
| Auto Play Next / Add custom actions | Shipped | ADR-0137 |
| Dual-band queue labels on HU | Shipped | ADR-0138 browse Queue node |
| Voice ≠ browse search convergence | P1 product | ADR-0131 lock |
| Discovery / Home feed nodes | P2 | Out |
| AAOS / Car App Library / Wear | P2 | Out |
| Real HU hardware QA | Process | Tester checklist |

## Tester checklist (HU)

- [ ] Play Live Radio from Auto → stream error does not auto-skip to next
- [ ] Playlist with missing track ids: page 0 then page 1 no duplicates
- [ ] Simulate Offline → Auto search shows local only (no network)
- [ ] Voice play-from-search under LocalOnly → local hit or empty (no search3)
- [ ] Start Auto radio → force-stop app → resume keeps radio stream

## Coverage

`PlaybackErrorRecoveryTest` (auto_radio), `AutoBrowseCatalogTest` (paging),
`MediaSessionCallbackTest` (LocalOnly search), `PlaybackResumptionMapperTest`
(radio round-trip).
