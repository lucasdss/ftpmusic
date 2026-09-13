# ADR 0030: Local Kotlin Quality Gates

## Status

Accepted

## Context

Project had no Detekt or ktlint integration. GitHub Actions was rejected due to
cost preference. Enforcement must work through Gradle and local Git hooks.
Repository contains existing Kotlin style debt and large architecture
coordinators. Baselines were rejected because they hide file-specific debt.

## Decision

Use two non-overlapping tools:

- ktlint formats and verifies Kotlin style.
- Detekt verifies enabled correctness and maintainability rules.

`check` includes both plugin verification tasks. Checked-in
`.githooks/pre-commit` auto-formats safely, aborts when files change, then runs
both checks. Developers activate it with `make install-hooks`.

No Detekt baseline exists. No failure is ignored. Rules unsuitable for current
project semantics are disabled explicitly in
`compose/config/detekt/detekt.yml`, with rationale beside each policy.

Partially staged Kotlin files are rejected. Auto-staging is forbidden because
it can commit unrelated working-tree hunks.

## Alternatives

### GitHub Actions

Rejected by owner due to cost preference.

### Husky and lint-staged

Rejected. Repository has no Node toolchain. Adding npm solely for Git hooks
creates unnecessary dependency and lockfile surface.

### Spotless

Rejected. It overlaps ktlint formatting and creates two formatting interfaces.

### Detekt Formatting Plugin

Rejected. ktlint already owns formatting; duplicate rules can conflict.

### Detekt Baseline

Rejected. Strict cleanup chosen.

### Auto-Stage Formatted Files

Rejected. Partial staging can silently include unrelated edits.

## Consequences

- First adoption commit contains broad mechanical formatting.
- Commits stop when formatting changes files.
- Local hooks remain bypassable with `--no-verify`.
- New clones must run `make install-hooks`.
- No server-side quality guarantee exists without CI.
- Architecture complexity needs separate targeted refactors; global thresholds
  high enough to admit current coordinators would be meaningless.
