@file:Suppress("DEPRECATION", "UnstableApiUsage", "DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

fun getConfigString(name: String): String = extra[name] as String
fun getConfigInt(name: String): Int = getConfigString(name).toInt()

group = getConfigString("package.name.android.group")
version = getConfigString("android.version.name")

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()

    androidTarget {
        publishLibraryVariants = listOf("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = getConfigString("package.name.ios")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.runtime)
                implementation(libs.kermit)
                implementation(libs.kotlinxCoroutinesCore)
            }
        }
    }
}

android {
    val packageName = getConfigString("package.name.android.group") +
        getConfigString("package.name.android.artifact")
    namespace = packageName
    compileSdk = getConfigInt("android.sdk.compile")

    defaultConfig {
        minSdk = getConfigInt("android.sdk.min")
        targetSdk = getConfigInt("android.sdk.target")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

publishing {
    publications {
        create<MavenPublication>("maven"){
            groupId = getConfigString("package.name.android.group")
            artifactId = getConfigString("package.name.android.artifact")
            version = getConfigString("android.version.name")
        }
    }
}
