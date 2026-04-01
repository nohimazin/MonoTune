pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.9.0")
}

@file:Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "MonoTune"
include(":app")
include(":innertube")
include(":kugou")
include(":lrclib")
include(":material-color-utilities")
include(":ffMetadataEx")
project(":ffMetadataEx").buildFileName = "../ffMetadataEx-build.gradle.kts"
include(":taglib")
project(":taglib").buildFileName = "../taglib-build.gradle.kts"

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that MonoTune and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")

//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}


// includeBuild(file("media").toPath().toRealPath().toAbsolutePath().toString()) {
//     dependencySubstitution {
//         substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
//         substitute(module("androidx.media3:media3-common-ktx")).using(project(":lib-common-ktx"))
//         substitute(module("androidx.media3:media3-datasource-okhttp")).using(project(":lib-datasource-okhttp"))
//         substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
//         substitute(module("androidx.media3:media3-exoplayer-workmanager")).using(project(":lib-exoplayer-workmanager"))
//         substitute(module("androidx.media3:media3-session")).using(project(":lib-session"))
//     }
// }
