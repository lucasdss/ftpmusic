# Critical Path — Market Gap Behavior Report

Caveman. Track 4 balanced. Source: docs audits + peer market (Symfonium/Ultrasonic/Tempo) + queue gold (Spotify/Apple).

## App

ftpmusic `1.6.0` — OpenSubsonic client. Local-first Room, Media3 dual-queue, Cast, FTS5, offline cache, Daily Mixes, Compose M3.

## Two north stars

| Set | Apps | Docs today |
|-----|------|------------|
| Queue gold | Spotify, Apple (+ Deezer/Tidal) | Heavy ADR-0039/0074 |
| Self-host peers | Symfonium, Ultrasonic, Tempo | Under-documented |

## Matrix

| Cap | FTP | Peers | Gold |
|-----|-----|-------|------|
| Dual queue | Lead | Strong/basic | SoT |
| Cast | Strong; CP append lag | Strong/partial | Strong |
| Offline Downloads UI | Engine yes; browse no | Yes | N/A |
| Android Auto browse | Missing | Table-stakes | Native |
| Search FTS | Strong | OK–strong | Strong |
| Lyrics songId | Partial | Varies | Strong |
| Star 1–5 | Missing | Often | Hearts/stars |
| Daily Mix recipes | Differentiator | Different | Discover-class |

## Keep

Dual CONTEXT/PRIORITY (no YT wipe). Mini no seek. Favorites tab peer. Cast CP honest until append real.

## P0 ship order

1. Android Auto browse MVP — ADR-0091
2. Offline Downloads screen + stale path heal
3. Cast Continuous Play Step 2

## P1 after

Guest createShare. Delete QueueScreen. Overwrite prune. Go-to-album. NP error banner. Lyrics-by-songId. setRating UI. Similar API primary.

## P2 later

Tablet rail. i18n. FTS chunk rebuild. Crashlytics/Fastlane. Full Dual-on-Cast. Internet Radio Auto node.

## Reject

YT dismiss-session. Mini interactive seek. Fake Cast CP ON.

## Execution status (2026-10-07)

| Phase | Status |
|-------|--------|
| A Auto browse MVP | Shipped ADR-0091 |
| B Downloads screen | Shipped ADR-0092 |
| C Cast CP Step 2 | Shipped ADR-0093 |
| D API hygiene | Shipped ADR-0094 |
| Validate | assembleDebug + targeted tests green |
