import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.koretide.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.koretide.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.koretide.app.HiltTestRunner"

        buildConfigField("String", "KHOA_API_KEY", "\"${localProps.getProperty("KHOA_API_KEY", "")}\"")
        buildConfigField("String", "KMA_API_KEY",  "\"${localProps.getProperty("KMA_API_KEY",  "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// Removes hiltJavaCompile* tasks, eliminating the Moshi APT discovery warning
hilt {
    enableAggregatingTask = true
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.livedata)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.core)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.android)

    // Chart
    implementation(libs.mpandroidchart)

    // Test
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
}
