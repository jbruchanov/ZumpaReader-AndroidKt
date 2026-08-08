pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    //no toml here yet
    id("de.fayard.refreshVersions") version "0.60.6"
}

refreshVersions {
    //workaround to avoid having empty versions.properties generated in root
    file("build/").mkdirs()
    versionsPropertiesFile = file("build/versions.properties")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

include(":appAndroid")
include(":appJvm")
include(":shared")
