import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.jetbrains.compose.compiler)
}

/**
 * The desktop app.
 *
 * It exists to run `:shared` somewhere that is not Android, which until now only the `jvm()` test
 * target proved at compile time. Everything below the UI - the http layer, the parser, the model,
 * the repositories - is the same code the Android app runs.
 *
 * The UI is **not** shared: `:appAndroid` is on `androidx.compose` and this is on Compose
 * Multiplatform, so the two screens are separate. Merging them is phase 3 in `KMP_PLAN.md`; this
 * module is where that work will land.
 */
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmtarget.get())
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmtarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmtarget.get())
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.stdlib)
}

/**
 * Loads the forum over the shared stack and prints what came back, without opening a window.
 * A quick way to check that `:shared` still works off Android - `./gradlew :appJvm:smoke`.
 */
tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Loads the main page through :shared and prints the result"
    mainClass = "com.scurab.zumpareader.desktop.SmokeKt"
    classpath = sourceSets["main"].runtimeClasspath
}

compose.desktop {
    application {
        mainClass = "com.scurab.zumpareader.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ZumpaReader"
            packageVersion = "1.0.0"
        }
    }
}
