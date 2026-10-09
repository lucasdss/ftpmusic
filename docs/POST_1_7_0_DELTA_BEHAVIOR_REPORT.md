# Post-1.7.0 delta — behavior report

Caveman. Scope: `v1.7.0..HEAD` + 1.8.0 remediation.

## Surface

7 commits after `v1.7.0` (version still 1.7.0/11 until cut):

- native AAB symbol gate
- BT A17 silent wake + off-LAN cache seat
- Daily Mix title clip
- scroll FPS pass 3
- UI density + typography subpage
- list chrome reactions/duration
- Wi‑Fi-only sync + cellular media policy

## Confirmed bugs → fixed (ADR-0106)

1. **BT seat omit cellular LOCAL_ONLY** — `isBtPlaybackNetworkBlocked` missed
   `NetworkPolicyState.isCellularHardLocal()` while `OfflineAwareHttpDataSource`
   blocked → doomed Exo open on cell hard-local. Fix: `computeBtPlaybackNetworkBlocked`.
2. **Syncing hang on null force job** — Settings Resync / CAS / policy skip →
   `syncNowAsync` null ×3 → early return, `_isDone` never set. Fix: set
   `_isError` + `_isDone`.
3. **DownloadManager offline mock/alias** — worker called `isQueueEnabled`;
   MockK stub on alias not hit → offline test red + real risk of wrong gate.
   Fix: gate on `isOfflineEnabled`; refresh transport each worker tick.
4. **Stale `wifiOrEthernet=true`** — optimistic default + no live refresh →
   pri0 could run under LOCAL_ONLY on cell. Fix: `updateTransport` in workerLoop.
5. **Legacy migrate re-apply** — new key wipe + leftover `download_mobile_data`
   re-migrated. Fix: `remove` legacy after migrate write.
6. **Offline seat forward-only** — cached tracks before current ignored → silence
   with cache in queue. Fix: wrap scan in `resolveOfflineStartIndex`.

## Baseline red (pre-fix)

- `DownloadManagerTest` offline skip (alias/gate)
- `ConnectivityNetworkWatcherTest` missing `hasTransport` stubs after ADR-0105

## Rejected / deferred

- Concurrent like rollback race — no confirm fail this pass
- VPN classified cellular — intentional transport taxonomy
- EQ dispose/recreate CPU spike — perf nicety, no user bug confirm
- Notif-denied “never silent” — ADR-0101 already logs; no code change

## Tests added/updated

- `NetworkPolicyHolderTest` migrate + clear legacy
- `DownloadManagerTest` cellular LOCAL_ONLY + stale wifi
- `PlaybackErrorRecoveryTest` wrap index + `computeBtPlaybackNetworkBlocked`
- `SyncingViewModelTest` null job → done/error
- `ConnectivityNetworkWatcherTest` transport stubs
