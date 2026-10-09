# Network Sync Policy Behavior Report

Caveman terse. Code truth after ADR-0105.

## Controls

| Control | Persist | Default |
|---------|---------|---------|
| Library sync on Wi‑Fi only | `KEY_LIBRARY_SYNC_WIFI_ONLY` | false (any network) |
| Cellular media | `KEY_CELLULAR_MEDIA_POLICY` | migrate from `download_mobile_data` |

Migration: `download_mobile_data=true` → `auto_cache`; `false` → `minimal`.

## Cellular media matrix

| Mode | Stream NP | Queue pri0 | Download pri1 | Album/mix pri2 | Metadata sync |
|------|-----------|------------|---------------|----------------|---------------|
| auto_cache | yes | yes | yes | yes | follows sync Wi‑Fi-only |
| minimal | yes | yes | no | no | follows sync Wi‑Fi-only |
| local_only | **no** | no | no | no | **blocked** |

Wi‑Fi / Ethernet: full sync + cache (still subject to Simulate Offline / reachability).

## Sync gates

Automatic FULL/DELTA (`SyncScheduleWorker` / `syncNowAsync` without override):

- Offline → skip
- Unreachable → skip / WM retry
- Cellular + `local_only` → skip
- Cellular + sync Wi‑Fi-only → skip
- Else run

WorkManager: `UNMETERED` when sync Wi‑Fi-only else `CONNECTED`.
In-worker `NetworkTransportPolicy` is truth (metered Wi‑Fi still Wi‑Fi).

Manual Resync: warn if sync Wi‑Fi-only **and** cellular → proceed with
`allowCellularOverride=true` or cancel.

First-login FULL: warn whenever cellular → proceed with override or cancel.

Mid-sync Wi‑Fi→cell + policy forbids cellular: abort at phase boundary; no watermark.

## Local-only formula

`offline || !hasOsNetwork || (cellular && local_only)`

Consumers: browse/search playable filters, OfflineAwareHttpDataSource, DownloadManager,
MetadataSyncWorker, MetadataEnrichWorker.

## Not persisted

One-shot cellular override after warn dialog.
