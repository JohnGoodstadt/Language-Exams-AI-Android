// <project-root>/settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/") // This is the official Gradle Plugin Portal repository
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Language Exams AI"
include(":app")