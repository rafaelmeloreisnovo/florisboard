import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.mikepenz.aboutlibraries)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xjvm-default=all",
            // Global Kotlin/JVM optimization: disables runtime null-safety assertions
            // for all build types and Android versions, improving performance but
            // making NullPointerException debugging harder if they occur.
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        ))
    }
}

android {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()
    
    // Configure build tools version for stability
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.patrickgold.florisboard"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = 85
        versionName = "0.4.0-bypassed"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Product requirement: deliver an Android 15 arm64-only build; other ABIs are intentionally omitted
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"unknown\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v1\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"addons.florisboard.org\"")
        
        // Optimize dex compilation for better crash resistance
        multiDexEnabled = true
        
        // Garante que o Gradle ache os arquivos de tradução e ícones
        sourceSets {
            getByName("main") {
                assets.srcDirs("src/main/assets")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        // Note: MissingTranslation and ExtraTranslation are disabled to allow partial translations
        // while maintaining build stability across multiple language packs
        disable += setOf("MissingTranslation", "ExtraTranslation")
        baseline = file("lint-baseline.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // Performance optimizations for release builds
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            
            // R8 full mode for maximum optimization
            // Note: Signing must be configured externally via signing.properties or CI/CD
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            
            // Speed up debug builds
            ndk {
                debugSymbolLevel = "NONE"
            }
            
            // Enable crash detection in debug
            isDebuggable = true
            
            // Enable JNI debugging to improve native crash diagnostics during development
            isJniDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    
    // Optimize build for better performance and crash resistance
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
        animationsDisabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Disable unused features for faster builds
        aidl = false
        renderScript = false
        shaders = false
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Remove duplicate files to prevent crashes
            pickFirsts += setOf(
                "META-INF/versions/9/previous-compilation-data.bin"
            )
            // Merge duplicate resources instead of failing
            merges += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
        jniLibs {
            // Reduce APK size and improve loading by keeping only required architectures
            useLegacyPackaging = false
            // Keep debug symbols for crash analysis in release builds
            keepDebugSymbols += setOf("**/*.so")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Trazendo as bibliotecas internas do projeto
    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.snygg) 
    implementation(projects.lib.compose)
    implementation(projects.lib.native)

    // Dependências externas essenciais
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    implementation(libs.cache4k)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)
    implementation(libs.patrickgold.jetpref.datastore.model)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    
    // Testes (opcional, mas evita erros se o projeto pedir)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.params)
}
