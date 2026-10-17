// Set -PcoreOnly=true to build/test just the pure-Kotlin :core module
// (useful where Google's Maven repository / Android SDK are unavailable).
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        if (providers.gradleProperty("coreOnly").orNull != "true") google()
    }
}

rootProject.name = "LocalBusinessAssistant"
include(":core")
if (!coreOnly) include(":app")
