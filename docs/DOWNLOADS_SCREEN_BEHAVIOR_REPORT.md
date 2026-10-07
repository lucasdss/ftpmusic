# Downloads Screen — Behavior Report

Caveman. ADR-0092. Phase B.

## Ship

| Action | Behavior |
|--------|----------|
| Open | Settings → Manage downloads |
| List | Paged explicit downloads |
| Play | Full list as CONTEXT, jump index |
| Remove | Unpin + clear Room download flags |
| Clear all | Existing `clearDownloads` |
| Stale heal | Path cleared; warning chip |
| loadMore | Mutex + AtomicBoolean (no duplicate pages) |

## Edge

Empty list copy. Mid-scroll load more. Heal keeps is_downloaded until remove.
Healed (null path) not offline-CP playable (ADR-0095).
