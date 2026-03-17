import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    val springBootVersion = "3.3.11"
    val kotlinVersion = "1.9.25"

    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version "1.1.7"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion

    id("com.adarshr.test-logger") version "4.0.0"
}

group = "io.bimurto"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

extra["springCloudVersion"] = "2023.0.5"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    val springCloudAwsVersion = "3.2.1"

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // dependencies for spring boot AWS
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:${springCloudAwsVersion}"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-2.2:2.19.0-alpha")
    implementation("software.amazon.awssdk:bedrockruntime:2.21.0")

    implementation("org.flywaydb:flyway-core:8.5.13")
    runtimeOnly("org.postgresql:postgresql")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("org.crac:crac")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.19.0")
    }
}

val localAwsEnvironment = mapOf(
    "AWS_REGION" to "eu-west-1",
    "AWS_ACCESS_KEY_ID" to "test",
    "AWS_SECRET_ACCESS_KEY" to "test"
)

tasks.bootRun{
    environment(localAwsEnvironment)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

springBoot {
    mainClass.set("io.bimurto.crac.Application")
}

tasks.getByName<BootJar>("bootJar") {
    archiveFileName.set("demo.jar")
}