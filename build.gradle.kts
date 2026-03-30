import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

group = "com.nearbybirds"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // PostGIS / Hibernate Spatial
    implementation("org.hibernate.orm:hibernate-spatial:6.4.4.Final")

    // PostgreSQL driver
    runtimeOnly("org.postgresql:postgresql")

    // Micrometer (Prometheus metrics)
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Structured logging (Logstash encoder for JSON logs)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Flyway for DB migrations (database module required for PostgreSQL 15+ in Flyway 10+)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Align host runs with Docker Compose: same password as secrets/postgres_password when the env var is unset.
// Applies to bootRun and bootTestRun (both use BootRun).
tasks.withType<BootRun>().configureEach {
    notCompatibleWithConfigurationCache(
        "BootRun tasks read optional secrets/postgres_password when SPRING_DATASOURCE_PASSWORD is unset",
    )
    doFirst {
        if (System.getenv("SPRING_DATASOURCE_PASSWORD").isNullOrBlank()) {
            val secretFile = rootProject.layout.projectDirectory.file("secrets/postgres_password").asFile
            if (secretFile.isFile) {
                environment(
                    "SPRING_DATASOURCE_PASSWORD",
                    secretFile.readText().filter { it != '\n' && it != '\r' },
                )
            }
        }
    }
}
