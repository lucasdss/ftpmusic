# ADR-0162: deleteUnpublishedBuilds preserves active/pending pointers

## Status

Accepted (amends ADR-0147)

## Context

ADR-0147 sticky Home cards read `active_daily_mix_id`. Active rows remain
`status=ready`. `deleteUnpublishedBuilds` wiped all ready rows before pending
set → Home JOIN missed the mix until activate (card disappeared for minutes).

## Decision

SQL delete of building/ready for a recipe **excludes** ids equal to that
recipe’s `active_daily_mix_id` and `pending_daily_mix_id`.

## Consequences

- Pending regen keeps the previous Home card visible.
- Orphan building/ready rows still GC’d.
- No Home auto-activate (ADR-0147 policy unchanged).
