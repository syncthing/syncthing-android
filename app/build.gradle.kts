plugins {
    id("com.android.application")
    id("com.github.ben-manes.versions")
    id("com.github.triplet.play") version "3.7.0"
}

dependencies {
    implementation("eu.chainfire:libsuperuser:1.1.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("com.annimon:stream:1.2.2")
    implementation("com.android.volley:volley:1.2.1")
    implementation("commons-io:commons-io:2.11.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        isTransitive = false
    }
    implementation("com.google.zxing:core:3.4.1")

    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("com.google.dagger:dagger:2.49")
    annotationProcessor("com.google.dagger:dagger-compiler:2.49")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.annotation:annotation:1.2.0")
}

android {
    val ndkVersionShared = rootProject.extra.get("ndkVersionShared")
    // Changes to these values need to be reflected in `../docker/Dockerfile`
    compileSdk = 33
    buildToolsVersion = "33.0.2"
    ndkVersion = "${ndkVersionShared}"

    buildFeatures {
        dataBinding = true
    }

    defaultConfig {
        applicationId = "com.nutomic.syncthingandroid"
        minSdk = 21
        targetSdk = 33
        versionCode = 4372
        versionName = "1.27.2-rc.2"
        testApplicationId = "com.nutomic.syncthingandroid.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("SYNCTHING_RELEASE_STORE_FILE")?.let(::file)
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = System.getenv("SYNCTHING_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isJniDebuggable = true
            isRenderscriptDebuggable = true
            isMinifyEnabled = false
        }
        getByName("release") {
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Otherwise libsyncthing.so doesn't appear where it should in installs
    // based on app bundles, and thus nothing works.
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

play {
    serviceAccountCredentials.set(
        file(System.getenv("SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE") ?: "keys.json")
    )
    track.set("beta")
}

/**
 * Some languages are not supported by Google Play, so we ignore them.
 */
tasks.register<Delete>("deleteUnsupportedPlayTranslations") {
    delete(
        "src/main/play/listings/de_DE/",
        "src/main/play/listings/el-EL/",
        "src/main/play/listings/en/",
        "src/main/play/listings/eo/",
        "src/main/play/listings/eu/",
        "src/main/play/listings/nb/",
        "src/main/play/listings/nl_BE/",
        "src/main/play/listings/nn/",
        "src/main/play/listings/ta/",
    )
}
