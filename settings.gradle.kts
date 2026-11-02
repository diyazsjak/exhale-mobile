pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central first: avoids Central's rate limits on CI/cloud.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's mirror of Maven Central first: avoids Central's rate limits on CI/cloud.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "exhale-mobile"
include(":app")
