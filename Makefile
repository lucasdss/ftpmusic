# ftpmusic — build, test, install
# Requires: JDK 17, Android SDK, adb

export ANDROID_HOME := $(HOME)/Library/Android/sdk
export PATH       := $(ANDROID_HOME)/platform-tools:$(PATH)
# Resolve JDK 17 via the version-independent brew symlink (survives openjdk
# upgrades, e.g. 17.0.19 -> 17.0.20). Falls back to PATH java if brew is
# unavailable. Override with: make JAVA_HOME=/path/to/jdk ...
JAVA_HOME ?= $(shell brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
export JAVA_HOME

GRADLE := cd compose && ./gradlew

.PHONY: all build install clean-install test test-report lint quality format install-hooks logs ui-logs uninstall

# ── Default: build debug APK ──────────────────────────────────────────────
all: build

build:
	$(GRADLE) assembleDebug

# ── Install on phone ──────────────────────────────────────────────────────
install: build
	@APK=$$(find compose/app/build/outputs/apk/debug -name '*.apk' 2>/dev/null | head -1); \
	if [ -z "$$APK" ]; then echo "❌ No APK found — build failed?"; exit 1; fi; \
	adb install -r "$$APK"
	@echo "📱 Installed on $$(adb devices | grep -w device | awk '{print $$1}')"

# ── Clean install (wipes app data, fresh start) ───────────────────────────
clean-install:
	adb uninstall com.lucasdss.ftpmusic.app 2>/dev/null || true
	$(GRADLE) assembleDebug
	@APK=$$(find compose/app/build/outputs/apk/debug -name '*.apk' 2>/dev/null | head -1); \
	adb install "$$APK"
	@echo "🧹 Clean install complete"

# ── Tests ─────────────────────────────────────────────────────────────────
test:
	$(GRADLE) testDebugUnitTest

test-report:
	$(GRADLE) testDebugUnitTest jacocoTestReport

# ── Code quality ──────────────────────────────────────────────────────────
lint:
	$(GRADLE) lintDebug

quality:
	$(GRADLE) ktlintCheck detekt

# ── Format Kotlin ─────────────────────────────────────────────────────────
format:
	$(GRADLE) ktlintFormat

# ── Install repository-managed Git hooks ──────────────────────────────────
install-hooks:
	git config core.hooksPath .githooks
	@echo "Git hooks installed from .githooks/"


# ── Phone logs ────────────────────────────────────────────────────────────
logs:
	adb -s $(DEVICE_ID) logcat -c
	adb -s $(DEVICE_ID) logcat ftpmusic-*:V ftpmusic:D *:S

ui-logs:
	adb -s $(DEVICE_ID) logcat -c
	adb -s $(DEVICE_ID) logcat -v brief | grep -i "ftpmusic\|compose\|subsonic\|cast"

# ── Uninstall from phone ──────────────────────────────────────────────────
uninstall:
	adb -s $(DEVICE_ID) uninstall com.lucasdss.ftpmusic.app
