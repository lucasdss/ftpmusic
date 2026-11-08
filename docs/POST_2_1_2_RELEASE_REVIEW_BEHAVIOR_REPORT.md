# POST 2.1.2 → 2.1.3 release review (caveman)

**Baseline:** testing cut `2.1.2` / `a8a68b7` (versionCode 29, Room 71)  
**Delta:** `e292e9a` (pack covers + Settings Custom Daily Mixes)  
**Target:** `2.1.3` (versionCode 30)

## Cross-model

| Pass | Model | Role |
|------|-------|------|
| Discover | Cursor explore (read-only) | Diff walk a8a68b7..HEAD |
| Challenge | LiteLLM DeepSeek (`deepseek-chat`) | Attack explore severity |

## Confirmed → fixed in 2.1.3

### Home cover-edit missing `dominantGenre`

**Symptom:** GenreMixCard AUTO uses `mix.recipeGenres`; Home long-press edit sheet did not pass `dominantGenre` → Style packs strip could show `mixes/default.webp` while card showed genre asset.  
**DeepSeek:** reclassified from “Low preview” to **latent correctness**; not fatal but must-fix plumbing.  
**Fix:** `HomeCoverEditTarget.Mix.dominantGenre` + saver; pass into `rememberCollectionCoverEditController`. Custom editor uses `firstOrNull { isNotBlank() }`.

## Non-bugs / defer

| Claim | Verdict |
|-------|---------|
| PLAYLIST pack > serverCoverArtId | Intentional product |
| DominantGenrePicker unused in prod | Leave staged helper |
| Pack browser remember vs sheet saveable | Defer UX consistency |
| Manage only when mixCards non-empty | Intentional |
| Network Custom Mixes removed | Correct ADR 0149 amendment |

## Fatal

None.
