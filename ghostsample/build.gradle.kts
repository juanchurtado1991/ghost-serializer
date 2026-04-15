plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghost.serialization.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ghost.serialization.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-core"))
    implementation(libs.okio)
    implementation(libs.retrofit)
    implementation(libs.okhttp)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    ksp(project(":ghost-compiler"))
}