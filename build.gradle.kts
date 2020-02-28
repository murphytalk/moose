import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val projectVersion = "1.0"

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.61"

    //https://github.com/jponge/vertx-gradle-plugin
    id("io.vertx.vertx-plugin") version "1.0.1"

    id("com.adarshr.test-logger") version "2.0.0"
    // Apply the application plugin to add support for building a CLI application.
    application
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_13
    targetCompatibility = JavaVersion.VERSION_13
}

dependencies {
    //kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    //to work with fasterxml 2.9x
    //https://github.com/FasterXML/jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.10")
    //vert.x
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-templ-pebble")
    implementation("io.vertx:vertx-config-yaml")
    implementation("io.vertx:vertx-redis-client")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-auth-htpasswd")
    //log
    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("io.vertx:vertx-unit")
    testImplementation("io.vertx:vertx-web-client")
}

vertx { // (1)
    vertxVersion = "3.8.5"
    mainVerticle = "moose.MainVerticle"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "12"
    }
}

tasks{
    testlogger {
        //theme = "standard"
        showExceptions = true
        showStackTraces = true
        showFullStackTraces = false
        showCauses = true
        slowThreshold = 2000
        showSummary = true
        showSimpleNames = false
        showPassed = true
        showSkipped = true
        showFailed = true
        showStandardStreams = true
        showPassedStandardStreams = true
        showSkippedStandardStreams = true
        showFailedStandardStreams = true
    }
    shadowJar{
        archiveVersion.set(projectVersion)
    }
}

