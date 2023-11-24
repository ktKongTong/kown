plugins {
    kotlin("multiplatform") version "1.9.0" apply false
    kotlin("android") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.9.21" apply false

    id("org.jetbrains.compose") version "1.5.2" apply false
    id("com.android.library") version "8.1.1" apply false
    id("app.cash.sqldelight") version "2.0.0" apply false
}



allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}