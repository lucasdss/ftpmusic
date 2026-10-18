# ADR 0146 — Android Auto Car Media Declaration

Date: 2026-10-18
Status: Accepted
Related: ADR-0091 (browse MVP), ADR-0131 (peer parity), ADR-0071 (sessionActivity)
Behavior: `docs/ANDROID_AUTO_CAR_DECLARATION_BEHAVIOR_REPORT.md`

## Context

Browse stack shipped (MediaLibraryService + AutoBrowseCatalog) but manifest
never declared Auto media templates. Symptom: mini-player transport OK on
car; opening FTP Music launched phone `MainActivity` via `setSessionActivity`.

Root gap: missing `automotive_app_desc` (`<uses name="media"/>`) and
`com.google.android.gms.car.application` meta-data.

## Decision

1. **Declare media** — `res/xml/automotive_app_desc.xml` + application
   meta-data `com.google.android.gms.car.application`.
2. **Content-style root** — `onGetLibraryRoot` returns `LibraryParams` with
   list hints for browsable + playable (`MediaConstants` CONTENT_STYLE_*).
3. **Keep sessionActivity** — `MainActivity` PendingIntent stays for
   notification / default-player claim (ADR-0071). Not removed.
4. **Out** — AAOS Car App Library; Builder context move to `MediaService.this`
   (only if DHU still fails after declaration).

## Consequences

- Auto HUs can show native media browse templates instead of phone Activity.
- Sideloaded builds still need Auto developer Unknown sources for DHU/HU.
