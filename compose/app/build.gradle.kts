import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.lucasdss.ftpmusic.app"
    compileSdk = 36

    // Release signing — Play App Signing uses an upload key. The keystore and
    // credentials live in compose/keystore.properties (gitignored); when absent
    // the release buildType simply has no signingConfig (unsigned artifacts).
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(FileInputStream(keystorePropertiesFile))
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.lucasdss.ftpmusic.app"
        minSdk = 26
        targetSdk = 36
        // Play Console requires monotonically increasing versionCode per upload.
        versionCode = 2
        versionName = "1.0.1"
        // Release-safe Log.w tracing for image/config diagnostics. Keep false in
        // shipped builds; flip to true for a local diagnostic build when needed.
        buildConfigField("boolean", "IMAGE_DIAGNOSTICS", "false")
    }

    buildTypes {
        debug {
            // Emits build/outputs/unit_test_code_coverage/debugUnitTest/…exec,
            // consumed by the jacocoTestReport task (Coverage Agent gate).
            isTestCoverageEnabled = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/native/*",
                "META-INF/native-image/*",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Required for Robolectric Compose UI tests (createComposeRule)
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1g" // default 512m intermittently OOMs the jacoco javaagent
                // Robolectric Compose tests flake with AppNotIdleException when
                // sandboxes compete for CPU, and shared @Volatile statics
                // (DynamicBaseUrl) leak across classes under multi-fork orderings.
                // One fork = deterministic order for the release gate.
                it.maxParallelForks = 1
            }
        }
    }
}

detekt {
    allRules = false
    basePath = rootProject.projectDir.absolutePath
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

ktlint {
    version.set("1.8.0")
    android.set(true)
    ignoreFailures.set(false)
    outputToConsole.set(true)
    verbose.set(true)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt DI
    val hiltVersion = "2.52"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Media3 (ExoPlayer + Cast)
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")

    // Networking
    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Netty HTTP/2 server for local proxy
    val nettyVersion = "4.1.115.Final"
    implementation("io.netty:netty-codec-http2:$nettyVersion")
    implementation("io.netty:netty-handler:$nettyVersion")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.65.Final")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.7")

    // Palette (album art color extraction)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Cast + Google Play Services
    // Force 21.5.0: media3-cast 1.9+/1.10+ transitively requires cast-framework 22.1.0,
    // which has a regression (googlecast/CastVideos-android#144, issuetracker 379962316):
    // session auto-resume after process death fails with internal error 2152 (~100% repro).
    // 21.5.0 is the last known-good version; media3 only uses stable APIs (getCurrentCastSession,
    // getRemoteMediaClient, setVolume, MediaQueue) that exist in 21.5.0.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0") {
        exclude(group = "com.google.android.gms", module = "play-services-cast")
    }
    implementation("com.google.android.gms:play-services-cast:21.5.0")
    configurations.all {
        resolutionStrategy {
            force("com.google.android.gms:play-services-cast-framework:21.5.0")
            force("com.google.android.gms:play-services-cast:21.5.0")
        }
    }

    // Reorderable list
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Testing
    testImplementation("junit:junit:4.13.2")

    // WorkManager — periodic background sync
    val workVersion = "2.9.1"
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    testImplementation("androidx.work:work-testing:$workVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Real org.json on the unit-test classpath (android.jar ships a stub) so
    // the iTunes/MusicBrainz parse paths are exercisable.
    testImplementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Robolectric — integration tests (simulate Android framework on JVM)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*",
        "**/Manifest*.*", "**/*Test*.*", "**/di/**",
        "**/Dagger*.*", "**/Hilt*.*", "**/*_Factory*.*",
        "**/*_MembersInjector*.*", "**/*_HiltComponents*.*",
        // Room/KSP-generated DAO and database implementations — machine code,
        // same rationale as the Dagger/Hilt exclusions above. Exposed once the
        // report moved onto the runtime jar (2026-08-27 fix); no hand-written
        // class in src/main matches *_Impl.
        "**/*_Impl.class", "**/*_Impl$*.class",
    )
    // Coverage Agent root cause (2026-08-27): the unit-test JVM loads app
    // classes from the RUNTIME jar (transformDebugClassesWithAsm output), which
    // rewrites the Hilt @AndroidEntryPoint classes (javap: MediaService extends
    // Hilt_MediaService there vs MediaLibraryService in tmp/kotlin-classes).
    // Reporting against tmp/kotlin-classes therefore produced a class-id
    // mismatch ("Execution data for class ... does not match") and the outer
    // Hilt-annotated classes reported a hard ZERO by construction. Point the
    // report at the same runtime jar the tests actually executed.
    val runtimeJar =
        "$buildDir/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar"
    val baseTree = if (file(runtimeJar).exists()) {
        zipTree(runtimeJar)
    } else {
        fileTree("$buildDir/tmp/kotlin-classes/debug")
    }
    val debugTree = baseTree.matching { exclude(fileFilter) }
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree("$buildDir") {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
}

// Room schema export — enables MigrationTestHelper-based migration tests
// (see app/schemas/ — committed; used by the coverage/migration test suite).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ── Waveform asset generation ──────────────────────────────────────────────
// Decodes ../waveform/{genre}/*.dat (20-byte header [1,1,48000,256,count] +
// count × u16 LE RMS magnitudes) into compact 400-bucket JSON assets committed
// under src/main/assets/waveform/{genre}/{name}.json (~2.9 KB per track).
// Re-run automatically on preBuild whenever the raw .dat files change; if the
// raw dir is absent (CI/clean checkout) the committed JSONs are used as-is.
val rawWaveformDir: File = rootProject.file("../waveform")
val waveformAssetsDir: File = layout.projectDirectory.dir("src/main/assets/waveform").asFile

// Buckets per track the generator emits — must match
// WaveformAssetLoader.ASSET_BAR_COUNT (the app-side constant).
val assetBars = 400

fun decodeWaveformDat(file: File): List<Float> {
    val bytes = file.readBytes()
    require(bytes.size >= 20) { "Truncated waveform header: ${file.name}" }
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.int // magic 1
    buf.int // magic 1
    buf.int // sample rate (48000)
    buf.int // samples per bucket (256)
    val count = buf.int
    require(count > 0 && bytes.size >= 20L + count * 2L) { "Bad waveform length: ${file.name}" }
    val vals = IntArray(count)
    for (i in 0 until count) vals[i] = buf.short.toInt() and 0xFFFF
    return waveformBars(vals, assetBars)
}

fun waveformBars(vals: IntArray, n: Int): List<Float> {
    if (vals.isEmpty()) return List(n) { 0f }
    val bars = FloatArray(n)
    val bucket = vals.size.toDouble() / n
    for (i in 0 until n) {
        val lo = (i * bucket).toInt()
        val hi = maxOf(lo + 1, ((i + 1) * bucket).toInt())
        var sum = 0L
        var mx = 0
        val end = minOf(hi, vals.size)
        for (j in lo until end) {
            val v = vals[j]
            sum += v
            if (v > mx) mx = v
        }
        val len = maxOf(1, end - lo)
        val mean = sum.toDouble() / len / 65535.0
        val max = mx / 65535.0
        bars[i] = (0.65 * mean + 0.35 * max).toFloat()
    }
    // Per-file normalization: scale by 95th percentile so a lone spike cannot
    // flatten the whole waveform; clamps to [0,1]. True silence stays 0.
    val sorted = bars.sorted()
    val p95 = sorted[(n * 0.95).toInt().coerceAtMost(n - 1)]
    val scale = if (p95 > 0.01f) p95 else 1f
    return bars.map { (it / scale).coerceIn(0f, 1f) }
}

fun waveformJson(bars: List<Float>): String {
    // Locale-safe 4-decimal formatting; the app's strict parser reads these back.
    return bars.joinToString(",", "[", "]") { "%.4f".format(Locale.US, it) }
}

tasks.register("generateWaveformAssets") {
    group = "build"
    description = "Decodes ../waveform/**/*.dat into 100-bar JSON assets under src/main/assets/waveform"
    val datInputs = fileTree(rawWaveformDir) { include("**/*.dat") }
    inputs.files(datInputs)
    outputs.dir(waveformAssetsDir)
    onlyIf { rawWaveformDir.exists() && datInputs.files.isNotEmpty() }
    doLast {
        val files = datInputs.files.filter { it.isFile }.sortedBy { it.absolutePath }
        files.forEach { dat ->
            val outDir = File(waveformAssetsDir, dat.parentFile.name).apply { mkdirs() }
            val outFile = File(outDir, dat.nameWithoutExtension + ".json")
            val bars = try {
                decodeWaveformDat(dat)
            } catch (e: Exception) {
                logger.warn("Skipping malformed waveform ${dat.name}: ${e.message}")
                return@forEach
            }
            outFile.writeText(waveformJson(bars))
        }
        logger.lifecycle("Generated ${files.size} waveform assets -> $waveformAssetsDir")
    }
}

// Regenerate before asset merging so APKs always include fresh waveforms.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn("generateWaveformAssets")
}
tasks.named("preBuild") { dependsOn("generateWaveformAssets") }
