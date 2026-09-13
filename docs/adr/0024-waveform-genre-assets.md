# ADR 0024: Genre-Matched Waveform Assets

- Status: Accepted
- Date: 2026-08-25
- Related: `docs/WAVEFORM_TIMETRACK_BEHAVIOR_REPORT.md`

## Context
The Now Playing scrubber previously showed synthetic sine "bars" seeded by track
ID (`WaveformRepository.generateBars`). User request: source the waveform from
real per-genre waveform files (`waveform/{genre}/*.dat`, ≤3 examples/genre),
picking a random example when the track genre matches, the closest genre
otherwise, and a random genre/file as last resort. Bars must reflect real audio
dynamics.

## Decision
1. **Build-time preprocessing, compact committed assets.** A Gradle task
   `generateWaveformAssets` decodes the raw `.dat` files (20-byte header +
   u16 RMS magnitudes) into 400-bucket JSON assets under
   `app/src/main/assets/waveform/{genre}/{name}.json` (~2.9 KB/track, ~500 KB
   total). Assets are committed → CI/clean checkouts need no raw files; task
   re-runs on `preBuild` when raw files change. Rationale: bundling raw `.dat`
   would add ~15 MB to the APK; runtime decoding of raw files buys nothing the
   compact form loses.
2. **400 buckets stored; screen-adaptive rendering.** Assets carry 400
   buckets/track (~0.45 s resolution for a 3-min track, 4× the original 100).
   The scrubber measures its usable width and decimates to
   `floor(width / bar-step)` bars at render time (`WaveformDecimator`, same
   0.65·mean + 0.35·max bucket blend as the generator). Phones render ~84 bars
   (fill width, no clipping), tablets up to ~200, ≥1400dp caps at 400. Never
   upsamples — a coarse cache on a wide screen renders as-is instead of
   fabricating detail. Original decision #2 (100 bars fixed) superseded.
3. **Per-file percentile normalization** (95th percentile scale, clamp [0,1]):
   a lone spike cannot flatten the waveform; loudness comparable across files;
   real silence reads quiet.
4. **Genre selection in the app** (`WaveformGenreMatcher`): exact (normalized)
   → curated alias map → fuzzy token-overlap (≥0.5, alphabetical tie-break) →
   random folder/file. Pure object, fully unit-tested.
5. **Room cache retained** (`track_waveforms`): first generation per track
   picks a random example; result cached → stable waveform per track, existing
   `CacheService` cleanup unchanged. Parser accepts any count in
   `MIN_BARS=20..MAX_BARS=1000`, so old 100-value rows stay valid (no
   regeneration churn on update).
6. **Synthetic fallback retained** (`generateBars`, 100 bars): any asset
   failure degrades to the legacy deterministic bars — the UI never renders an
   empty bar.

## Consequences
- APK grows ~500 KB instead of ~15 MB.
- New DI wiring: `WaveformAssetSource` (interface) ←
  `AndroidWaveformAssetSource` (@Inject, AssetManager) via
  `WaveformModule` @Binds; `WaveformAssetLoader` (@Singleton).
- `WaveformRepository` gains `TrackDao` (genre lookup) + `WaveformAssetLoader`;
  public `getOrGenerate(trackId)` signature unchanged → no UI/NavHost changes.
- New pure `WaveformDecimator` (fully unit-tested); scrubber layout wrapped in
  `BoxWithConstraints` for width measurement.
- Raw `waveform/` dir gitignored; regeneration requires re-running the build
  task after `.dat` updates.
- Trade-off: closest-genre fuzzy match is heuristic (token overlap); ties are
  arbitrary-but-deterministic (alphabetical).
- Trade-off: decimation uses mean+max per bucket (keeps transients, loses
  sub-bucket detail by design); SoundCloud-style gapless envelope rendering is
  a possible later renderer swap on the same 400-bucket data.
