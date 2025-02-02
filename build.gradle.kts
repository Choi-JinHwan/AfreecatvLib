@file:Suppress("SpellCheckingInspection")

plugins {
    java
    `java-library`
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "2.1.0"
}

group = "com.github.Choi-JinHwan"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.repsy.io/mvn/lone64/paper")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")

    compileOnly("net.kyori:adventure-api:4.13.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.13.0")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    compileOnly("commons-io:commons-io:2.16.1")
    compileOnly("org.jetbrains:annotations:20.1.0")
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveFileName.set("AfreecatvLib.jar")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create("maven-public", MavenPublication::class) {
            groupId = rootProject.group.toString()
            artifactId = "library"
            version = "1.0.0"
            from(components.getByName("java"))
        }
    }
}
