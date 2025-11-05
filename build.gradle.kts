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
    // Ktor Network for TLS (includes coroutines)
    implementation("io.ktor:ktor-network-tls:3.3.1")
    // Bouncy Castle for self-signed certificate generation
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
