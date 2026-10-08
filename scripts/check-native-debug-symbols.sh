#!/usr/bin/env bash
# Gate: every Play/internal testing AAB must keep release native symbol config
# and must package extractable symbols when unstripped .so exist (ADR-0018).
#
# Exit 0 = PASS (optional WARN for pre-stripped dependency .so only)
# Exit 1 = FAIL
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="$ROOT/compose/app/build.gradle.kts"
AAB="$ROOT/compose/app/build/outputs/bundle/release/app-release.aab"
MERGED_LIBS="$ROOT/compose/app/build/intermediates/merged_native_libs/release"
SYMBOLS_ZIP_DIR="$ROOT/compose/app/build/outputs/native-debug-symbols/release"

fail() {
  echo "❌ native-debug-symbols: $*" >&2
  exit 1
}

warn() {
  echo "⚠️  native-debug-symbols: $*" >&2
}

pass() {
  echo "✅ native-debug-symbols: $*"
}

# 1) Static: release must set SYMBOL_TABLE (or FULL)
if [[ ! -f "$GRADLE_FILE" ]]; then
  fail "missing $GRADLE_FILE"
fi

# Require SYMBOL_TABLE or FULL (release is the only buildType that sets ndk today).
if ! grep -qE 'debugSymbolLevel[[:space:]]*=[[:space:]]*"(SYMBOL_TABLE|FULL)"' "$GRADLE_FILE"; then
  fail "ndk.debugSymbolLevel must be SYMBOL_TABLE or FULL in compose/app/build.gradle.kts"
fi
pass "release debugSymbolLevel configured"

# 2) AAB required (caller runs bundleRelease / make bundle-release first)
if [[ ! -f "$AAB" ]]; then
  fail "missing AAB at $AAB — run: make bundle-release (or cd compose && ./gradlew :app:bundleRelease)"
fi

SO_LIST="$(unzip -Z1 "$AAB" | grep -E '^base/lib/.+\.so$' || true)"
SO_COUNT="$(printf '%s\n' "$SO_LIST" | grep -c '.' || true)"
if [[ -z "${SO_LIST// }" ]]; then
  SO_COUNT=0
fi

if [[ "$SO_COUNT" -eq 0 ]]; then
  pass "no native .so in AAB — nothing to symbolicate"
  exit 0
fi

echo "   packaged .so ($SO_COUNT):"
printf '%s\n' "$SO_LIST" | sed 's/^/     /'

# 3) Embedded metadata or AGP zip
HAS_META=0
if unzip -Z1 "$AAB" | grep -qiE 'BUNDLE-METADATA/.+debugsymbols|BUNDLE-METADATA/.+native.?debug|BUNDLE-METADATA/.+symbols'; then
  HAS_META=1
fi
HAS_ZIP=0
if compgen -G "$SYMBOLS_ZIP_DIR"'/*.zip' >/dev/null 2>&1; then
  HAS_ZIP=1
fi

if [[ "$HAS_META" -eq 1 || "$HAS_ZIP" -eq 1 ]]; then
  pass "native debug symbols present (metadata=$HAS_META zip=$HAS_ZIP)"
  exit 0
fi

# 4) No metadata: fail only if merged libs contain unstripped .so
UNSTRIPPED=()
STRIPPED=0
FOUND_MERGED=0
if [[ -d "$MERGED_LIBS" ]]; then
  while IFS= read -r -d '' so; do
    FOUND_MERGED=1
    info="$(file -b "$so" 2>/dev/null || true)"
    # "not stripped" / missing the word stripped → treat as having symbols to package
    if echo "$info" | grep -qi 'not stripped'; then
      UNSTRIPPED+=("$so")
    elif echo "$info" | grep -qi 'stripped'; then
      STRIPPED=$((STRIPPED + 1))
    else
      # Ambiguous file(1) output — require symbols to be safe
      UNSTRIPPED+=("$so")
    fi
  done < <(find "$MERGED_LIBS" -type f -name '*.so' -print0 2>/dev/null)
fi

if [[ "${#UNSTRIPPED[@]}" -gt 0 ]]; then
  echo "   unstripped natives without AAB/zip symbols:" >&2
  for s in "${UNSTRIPPED[@]}"; do
    echo "     $s" >&2
  done
  fail "unstripped .so present but AAB has no debugsymbols metadata / native-debug-symbols zip"
fi

if [[ "$FOUND_MERGED" -eq 1 && "$STRIPPED" -gt 0 ]]; then
  warn "AAB has .so but all merged natives are pre-stripped (e.g. androidx.graphics.path) — Play soft-warn OK"
  pass "accepted: no extractable symbols (NO-SOURCE)"
  exit 0
fi

# Merged intermediates missing (stale clean) but AAB has .so and no metadata
warn "could not inspect merged_native_libs; AAB has .so without embedded symbols"
fail "rebuild with: cd compose && ./gradlew :app:bundleRelease then re-run this check"
