#!/bin/bash
# Build and sign release APK using keystore.properties
# Usage: ./scripts/build-signed.sh

set -e
cd "$(dirname "$0")/.."

if [ ! -f keystore.properties ]; then
    echo "❌ keystore.properties not found. Run step3-generate-upload-key.sh first."
    exit 1
fi

# Read properties
source <(grep -v '^#' keystore.properties | sed 's/\(.*\)=\(.*\)/\1="\2"/')

echo "=== Building signed release ==="
echo "Keystore: $storeFile"
echo "Alias: $keyAlias"

./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file="$storeFile" \
  -Pandroid.injected.signing.store.password="$storePassword" \
  -Pandroid.injected.signing.key.alias="$keyAlias" \
  -Pandroid.injected.signing.key.password="$keyPassword"

echo ""
echo "✅ Signed APK: app/build/outputs/apk/release/app-release.apk"
echo "📤 Upload to Google Play Console → Internal Testing → Create release"
