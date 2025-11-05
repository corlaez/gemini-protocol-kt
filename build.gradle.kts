plugins {
    kotlin("jvm") version "2.2.20"
    `maven-publish`// for jitpack
}

group = "com.github.corlaez"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }// for jitpack
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Bouncy Castle to make working directly with certificates and private keys easier
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")// Core provider (crypto algos, key gen, encrypt/decrypt)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82")// Certificate handling, PEM and PKCS utilities

    // Test
    testImplementation(kotlin("test"))
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    explicitApi()
    jvmToolchain(21)
}
tasks.withType<Test>().configureEach {
    jvmArgs(
        "-XX:+CrashOnOutOfMemoryError",
    )
}

java {// for jitpack (not mandatory but recommended)
    withSourcesJar()
}
publishing {// for jitpack
    publications {
        create<MavenPublication>("ktor-gemini") {
            // Uses the artifacts defined by the 'java' or 'kotlin-jvm' component
            from(components["kotlin"])
        }
    }
}
