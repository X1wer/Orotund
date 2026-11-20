plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.x1wer.szabadzene"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.x1wer.szabadzene"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "1.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // A migrációs teszt a kiexportált Room-sémákat olvassa.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        // Kis, generált tesztfájlok (tools/generate_test_audio.py --fixtures) mindkét tesztkészletnek.
        getByName("androidTest").assets.directories.add("$projectDir/src/sharedTest/assets")
        getByName("test").resources.directories.add("$projectDir/src/sharedTest/assets")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    constraints {
        // A navigáció 1.7.3-at hozna; a Room migrációs tesztje (room-testing) legalább 1.8-at igényel,
        // és a tesztek az alkalmazás futásidejű verzióit használják.
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    // Címkeírás (MP3, M4A, FLAC, OGG Vorbis): LGPL-2.1, GPL-3.0-val kompatibilis.
    implementation(libs.jaudiotagger)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // A Compose tesztkönyvtár régebbi Espressót hozna, ami Android 15+ alatt nem fut.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.room.testing)
}
