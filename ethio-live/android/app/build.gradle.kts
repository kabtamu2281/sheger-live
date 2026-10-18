import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Client configuration. Only public identifiers belong here: the Supabase anon key is designed to be public
// (row level security protects data) and the Agora App ID is public; the Agora certificate, payment keys and
// service-role key live only in Supabase secrets. Values come from -P flags, environment variables or
// android/local.properties (never committed).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(name: String): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name) ?: localProps.getProperty(name) ?: ""

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "et.ethiolive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = cfg("APPLICATION_ID").ifEmpty { "et.ethiolive.app" }
        minSdk = 24
        targetSdk = 35
        versionCode = cfg("VERSION_CODE").ifEmpty { "1" }.toInt()
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "AGORA_APP_ID", "\"${cfg("AGORA_APP_ID")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${cfg("FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${cfg("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${cfg("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${cfg("FIREBASE_SENDER_ID")}\"")
        // Google Play requires Play Billing for in-app coin purchases. Chapa (Telebirr, CBE Birr, cards) is only
        // offered in builds distributed outside Play: set PAYMENT_PROVIDERS=google_play,chapa for those.
        buildConfigField("String", "PAYMENT_PROVIDERS", "\"${cfg("PAYMENT_PROVIDERS").ifEmpty { "google_play" }}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    // Legal documents ship inside the app (assets: docs/legal/*.md) so they are readable offline and always match the build.
    sourceSets.getByName("main").assets.srcDir("../../docs/legal")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Backend: Supabase (auth, PostgREST, realtime, edge functions, storage).
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")

    // Real-time audio/video. Only the core RTC module plus beauty/low-light (clear-vision) and AI noise
    // suppression are bundled; the full SDK's screen sharing, virtual background and AV1 codecs would add ~40 MB.
    val agora = "4.5.2"
    implementation("io.agora.rtc:full-rtc-basic:$agora")
    implementation("io.agora.rtc:clear-vision:$agora")
    implementation("io.agora.rtc:ains:$agora")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
