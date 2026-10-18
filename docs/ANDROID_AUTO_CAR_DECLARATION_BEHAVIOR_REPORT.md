# Android Auto Car Declaration — Behavior Report

Caveman. ADR-0146.

## Symptom

User: Auto open → phone UI. Mini-player play/pause/skip OK. No car browse tree.

## Cause map

| Signal | Meaning |
|--------|---------|
| MediaSession live | Mini-player transport |
| No `automotive_app_desc` | Auto not mark app as media template host |
| `setSessionActivity(MainActivity)` | Open-app path → phone |
| Early sync `albums=0` | Empty children only — not phone launch |

## Fix

| Change | Effect |
|--------|--------|
| `@xml/automotive_app_desc` + car meta-data | Auto media declaration |
| Root + children CONTENT_STYLE list hints | HU list layout; echo client offline/recent/suggested |
| Keep sessionActivity | Notif / default-player unchanged |

## Edge

| Case | Result |
|------|--------|
| Sideload + no Unknown sources | App may still hide from Auto launcher |
| Empty Room | Root 10 folders; empty leaf lists |
| Notif tap | Still opens phone MainActivity (expected) |

## DHU / HU check

1. Reinstall build. Auto developer mode. Unknown sources if sideload.
2. Open FTP Music from Auto media launcher.
3. Expect: car browse (Recent / Queue / …). Not phone MainActivity.
4. Mini-player still controls playback.

## Coverage

`MediaSessionCallbackTest` root content-style. `AutomotiveAppDescRobolectricTest`.
Existing `AutoBrowseCatalogTest` / browse callback suite.
