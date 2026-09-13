# Contributing to ftpmusic

Thanks for your interest. This guide covers the local workflow.

## Prerequisites

- JDK 17 (`brew install openjdk@17` or equivalent)
- Android SDK (platform 35, build-tools)
- `adb` on PATH for device installs
- Optional: a Subsonic/OpenSubsonic server (Navidrome, Gonic, Airsonic-Advanced, …) for manual testing

## Build and test

All Gradle commands run from `compose/`; the root `Makefile` wraps the common ones:

```bash
make            # assemble debug APK
make test       # unit tests
make test-report # tests + JaCoCo coverage report
make quality    # ktlintCheck + detekt
make format     # ktlintFormat
make build      # assembleDebug
```

Install the repository-managed Git hooks once per clone:

```bash
make install-hooks
```

The pre-commit hook formats Kotlin and runs the quality gates. Commits stop
when formatting changes files; re-stage and commit again.

## Workflow

1. Open an issue describing the problem or proposal before large changes.
2. Keep pull requests focused; one concern per PR.
3. Add or update tests for behavior changes. New logic should keep the
   modified files at or above the project's 80% line/branch coverage gate.
4. Run `make test`, `make quality`, and `make build` before pushing.
5. Do not commit secrets: server credentials, API keys, keystores, or
   `keystore.properties`. Live-server integration tests read credentials
   from `FTPMUSIC_TEST_SERVER`, `FTPMUSIC_TEST_USER`, `FTPMUSIC_TEST_PASS`.

## Commit messages

Conventional Commits style (`feat:`, `fix:`, `perf:`, `docs:`, `refactor:`,
`test:`, `chore:`), imperative mood, concise subject.

## Architecture notes

- `CONTEXT.md` is the domain glossary.
- `docs/adr/` records architectural decisions; add an ADR for systemic changes.
- The app is local-first: Room is the source of truth, network calls are
  best-effort mirrors.

## Code of Conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
