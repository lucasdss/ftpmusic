# Daily Mix rebuild anti-repeat — behavior report

## Symptom

Users reported the same songs after Daily Mix rebuild. Build sizes vary
(30–120 random); the issue is cross-rebuild overlap, not intra-mix dupes.

## Root cause

Listen-only exclude set, &lt;30 fresh cliff that disabled exclusion, weak −1
penalty, calendar-yesterday lookup under sticky versioning.

## Fix

- Full prior lists (active + 2 superseded) + 7-day global honest listens.
- Prefer fresh always; hard-fill re-admits when needed (no cliff).
- Sticky-safe penalty set; keep 2 superseded on activate.

ADR: [0163](adr/0163-daily-mix-rebuild-anti-repeat.md).
Spec: `specs/daily-mix-sticky-and-anti-repeat-spec.md`.
