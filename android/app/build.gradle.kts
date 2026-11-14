import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release signing key lives outside the repository; without it a release build stops rather than leave an unsigned APK.
val releaseKey = rootProject.file("../.dev/keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.vehiclecare.app"
    compileSdk = 34
    useLibrary("android.car")

    defaultConfig {
        // Instrumented tests use a separate package so installing/removing their APK cannot erase user records.
        applicationId = if (providers.gradleProperty("careIntegration").orNull == "true") "dev.vehiclecare.verification" else "dev.vehiclecare.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 12
        versionName = "1.2.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    testImplementation("junit:junit:4.13.2")
    // Unit tests run against stubbed android.jar, whose org.json throws; the real library backs JSON round trips.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}

// An unsigned release APK cannot be installed, so a release package without the key fails instead of being produced.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst { check(releaseKey != null) { "Release signing key missing: .dev/keystore.properties" } }
}
