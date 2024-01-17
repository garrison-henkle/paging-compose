@file:Suppress("UnstableApiUsage")

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
}

fun getConfigString(name: String): String = extra[name] as String
fun getConfigInt(name: String): Int = getConfigString(name).toInt()

android {
    namespace = getConfigString("package.name.android.example")
    compileSdk = getConfigInt("android.sdk.compile")

    defaultConfig {
        applicationId = "dev.henkle.compose.paging.android"
        minSdk = getConfigInt("android.sdk.min")
        targetSdk = getConfigInt("android.sdk.target")
        versionCode = getConfigInt("android.version.code")
        versionName = getConfigString("android.version.name")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":paging"))
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.runtime)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntime)
    implementation(libs.kermit)
    implementation(libs.kotlinxCoroutinesCore)
}