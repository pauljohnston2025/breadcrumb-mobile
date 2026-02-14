import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.gpx.parser)
            implementation(libs.garmin.ciq.sdk)
            implementation(libs.ktor.client.cio.jvm)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.fragment)
//            implementation(libs.skiko.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.reorderable)

            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.navigation)
            // for webserver
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.resources)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.resources)
            implementation(libs.logback.classic) // Required for logging in Ktor
            implementation(libs.napier)
            implementation(libs.uuid)
            implementation(libs.skiko.core)
            implementation(libs.play.services.location)
//            implementation(libs.icons.extended)
        }
    }
}

fun executeCommand(command: String, workingDir: File = rootProject.projectDir, fallbackValue: String = ""): String {
    return try {
        val parts = command.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        proc.waitFor(5, TimeUnit.SECONDS) // Wait for 5 seconds
        val output = proc.inputStream.bufferedReader().readText().trim()
        if (proc.exitValue() != 0) {
            val error = proc.errorStream.bufferedReader().readText().trim()
            println("Warning: Command '$command' failed with error: $error. Output: $output. Using fallback: '$fallbackValue'")
            fallbackValue
        } else {
            output
        }
    } catch (e: Exception) {
        println("Warning: Could not execute command '$command': ${e.message}. Using fallback: '$fallbackValue'")
        fallbackValue
    }
}

val gitCommitCount = executeCommand("git rev-list --count HEAD", fallbackValue = "1").toIntOrNull() ?: 1
val gitVersionName = executeCommand("git describe --tags --dirty --always", fallbackValue = "0.1.0-SNAPSHOT")

android {
    namespace = "com.paul"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.paul.breadcrumb"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = gitCommitCount
        versionName = gitVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders.put("appIcon", "@mipmap/iconlarge")
            manifestPlaceholders.put("appIconRound", "@mipmap/iconlarge")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders.put("appIcon", "@mipmap/iconlargedebug")
            manifestPlaceholders.put("appIconRound", "@mipmap/iconlargedebug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

