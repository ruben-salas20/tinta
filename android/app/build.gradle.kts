plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// versionCode crece con cada versión X.Y.Z: 0.1.0 → 100.
val appVersion: String = (project.findProperty("tintaVersion") as String?) ?: error("Falta tintaVersion en gradle.properties")
val appVersionCode: Int = appVersion.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Con -PreleasesApi=<url> se prueba la actualización con un servidor falso.
val releasesApi: String = (project.findProperty("releasesApi") as String?)
    ?: "https://api.github.com/repos/ruben-salas20/tinta/releases/latest"

// Sin estas variables, assembleRelease sale sin firmar.
val releaseKeystore: String? = System.getenv("TINTA_KEYSTORE")

android {
    namespace = "dev.rubensalas.tinta"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.rubensalas.tinta"
        minSdk = 34
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "RELEASES_API", "\"$releasesApi\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("publicacion") {
                storeFile = file(releaseKeystore)
                storeType = "PKCS12"
                storePassword = System.getenv("TINTA_KEYSTORE_PASSWORD")
                keyAlias = "tinta"
                keyPassword = System.getenv("TINTA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("publicacion")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
