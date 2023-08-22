@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.kotlinAndroid) version libs.versions.kotlin apply false
    alias(libs.plugins.kotlinMultiplatform) version libs.versions.kotlin apply false
    alias(libs.plugins.androidApplication) version libs.versions.androidPlugin apply false
    alias(libs.plugins.androidLibrary) version libs.versions.androidPlugin apply false
    alias(libs.plugins.composeMultiplatform) version libs.versions.compose apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
