plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.saul.geoalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.saul.geoalarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default basemap. OpenFreeMap: OSM vector tiles, no API key, no request limits.
        buildConfigField("String", "DEFAULT_STYLE_URL", "\"https://tiles.openfreemap.org/styles/liberty\"")
    }

    // fdroid: 100% FOSS, custom GPS geofence engine only.
    // gms:    adds Google Play Services geofencing, falls back to the custom engine when GMS is absent.
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
        }
        create("gms") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            // Unsigned here; release signing happens in Signet (or by the F-Droid build server).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

// Room schema history, checked in so migrations can be verified.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Map: MapLibre Native (BSD-2), OSM vector tiles, offline regions via OfflineManager.
    implementation("org.maplibre.gl:android-sdk:13.6.1")

    // Persistence
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Play Services geofencing: gms flavor ONLY. The fdroid flavor must stay free of proprietary deps.
    "gmsImplementation"("com.google.android.gms:play-services-location:21.4.0")
    "gmsImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
