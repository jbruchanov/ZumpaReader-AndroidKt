plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.jetbrains.serialization)
}

/**
 * The platform-independent half of the app - parsing, the http layer, the model, the repositories.
 *
 * **The `jvm()` target is not there to ship anything.** Nothing consumes it yet. It is a compile-time
 * guard: `commonMain` has no Android on its classpath under that target, so an `android.*` import
 * that sneaks in fails the build instead of quietly making the module un-portable. When the ios
 * targets are added, the work will already be done.
 */
kotlin {
    android {
        namespace = "com.scurab.android.zumpareader.shared"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()

        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.io.core)
            api(libs.ksoup)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        /**
         * Tests that need something the JVM has and common does not: `mockk`, and loading the
         * captured html fixtures off the classpath. They exercise shared code either way - the
         * split is about the test tooling, not about what is under test.
         */
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
            implementation(libs.mockk)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            api(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            api(libs.ktor.client.okhttp)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
