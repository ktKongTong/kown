plugins {
//    kotlin("jvm") version "1.9.0" apply false
    kotlin("multiplatform") version "1.9.0" apply false
    kotlin("android") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    id("org.jetbrains.compose") version "1.5.2" apply false

    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("app.cash.sqldelight") version "2.0.0" apply false
//    id("org.jetbrains.kotlin.android") version '1.9.0' apply false
}



allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
//dependencies {
//    val ktorVersion = "2.3.4"
//    val coroutineVersion = "1.7.3"
//    testImplementation(kotlin("test"))
//    implementation("io.ktor:ktor-client-core:$ktorVersion")
//    implementation("io.ktor:ktor-client-cio:$ktorVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutineVersion")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutineVersion")
//
//}

//tasks.test {
//    useJUnitPlatform()
//}

//kotlin {
//    jvmToolchain(17)
//}