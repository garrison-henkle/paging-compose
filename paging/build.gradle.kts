@file:Suppress("DEPRECATION", "UnstableApiUsage", "DSL_SCOPE_VIOLATION")

import org.jetbrains.kotlin.konan.properties.loadProperties

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
                jvmTarget = "11"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = getConfigString("package.name.ios")
            isStatic = true
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
                implementation(libs.uuid)
            }
        }
    }
}

android {
    val packageName =
        getConfigString("package.name.android.group") +
            getConfigString("package.name.android.artifact")
    namespace = packageName
    compileSdk = getConfigInt("android.sdk.compile")

    defaultConfig {
        minSdk = getConfigInt("android.sdk.min")
        targetSdk = getConfigInt("android.sdk.target")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    val localProperties = loadProperties(rootProject.file("local.properties").absolutePath)
    repositories {
        data class Repo(val name: String, val url: String, val user: String, val password: String)
        val repo1User: String =
            localProperties.getProperty("maven.username")
                ?: throw Exception("missing local.properties field maven.username")
        val repo1Password: String =
            localProperties.getProperty("maven.password")
                ?: throw Exception("missing local.properties field maven.password")
        val repo1Name: String =
            localProperties.getProperty("maven.name")
                ?: throw Exception("missing local.properties field maven.name")
        val repo1Url: String =
            localProperties.getProperty("maven.url")
                ?: throw Exception("missing local.properties field maven.url")
        val repo2User: String =
            localProperties.getProperty("maven2.username")
                ?: throw Exception("missing local.properties field maven2.username")
        val repo2Password: String =
            localProperties.getProperty("maven2.password")
                ?: throw Exception("missing local.properties field maven2.password")
        val repo2Name: String =
            localProperties.getProperty("maven2.name")
                ?: throw Exception("missing local.properties field maven2.name")
        val repo2Url: String =
            localProperties.getProperty("maven2.url")
                ?: throw Exception("missing local.properties field maven2.url")
        val repos =
            listOf(
                Repo(name = repo1Name, url = repo1Url, user = repo1User, password = repo1Password),
                Repo(name = repo2Name, url = repo2Url, user = repo2User, password = repo2Password),
            )

        for (repo in repos) {
            maven {
                name = repo.name
                setUrl(repo.url)
                credentials {
                    username = repo.user
                    password = repo.password
                }
            }
        }
    }
}
