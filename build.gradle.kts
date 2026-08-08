import java.util.Properties

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.firebase.crashlytics).apply(false)
    alias(libs.plugins.jetbrains.compose.compiler).apply(false)
    alias(libs.plugins.jetbrains.serialization).apply(false)
    alias(libs.plugins.google.services).apply(false)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

allprojects {
    extra["localProperties"] = localProperties
}
