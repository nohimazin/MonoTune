import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

android {
    namespace = "com.kyant.taglib"
    compileSdk = 35
    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += arrayOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=none")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        checkReleaseBuilds = false
    }
}


dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
