import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.hilt)
}

android {
    namespace = libs.versions.appPackageName.get()
    compileSdk = 37

    defaultConfig {
        applicationId = libs.versions.appPackageName.get()
        // after 2020
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "37.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

ksp {
    arg("ksp.incremental", "false")
    arg("ksp.verbose", "true")  // 开启详细日志
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)
    implementation(libs.hilt.android)
    debugImplementation(libs.androidx.ui.tooling)
    ksp(libs.hilt.compiler) // for hilt kernel compile
    ksp(libs.androidx.hilt.compiler) // for Hilt-worker annotation compile
    ksp(libs.kotlin.metadata.jvm) // explicitly assign to overwrite the default
    implementation(libs.accompanist.permissions)
    implementation(libs.timber)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

sqldelight {
    databases {
        create("SqlDelightDb") {
            packageName.set("${libs.versions.appPackageName.get()}.sqldelight")
        }
    }
}