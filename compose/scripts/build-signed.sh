#!/bin/bash
# Build and sign release APK using keystore.properties
# Usage: ./scripts/build-signed.sh

set -e
cd "$(dirname "$0")/.."

if [ ! -f keystore.properties ]; then
    echo "❌ keystore.properties not found. Run step3-generate-upload-key.sh first."
    exit 1
fi

# Signing is wired via compose/keystore.properties in app/build.gradle.kts
# (signingConfigs.release). Do not pass -Pandroid.injected.signing.* with a
# relative storeFile — AGP resolves it against the daemon cwd and fails.
echo "=== Building signed release (project signingConfig) ==="
./gradlew :app:assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
echo ""
echo "✅ Signed APK: $APK"
ls -la "$APK"
echo "📤 Upload to Google Play Console → Internal Testing → Create release"
