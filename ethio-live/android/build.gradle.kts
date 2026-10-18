buildscript {
    val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"
    repositories {
        if (!coreOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.1.10")
        if (!coreOnly) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.10")
            classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.1.10-1.0.31")
        }
    }
}
