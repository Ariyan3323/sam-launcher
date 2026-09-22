plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "de.szalkowski.activitylauncher"
    compileSdk = 36

    defaultConfig {
        // Canonical package name for the unified Sam Launcher application.
        applicationId = "com.fgmembers.samlauncher"
        minSdk = 21
        targetSdk = 36
        versionCode = 81
        versionName = "2.2.20"
        buildConfigField("String", "GEMINI_API_KEY", "\"${System.getenv("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${System.getenv("OPENAI_API_KEY") ?: ""}\"")
        buildConfigField("String", "SAM_KNOWLEDGE_FEED_URL", "\"${System.getenv("SAM_KNOWLEDGE_FEED_URL") ?: ""}\"")

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("oss") {
            // for direct distribution
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_SMS_READ", "true")
        }
        create("playStore") {
            // includes Google Play Store specific additions
            dimension = "distribution"
            minSdk = 23
            buildConfigField("boolean", "ALLOW_SMS_READ", "false")
        }
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file(System.getenv("KEYSTORE") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.9"
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        disable.add("MissingTranslation")
    }
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.48")
    "playStoreImplementation"("com.google.android.play:review-ktx:2.0.2")
    kapt("com.google.dagger:hilt-compiler:2.48")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
