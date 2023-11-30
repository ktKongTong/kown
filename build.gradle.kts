plugins {
    kotlin("multiplatform") version "1.9.21" apply false
    kotlin("android") version "1.9.20" apply false
    kotlin("plugin.serialization") version "1.9.20" apply false

    id("org.jetbrains.compose") version "1.5.11" apply false
    id("com.android.library") version "8.1.4" apply false
    id("app.cash.sqldelight") version "2.0.0" apply false
    id("com.diffplug.spotless") version "6.23.2"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            allWarningsAsErrors = true
            freeCompilerArgs =
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                )
        }
    }
    apply(plugin = "com.diffplug.spotless")
    spotless {
        kotlin {
            target("library/**/*.kt")
            targetExclude("library/build/**/*.kt", "bin/**/*.kt", "buildSrc/**/*.kt")
            ktlint("1.0.1")
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.0.1")
        }
        java {
            target("library/**/*.java")
            targetExclude("library/build/**/*.java", "bin/**/*.java")
        }
    }
}

extra.apply {
    set("kownVersion", "0.0.1-alpha01")
    set("kownGroupId", "io.ktlab.mvn")
}
