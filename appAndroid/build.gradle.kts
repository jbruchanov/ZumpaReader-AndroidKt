import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    //agp 9 brings the kotlin android plugin with it, so it is not applied here
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose.compiler)
    alias(libs.plugins.jetbrains.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

@Suppress("UNCHECKED_CAST")
val localProperties = rootProject.extra["localProperties"] as Properties

/** Was `tools.gradle` - two helpers for the build stamp, small enough to keep here. */
fun buildDate(): String = SimpleDateFormat("yyyyMMdd").format(Date())

fun gitSha(): String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
}.getOrDefault("unknown")

android {
    namespace = "com.scurab.android.zumpareader"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    buildFeatures {
        buildConfig = true
        compose = true
    }
    val fileProviderAuthority = "com.scurab.android.zumpareader.fileprovider"
    defaultConfig {
        applicationId = "com.scurab.zumpareader"
        buildConfigField("String", "BUILD_DETAIL", "\"build-${buildDate()},git-${gitSha()}\"")
        buildConfigField("String", "Authority", "\"$fileProviderAuthority\"")
        minSdk = libs.versions.android.sdk.min.get().toInt()
        targetSdk = libs.versions.android.sdk.target.get().toInt()
        versionCode = libs.versions.app.version.code.get().toInt()
        versionName = libs.versions.app.version.name.get()
        manifestPlaceholders["authority"] = fileProviderAuthority
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            keyAlias = localProperties.getProperty("releaseKeyAlias")
            keyPassword = localProperties.getProperty("releaseKeyPassword")
            storePassword = localProperties.getProperty("releaseStorePassword")
            val keystorePath = localProperties.getProperty("releaseKeyStore")
            if (keystorePath != null) {
                // without this check, builds fail for debug if 'releaseKeyStore' is missing
                storeFile = file(keystorePath)
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-${buildDate()}"
            isMinifyEnabled = false
        }
        release {
            //on since the compose migration: R8 takes the apk from 16MB to under 5MB, which is what
            //makes the whole Material icon set affordable - see the note in the version catalog
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmtarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmtarget.get())
    }

}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmtarget.get())
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.firebase)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.android.base)
    implementation(libs.bundles.android.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.ktor)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
