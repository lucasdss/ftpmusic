# Play policy compliance — October 29, 2026

**Announcement:** [Policy announcement: October 29, 2026](https://support.google.com/googleplay/android-developer/answer/18100766)  
**App under review:** FTP Music **2.1.0** (`versionCode` 27), `targetSdk` **36**, Room 68  
**Recorded:** 2026-11-03

## Verdict

FTP Music is **compliant** with enforceable items in this announcement at
`targetSdk` 36. No emergency permission or feature change is required to keep
shipping. Remaining work is Console ops, vitals tracking for Feb 2027, and
Local Network readiness when bumping to API 37 (ADR-0153).

Deceptive Behavior (review manipulation): we do not engage in behaviors that
manipulate the Play app review process. Releases are signed local AABs uploaded
through Play Console; no review-farming or cloaking.

## Item matrix

| Policy | Applies? | Status | Evidence / action |
|--------|----------|--------|-------------------|
| **Local Network** (`ACCESS_LOCAL_NETWORK`, API 37+) | When `targetSdk ≥ 37` | N/A today — SDK 36 gets implicit LAN via `INTERNET` | [ADR-0153](adr/0153-local-network-permission-api-37.md); do **not** declare while on 36 |
| **Manage Contacts** | No | Compliant | No contacts perms; guarded by `PlayRestrictedPermissionsTest` |
| **AppFunctions API** | No | Compliant | No AppFunctions dependency or metadata |
| **Subscriptions eligibility discounts** | No | Compliant | No Play Billing / IAP / subscriptions |
| **Location** (clarification; API 37+ Jan 27 2027) | No | Compliant | No location perms; guard |
| **All Files Access** | No | Compliant | Cover import = `PickVisualMedia`; no `MANAGE_EXTERNAL_STORAGE` / `READ_MEDIA_*` |
| **AccessibilityAPI** | No | Compliant | Not an accessibility tool; no `isAccessibilityTool` |
| **Deceptive Behavior** | Process | Compliant by practice | See note above |
| **Families Self-Certified Ads SDK** | No | Compliant | No ads / AdMob |
| **Cryptocurrency** | No | N/A | — |
| **SMS / call log** | No | Compliant | No SMS/call-log perms; guard |
| **Payments (AI/agentic)** | No | Compliant | No digital purchases |
| **Play Age Signals** | Optional | Deferred | Not child-directed; integrate only if Console/product requires |
| **Japan payments auth** (Dec 1 2026) | Only if selling JP via Play | **N/A default** | App has no IAP; confirm merchant account in Console |
| **Trader status** (Nov 2026) | Developer account | **Manual Console** | Checklist below |
| **Games for cars / XR** | No | N/A | Android Auto is **media** templates ([ADR-0146](adr/0146-android-auto-car-media-declaration.md)) |
| **Memory + DEX quality** (Feb 2027) | Yes | On track for DEX | Release `isMinifyEnabled = true` + `proguard-android-optimize.txt`; track Android vitals after next AAB |

## Local Network (future API 37)

Core LAN uses that will need `ACCESS_LOCAL_NETWORK` + Play declaration when
targeting 37:

1. User-typed Navidrome/Subsonic URLs on private LAN ([`ServerConnectionInput`](../compose/app/src/main/java/com/lucasdss/ftpmusic/app/ui/server/ServerConnectionInput.kt)).
2. Cast / media transfer to speakers and TVs (Play use case: **Media casting**).

Device pickers alone cannot replace arbitrary typed server URLs. Declaration
prompts ~June 2027; enforcement **August 25, 2027**. Details: ADR-0153.

## DEX / memory readiness (Feb 2027 reminder)

| Control | Current |
|---------|---------|
| R8 minify | On (`isMinifyEnabled = true`) |
| Optimize rules | `proguard-android-optimize.txt` + `proguard-rules.pro` |
| `isShrinkResources` | **Off** — evaluate separately (dynamic resource name risk); not required for this policy cut |
| Memory P90 / bitmap / DEX % | Record from Android vitals after next Play upload |

Thresholds: apps with >10 MB DEX need ≥25% optimization, shrinking, and
obfuscation each; memory bad-behavior thresholds are RAM-tiered (Play Console
technical quality requirements).

## Manifest regression guard

[`PlayRestrictedPermissionsTest`](../compose/app/src/test/java/com/lucasdss/ftpmusic/app/PlayRestrictedPermissionsTest.kt)
fails the build if restricted sensitive permissions appear unexpectedly in
[`AndroidManifest.xml`](../compose/app/src/main/AndroidManifest.xml).

## In-app disclosure

Settings → About opens the canonical privacy policy:

`https://blog.xdsa.net/posts/ftp-music-privacy-policy/` ([`PRIVACY.md`](../PRIVACY.md)).

## Console checklist (human)

- [ ] Complete **trader status** declaration in Play Console (Nov 2026)
- [ ] Confirm Japan seller authorization N/A (no IAP) or complete form by Dec 1 2026 if merchant account sells JP
- [ ] Reconfirm Data Safety + FGS (`mediaPlayback`, `dataSync`) declarations for 2.1.0
- [ ] Target audience / content rating: not Designed for Families; Age Signals deferred
- [ ] After next AAB: record memory + DEX optimization vitals vs Feb 2027 thresholds
