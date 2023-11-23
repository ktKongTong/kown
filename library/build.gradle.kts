@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}
sqldelight {
    databases {
        create("KownDatabase") {
            packageName.set("io.ktlab.kown.model")
        }
    }
}

kotlin{
    androidTarget("android")
    jvm()

    val ktorVersion = "2.3.6"
    val coroutineVersion = "1.7.3"
    val serializationVersion = "1.6.0"
    val ioVersion = "0.3.0"
    val okioVersion = "3.6.0"
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:$ioVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("com.squareup.okio:okio:$okioVersion")
                implementation("app.cash.sqldelight:runtime:2.0.0")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.0")

//              implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutineVersion")
//              testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutineVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:2.0.0")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.0.0")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
    }
}

android {
    namespace = "io.ktlab.kown"
    compileSdk = 34
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}