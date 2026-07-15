import java.util.Properties
import org.gradle.api.tasks.compile.JavaCompile

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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koretide.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"

        testInstrumentationRunner = "com.koretide.app.HiltTestRunner"

        buildConfigField("String", "KHOA_API_KEY",  "\"${localProps.getProperty("KHOA_API_KEY",  "")}\"")
        buildConfigField("String", "KMA_API_KEY",   "\"${localProps.getProperty("KMA_API_KEY",   "")}\"")
        buildConfigField("String", "BEACH_API_KEY", "\"${localProps.getProperty("BEACH_API_KEY", "")}\"")
        buildConfigField("String", "KASI_API_KEY",  "\"${localProps.getProperty("KASI_API_KEY",  "")}\"")

        manifestPlaceholders["naverClientId"] = localProps.getProperty("NAVER_CLIENT_ID", "j5uq4i1va5")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // AAB에 네이티브 디버그 기호 포함 → Play Console 경고 해소(크래시 스택 심볼화)
            ndk {
                debugSymbolLevel = "FULL"
            }
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

// enableAggregatingTask only affects Kapt-mode Hilt; harmless with KSP.
hilt {
    enableAggregatingTask = true
}

// Hilt still creates hiltJavaCompile* tasks for component aggregation even in KSP mode.
// Those tasks discover moshi-kotlin-codegen's bundled APT processor via the
// META-INF/services/ manifest and invoke it, triggering the Kapt-deprecation warning.
// Stripping the JAR from every JavaCompile AP classpath silences the warning.
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        options.annotationProcessorPath = options.annotationProcessorPath
            ?.filter { "moshi-kotlin-codegen" !in it.name }
    }
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

    // AdMob
    implementation(libs.play.services.ads)

    // Naver Maps
    implementation(libs.naver.map.sdk)

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
