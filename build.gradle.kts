plugins {
    id("java")
    id("maven-publish")
    id("io.deepmedia.tools.deployer") version "0.16.0"
    `java-library`
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.kryptonmc.org/releases")
    maven("https://repo.spongepowered.org/maven/")
}

java {
    withJavadocJar()
    withSourcesJar()
}

deployer {
    signing {
        key.set(secret("winterSigningKey"))
        password.set(secret("winterSigningPassphrase"))
    }

    // 1. Artifact definition.
    // https://opensource.deepmedia.io/deployer/artifacts
    content {
        component {
            fromJava() // shorthand for fromSoftwareComponent("java")
        }
    }

    // 2. Project details.
    // https://opensource.deepmedia.io/deployer/configuration
    projectInfo {
        description.set("The framework to make plugin creation easier than ever.")
        url.set("https://github.com/thewinterframework/configuration")
        license(MIT)

        groupId.set(group.toString())
        scm.fromGithub("thewinterframework", "configuration")
        developer("Diego Cardenas", "diego.cardenas.v06@gmail.com")
        artifactId.set(project.name)
    }

    // 3. Central Portal configuration.
    // https://opensource.deepmedia.io/deployer/repos/central-portal
    centralPortalSpec {
        signing.key.set(secret("winterSigningKey"))
        signing.password.set(secret("winterSigningPassphrase"))
        auth.user.set(secret("winterRepositoryUsername"))
        auth.password.set(secret("winterRepositoryPassword"))
    }
}

dependencies {
    // Core
    compileOnlyApi("com.thewinterframework:core:1.0.5")
    annotationProcessor("com.thewinterframework:core:1.0.5")

    // Configurate
    compileOnlyApi("org.spongepowered:configurate-yaml:4.2.0")
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