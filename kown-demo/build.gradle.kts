plugins {
//    kotlin("jvm")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    jvm()
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(project(":library"))
                val coroutineVersion = "1.7.3"
                implementation(compose.desktop.currentOs)
                api(compose.runtime)
                api(compose.preview)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.materialIconsExtended)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutineVersion")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io/ktlab/demo/MainKt"
    }
}