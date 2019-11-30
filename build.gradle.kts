import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    //kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    //vert.x
    implementation("io.vertx:vertx-web:3.8.4")
    implementation("io.vertx:vertx-lang-kotlin:3.8.4")
    //log
    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
}

val mainVerticleName = "moose.MainVerticle"
val projectVersion = "1.0"

application {
    // Define the main class for the application.
    mainClassName = "io.vertx.core.Launcher"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

// also see: https://github.com/csolem/gradle-shadow-jar-with-kotlin-dsl/blob/master/build.gradle.kts
tasks {
    shadowJar{
        archiveVersion.set(projectVersion)
        manifest {
            attributes.apply {
                put("Main-Verticle", mainVerticleName)
            }
        }
        mergeServiceFiles{
            include("META-INF/services/io.vertx.core.spi.VerticleFactory")
        }
    }
    build {
        dependsOn(shadowJar)
    }
}


