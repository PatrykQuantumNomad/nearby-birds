pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.springframework.boot") version "3.5.13"
        id("io.spring.dependency-management") version "1.1.7"
        id("org.jetbrains.kotlin.jvm") version "2.3.20"
        id("org.jetbrains.kotlin.plugin.spring") version "2.3.20"
        id("org.jetbrains.kotlin.plugin.jpa") version "2.3.20"
    }
}

rootProject.name = "nearby-birds"
