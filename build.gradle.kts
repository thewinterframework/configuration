plugins {
    id("java")
    id("maven-publish")
    `java-library`
}

repositories {
    mavenLocal()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.kryptonmc.org/releases")
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    // Core
    api("com.thewinterframework:core:1.0.0")
    annotationProcessor("com.thewinterframework:core:1.0.0")

    // Configurate
    compileOnlyApi("org.spongepowered:configurate-yaml:4.2.0-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}