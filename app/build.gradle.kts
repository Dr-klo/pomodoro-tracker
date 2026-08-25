import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

// Release signing. The keystore and its passwords live *outside* the repository: a signing key
// leaked into a public history has no revocation path, and .gitignore is only one layer of defence.
// Looked up in ~/.pomodoro; the old in-project location still works for a checkout not yet migrated.
val keystorePropsFile = sequenceOf(
    File(System.getProperty("user.home"), ".pomodoro/keystore.properties"),
    rootProject.file("keystore.properties")
).firstOrNull { it.exists() }

val keystoreProps = Properties().apply {
    keystorePropsFile?.let { file -> FileInputStream(file).use { load(it) } }
}

android {
    namespace = "com.drklo.pomodoro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drklo.pomodoro"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The exported schemas are the reference migrations are checked against, so they are versioned.
    sourceSets {
        // MigrationTestHelper reads them from the test APK's assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        // Unit tests touch pure logic only; stubbed android.* calls return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        if (keystorePropsFile != null) {
            create("release") {
                // Absolute path since the move out of the repo; a relative one still resolves
                // against the properties file that named it.
                storeFile = File(keystoreProps.getProperty("storeFile")).let { named ->
                    if (named.isAbsolute) named else File(keystorePropsFile.parentFile, named.path)
                }
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v3 carries the key-rotation record. The app is distributed as APK files and
                // lives entirely on this signature, so without v3 a compromised key could never be
                // replaced without orphaning every existing install. v4 is left off: it emits a
                // separate .idsig that only speeds up `adb install --incremental`.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Without R8 the APK was 41.7 MB, of which 42 MB was dex: material-icons-extended
            // compiles thousands of icons and the app draws eleven of them. Shrinking is what makes
            // the download reasonable for something handed out as a file.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Warnings must not accumulate: every one of them is either fixed or explicitly suppressed.
        allWarningsAsErrors = true
    }
    buildFeatures {
        compose = true
    }

    lint {
        // Anything new fails the build; the few accepted leftovers live in the baseline.
        warningsAsErrors = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
        disable += setOf(
            // Dependency freshness is a deliberate, separate chore — not a per-build gate.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            // The app ships as a plain APK, never as an AAB, so language splits cannot happen.
            "AppBundleLocaleChanges"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Catches what Android Lint does not: complexity, long methods, duplication, formatting.
// `detekt-formatting` wraps ktlint, so no separate formatting plugin is needed.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

// The plain `detekt` task looks at production code only; test code is held to the same bar.
tasks.named("check") { dependsOn("detektDebugUnitTest") }

dependencies {
    detektPlugins(libs.detekt.formatting)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
