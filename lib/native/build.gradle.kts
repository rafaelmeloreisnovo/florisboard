import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.kotlin.android)
}

val projectMinSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.set(listOf(
            "-Xjvm-default=all"
        ))
    }
}

android {
    namespace = "org.florisboard.libnative"
    compileSdk = projectCompileSdk.toInt()
    ndkVersion = tools.versions.ndk.get()

    defaultConfig {
        minSdk = projectMinSdk.toInt()

        externalNativeBuild {
            cmake {
                targets("fl_native")
                arguments(
                    "-DCMAKE_ANDROID_API=$minSdk",
                    // Add optimization flags for better performance
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-$minSdk",
                )
                // Enable compiler flags for better debugging and optimization
                cFlags("-Wall", "-Wextra", "-std=c11")
                cppFlags("-Wall", "-Wextra", "-std=c++17")
            }
        }

        ndk {
            // Enable ABI filters for consistent ARM64/ARM32 builds
            // Matches the main app configuration for proper library inclusion
            // Note: All ABIs must be included to prevent UnsatisfiedLinkError on different devices
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("consumer-rules.pro")
        }
        create("beta") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("consumer-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        maybeCreate("main").apply {
            java {
                srcDirs("src/main/kotlin")
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = tools.versions.cmake.get()
            path("src/main/rust/CMakeLists.txt")
        }
    }
}

tasks.named("clean") {
    doLast {
        delete("src/main/rust/target")
    }
}

dependencies {
    // none
}
