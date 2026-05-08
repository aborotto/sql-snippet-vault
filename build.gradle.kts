plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group   = "com.sqlfolio"
version = "1.2.0"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // BYOB back-end drivers — bundled into the plugin JAR
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    intellijPlatform {
        datagrip(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("com.intellij.database")

        // Verify compatibility against the current and one previous major version
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
        // change-notes are defined in plugin.xml — no duplication needed here
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
