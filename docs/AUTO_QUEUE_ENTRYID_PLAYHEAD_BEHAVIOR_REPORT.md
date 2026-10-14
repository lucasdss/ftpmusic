# Auto Queue / entryId / playhead fixes (caveman)

**Source:** post-1.13 playback review  
**Ship with:** 1.13.1 (versionCode 21)

## Confirmed bugs → fixed

### A. Auto Queue stale after skip (High)

`primeQueueSnapshot` early-returned on same Dual revision → playhead advance
kept old Up Next rows.  
**Fix:** refresh when `currentIndex` changes (revision may stay).  
**Test:** `primeQueueSnapshot refreshes when playhead advances same revision`

### B. Batch `ensureEntryId` duplicate ids (High)

`peekNextEntryId()` applied to every item in `appendToContext` /
`addAllToQueue` / adopt → one shared entry id; Dual `stamp` kept duplicates.  
**Fix:** `allocateNextEntryId()` advances per stamp.  
**Tests:** `allocateNextEntryId advances uniquely`,
`appendToContext batch assigns unique entry ids`

### C. `syncDualQueueToPlayer` mediaId playhead (Med)

Duplicate mediaId (Play Next / Add of current) mapped to first row.  
**Fix:** prefer entryId like `currentCanonicalIndex`.  
**Test:** `syncDualQueueToPlayer prefers entryId when mediaIds duplicate`

## Residual (not this patch)

- Cast JumpTo throw UX / Cast unit coverage (intentional Media3 bypass)
- Cross-band drag LATE rebuild doc mismatch
