plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.devtools.ksp") version "2.3.12"
    jacoco
}

group = "dev.erickvieira"
version = "0.0.1-SNAPSHOT"
description = "flash-booking"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4 no longer manages Testcontainers module versions, so import their BOM.
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

// The OpenAPI Gradle plugin stopped at 7.14.0, which predates `useSpringBoot4`/`useJackson3`.
// We therefore drive the 7.25.0 CLI (same codegen) through a plain JavaExec task.
val openApiGenerator by configurations.creating
val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

// The `info.solidsoft.pitest` plugin is incompatible with Gradle 9, so mutation testing runs the
// PIT command line directly through a JavaExec task (`mutationTest`).
val pitestTool by configurations.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Spring Boot 4 split auto-configuration into per-technology modules.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.mcarle:konvert-annotations:4.5.1")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("org.postgresql:postgresql")

    ksp("io.mcarle:konvert:4.5.1")
    kspTest("io.mcarle:konvert:4.5.1")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    openApiGenerator("org.openapitools:openapi-generator-cli:7.25.0")

    pitestTool("org.pitest:pitest-command-line:1.20.2")
    pitestTool("org.pitest:pitest-junit5-plugin:1.2.3")
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
        )
    }
}

// `integrationTest` is a second source set that needs live infrastructure (Testcontainers),
// so it stays out of `test`/`check` and only runs through `verify`.
sourceSets {
    main {
        kotlin.srcDir(generatedOpenApiDir.map { it.dir("src/main/kotlin") })
        // The springdoc provider emits a configuration bean outside our package, which is never
        // component-scanned; drop it instead of carrying dead code.
        kotlin.exclude("org/openapitools/**")
    }
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:postgresql")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
}

val openapiGenerate =
    tasks.register<JavaExec>("openapiGenerate") {
        group = "openapi"
        description = "Generates API interfaces and models from the OpenAPI specification."
        classpath = openApiGenerator
        mainClass.set("org.openapitools.codegen.OpenAPIGenerator")

        val spec = layout.projectDirectory.file("src/main/resources/openapi/api.yaml")
        val output = generatedOpenApiDir.get().asFile
        inputs.file(spec)
        outputs.dir(output)

        args =
            listOf(
                "generate",
                "-i", spec.asFile.absolutePath,
                "-g", "kotlin-spring",
                "-o", output.absolutePath,
                "--library", "spring-boot",
                "--additional-properties",
                listOf(
                    "interfaceOnly=true",
                    "useSpringBoot4=true",
                    "useJackson3=true",
                    "useBeanValidation=true",
                    "useTags=true",
                    "requestMappingMode=api_interface",
                    "documentationProvider=springdoc",
                    "apiPackage=dev.erickvieira.flashbooking.adapter.input.web.api",
                    "modelPackage=dev.erickvieira.flashbooking.adapter.input.web.model",
                ).joinToString(","),
                "--global-property", "apiDocs=false,modelDocs=false,apiTests=false,modelTests=false",
            )
    }

tasks.named("compileKotlin") {
    dependsOn(openapiGenerate)
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn(openapiGenerate)
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests against live infrastructure (Testcontainers)."
        group = "verification"
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
    }

jacoco {
    toolVersion = "0.8.13"
}

val jacocoCoverageExclusions =
    listOf(
        // Framework bootstrap, never exercised by tests.
        "dev/erickvieira/flashbooking/FlashBookingApplication*",
        // Code generated from the OpenAPI contract (reproduced from the spec, not hand-written).
        "dev/erickvieira/flashbooking/adapter/input/web/api/**",
        "dev/erickvieira/flashbooking/adapter/input/web/model/**",
    )

val coverageMinimum = 0.90

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(jacocoCoverageExclusions) } },
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(jacocoCoverageExclusions) } },
    )
    violationRules {
        rule {
            limit {
                minimum = coverageMinimum.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Mutation testing focuses on the pure logic (no framework/adapters) to stay fast and stable.
// The pipeline is separate from `check` and only joined through `verify`.
val mutationTest =
    tasks.register<JavaExec>("mutationTest") {
        group = "verification"
        description = "Runs PIT mutation testing over the production code (excludes OpenAPI-generated)."
        dependsOn(tasks.classes, tasks.testClasses)

        inputs.dir(layout.projectDirectory.dir("src/main/kotlin"))
        inputs.dir(layout.projectDirectory.dir("src/test/kotlin"))
        inputs.files(sourceSets.main.get().output, sourceSets.test.get().output)

        val projectClasspath =
            files(sourceSets.main.get().runtimeClasspath, sourceSets.test.get().runtimeClasspath)
        classpath = pitestTool + projectClasspath
        mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")

        val reportDir = layout.buildDirectory.dir("reports/pitest")
        outputs.dir(reportDir)

        args =
            listOf(
                "--reportDir", reportDir.get().asFile.absolutePath,
                "--targetClasses", "dev.erickvieira.flashbooking.*",
                "--targetTests", "dev.erickvieira.flashbooking.*",
                "--excludedClasses",
                "*Test*,dev.erickvieira.flashbooking.fixtures.*,dev.erickvieira.flashbooking.adapter.input.web.api.*,dev.erickvieira.flashbooking.adapter.input.web.model.*,org.openapitools.*,dev.erickvieira.flashbooking.FlashBookingApplication*",
                "--sourceDirs", "src/main/kotlin",
                "--excludedMethods", "getValue",
                "--classPath", projectClasspath.asPath,
                "--outputFormats", "HTML,XML",
                "--timestampedReports", "false",
                "--threads", "4",
                "--mutationThreshold", "90",
                "--failWhenNoMutations", "false",
                "--verbose", "false",
            )
    }

val verify =
    tasks.register("verify") {
        group = "verification"
        description = "Runs unit tests, coverage gate, integration tests and mutation testing (the full gate)."
        dependsOn(tasks.check, integrationTest, mutationTest)
    }
