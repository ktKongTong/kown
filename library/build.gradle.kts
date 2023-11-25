import java.util.Properties

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("app.cash.sqldelight")
    id("maven-publish")
    id("signing")
}
sqldelight {
    databases {
        create("KownDatabase") {
            packageName.set("io.ktlab.kown.model")
        }
    }
}
kotlin {
    androidTarget {
//        publishLibraryVariants("release")
//        mavenPublication {
//            artifactId = artifactId.replace("library", "kown")
//        }
    }
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvmToolchain(17)
    val ktorVersion = "2.3.6"
    val coroutineVersion = "1.7.3"
    val serializationVersion = "1.6.1"
    val ioVersion = "0.3.0"
    val datetimeVersion = "0.4.1"
    val okioVersion = "3.6.0"
    val sqlDelightVersion = "2.0.0"
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:$ioVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
                implementation("com.squareup.okio:okio:$okioVersion")
                implementation("app.cash.sqldelight:runtime:$sqlDelightVersion")
                implementation("app.cash.sqldelight:coroutines-extensions:$sqlDelightVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:$sqlDelightVersion")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:$sqlDelightVersion")
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
extra.apply {
    val publishPropFile = rootProject.file("publish.properties")
    if (publishPropFile.exists()) {
        Properties().apply {
            load(publishPropFile.inputStream())
        }.forEach { name, value ->
            if (name == "signing.secretKeyRingFile") {
                set(name.toString(), rootProject.file(value.toString()).absolutePath)
            } else {
                set(name.toString(), value)
            }
        }
    } else {
        set("signing.keyId", System.getenv("SIGNING_KEY_ID"))
        set("signing.password", System.getenv("SIGNING_PASSWORD"))
        set("signing.secretKeyRingFile", System.getenv("SIGNING_SECRET_KEY_RING_FILE"))
        set("ossrhUsername", System.getenv("OSSRH_USERNAME"))
        set("ossrhPassword", System.getenv("OSSRH_PASSWORD"))
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}
// https://github.com/gradle/gradle/issues/26091
val signingTasks = tasks.withType<Sign>()
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(signingTasks)
}
publishing {
    if (rootProject.file("publish.properties").exists()) {
        signing {
            sign(publishing.publications)
        }
        repositories {
            maven {
                val releasesRepoUrl =
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl =
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                url =
                    if (version.toString().endsWith("SNAPSHOT")) {
                        uri(snapshotsRepoUrl)
                    } else {
                        uri(releasesRepoUrl)
                    }
                credentials {
                    username = project.ext.get("ossrhUsername").toString()
                    password = project.ext.get("ossrhPassword").toString()
                }
            }
        }
    }

    publications.withType<MavenPublication> {
        artifact(javadocJar)
        pom {
            description.set("A downloader library for Kotlin Multiplatform")
            url.set("https://github.com/ktKongTong/kown")
            groupId = rootProject.extra["kownGroupId"].toString()
            artifactId = artifactId.replace("library", "kown")
            version = rootProject.extra["kownVersion"].toString()
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("kt")
                    name.set("kongtong")
                    email.set("kt@ktlab.io")
                }
            }
            scm {
                url.set("https://github.com/ktKongTong/kown")
                connection.set("scm:git:git://github.com/ktKongTong/kown.git")
                developerConnection.set("scm:git:git://github.com/ktKongTong/kown.git")
            }
        }
    }
}
