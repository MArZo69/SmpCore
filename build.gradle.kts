plugins {
    java
}

group = "fr.smp"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // VaultAPI
    maven("https://repo.helpch.at/releases/") // PlaceholderAPI
}

dependencies {
    // Ton serveur tourne sur Paper 26.2 (build 87). Le "+" prend le dernier build 26.2.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("SmpCore-${project.version}.jar")
}