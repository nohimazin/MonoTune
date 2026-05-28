import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val ffMetadataCmakeFile = file("src/main/cpp/CMakeLists.txt")
val hasFfMetadataCmake = ffMetadataCmakeFile.exists()

kotlin {
	jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
	namespace = "wah.mikooomich.ffMetadataEx"
	compileSdk = 35

	defaultConfig {
		minSdk = 24

		if (hasFfMetadataCmake) {
			externalNativeBuild {
				cmake {
					arguments += listOf("-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=none")
				}
			}
		}
	}

	sourceSets {
		getByName("main") {
			jniLibs.srcDirs("ffmpeg-android-maker/output/lib/")
		}
	}

	if (hasFfMetadataCmake) {
		externalNativeBuild {
			cmake {
				path = ffMetadataCmakeFile
				version = "3.31.6"
			}
		}
	}

	ndkVersion = "29.0.13113456"

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

dependencies {
	implementation(libs.annotation)
	implementation(libs.media3)
}
