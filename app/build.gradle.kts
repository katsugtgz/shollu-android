import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ebsoft.shollu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ebsoft.shollu"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.10.0"

        // Version from CI (RELEASE_* env vars on tag push) or -P gradle properties if available
        val versionNameOverride = System.getenv("RELEASE_VERSION_NAME")?.takeIf { it.isNotBlank() }
            ?: project.findProperty("RELEASE_VERSION_NAME")?.toString()
        if (versionNameOverride != null) {
            versionName = versionNameOverride
        }
        val versionCodeOverride = System.getenv("RELEASE_VERSION_CODE")?.takeIf { it.isNotBlank() }
            ?: project.findProperty("RELEASE_VERSION_CODE")?.toString()
        versionCodeOverride?.toIntOrNull()?.let { override ->
            if (override > 0) versionCode = override
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Define signing configs BEFORE buildTypes (required for proper evaluation order)
    signingConfigs {
        create("release") {
            // Credential resolution order: RELEASE_* environment variables (CI) first,
            // then app/signing.properties (local dev), then defaults below.
            val signingProps = Properties()
            val signingPropsFile = rootProject.file("app/signing.properties")
            if (signingPropsFile.exists()) {
                // Note: raw file reads here are not configuration-cache inputs; the project
                // does not enable the configuration cache. If enabling it later, re-work this
                // to providers.fileContents(...) so credential edits invalidate the cache.
                signingPropsFile.inputStream().use { signingProps.load(it) }
            }

            fun signingValue(envName: String, propsKey: String = envName): String? =
                System.getenv(envName)?.takeIf { it.isNotBlank() }
                    ?: signingProps.getProperty(propsKey)?.takeIf { it.isNotBlank() }

            storeFile = rootProject.file(signingValue("RELEASE_STORE_FILE") ?: "app/release.keystore")
            keyAlias = signingValue("RELEASE_KEY_ALIAS") ?: "shollu-release"
            signingValue("RELEASE_KEY_PASSWORD")?.let { keyPassword = it }
            signingValue("RELEASE_STORE_PASSWORD")?.let { storePassword = it }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Signing configuration - use getByName since we defined it above
            signingConfig = signingConfigs.getByName("release")
        }
        
        debug {
            // Always have a valid signing config for debug builds
            signingConfig = signingConfigs.getByName("debug")
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
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // AppWidget (Glance)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Play Services Location
    implementation(libs.play.services.location)

    // Utilities
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
