# Daily Mix sticky card during pending regen — behavior report

## Symptom

Rebuilding a Daily Mix made the Home card disappear until the new version
activated (logs: `pending set mixId=1` then no `sticky show` for that id for
~45 minutes until `activate`).

## Root cause

`deleteUnpublishedBuilds` deleted all `ready` rows, including the active
pointer target (active is a pointer, status stays `ready`).

## Fix

SQL keep-out for `active_daily_mix_id` / `pending_daily_mix_id` in
[`GenreMixDao.deleteUnpublishedBuilds`](compose/app/src/main/java/com/lucasdss/ftpmusic/app/data/db/Daos.kt).

ADR: [0162](adr/0162-daily-mix-delete-unpublished-keeps-pointers.md).
Spec: `specs/daily-mix-sticky-and-anti-repeat-spec.md`.
