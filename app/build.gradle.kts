plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project

android {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()

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
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"unknown\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v1\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"addons.florisboard.org\"")
        
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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Trazendo as bibliotecas internas do projeto
    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.snygg) 
    implementation(projects.lib.compose)

    // Dependências externas essenciais
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.emoji2)
    implementation(libs.cache4k)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)
    implementation(libs.patrickgold.jetpref.datastore.model)
    
    // Testes (opcional, mas evita erros se o projeto pedir)
    testImplementation(libs.kotlin.test.junit5)
}
