plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

repositories {
    mavenCentral()


    ivy {
        name = "ChunkerIvy"
        url = uri("https://github.com/HiveGamesOSS/Chunker")
        patternLayout {
            artifact("releases/download/[revision]/[artifact]-[revision].jar")
        }
        metadataSources { artifact() }
    }
}

application {
    mainClass = "games.orium.Crocon"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    compileOnly("org.graalvm.sdk:graal-sdk:25.0.1")
    implementation("chunker:chunker-cli:1.13.0")

}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("crocon")
    archiveClassifier.set("")
    archiveVersion.set("1.0")

    exclude("META-INF/native-image/**")
    exclude("org/graalvm/**")
    exclude("com/oracle/svm/**")
}

