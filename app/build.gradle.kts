plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.apkm.addon.fdroid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.apkm.addon.fdroid"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.bcpg.jdk18on)
    implementation(libs.bcprov.jdk18on)
    testImplementation(libs.junit)
    implementation(libs.jsoup)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}