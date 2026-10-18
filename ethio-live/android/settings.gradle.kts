rootProject.name = "EthioLive"

// `-PcoreOnly=true` builds just the pure-Kotlin :core module (no Android SDK or Google Maven needed).
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

dependencyResolutionManagement {
    repositories {
        if (!coreOnly) google()
        mavenCentral()
    }
}

include(":core")
if (!coreOnly) include(":app")
