plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "3.4.0"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.FlightBooking"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain") // Ktor Netty entry point
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21) // Java 21
}

dependencies {
    // Ktor Core & Netty
    implementation("io.ktor:ktor-server-core:3.4.0")
    implementation("io.ktor:ktor-server-netty:3.4.0")
    implementation("io.ktor:ktor-server-auth:3.4.0")
    implementation("io.ktor:ktor-server-html-builder:3.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.1") // Keep only HTML builder

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")

    // BCrypt for password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Sessions
    implementation("io.ktor:ktor-server-sessions:3.4.0")

    // Content Negotiation + Jackson
    implementation("io.ktor:ktor-server-content-negotiation:3.4.0")
    implementation("io.ktor:ktor-serialization-jackson:3.4.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // SQLite + Exposed ORM
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")

    // Pebble Templates
    implementation("io.ktor:ktor-server-pebble:3.4.0")
    implementation("io.pebbletemplates:pebble:3.2.2")
}