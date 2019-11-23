/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin application project to get you started.
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.41"
    //https://imperceptiblethoughts.com/shadow/introduction/
    id("com.github.johnrengelman.shadow") version "5.2.0"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    // Define the main class for the application.
    mainClassName = "reindeer.AppKt"
}

val rel = "1.0"

val shadowJar: ShadowJar by tasks

// also see: https://github.com/csolem/gradle-shadow-jar-with-kotlin-dsl/blob/master/build.gradle.kts
shadowJar.apply {
    archiveBaseName.set("reindeer")
    archiveVersion.set(rel)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}