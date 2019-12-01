import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val projectVersion = "1.0"

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.41"

    //https://github.com/jponge/vertx-gradle-plugin
    id("io.vertx.vertx-plugin") version "1.0.1"

    // Apply the application plugin to add support for building a CLI application.
    application
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
    mavenCentral()
}

dependencies {
    //kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    //vert.x
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-templ-pebble")
    implementation("io.vertx:vertx-lang-kotlin")
    //log
    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
}

vertx { // (1)
    vertxVersion = "3.8.4"
    mainVerticle = "moose.MainVerticle"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks{
    shadowJar{
        archiveVersion.set(projectVersion)
    }
}

