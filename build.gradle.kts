plugins {
    idea
    `kotlin-dsl`
    `groovy`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.palantir.git-version") version "3.1.0"
    `maven-publish`
    `java-gradle-plugin`
}

val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
group = "com.jetbrains.python"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

tasks.withType<Test>() {
    maxParallelForks = 8
}

publishing {
    repositories {
        maven {
            url = uri("../mvn_repo")
        }
    }
}

dependencies {
    implementation(gradleApi())

    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-3.0") {
        exclude(module = "groovy-all")
    }
}

gradlePlugin {
    website.set("https://github.com/JetBrains/gradle-python-envs")
    vcsUrl.set("https://github.com/JetBrains/gradle-python-envs")
    plugins {
        create("pythonEnvsPlugin") {
            id = "com.jetbrains.python.envs"
            implementationClass = "com.jetbrains.python.envs.PythonEnvsPlugin"
            displayName = "Gradle Python Envs plugin"
            description = "Gradle plugin to install different Python environments"
            tags.set(listOf("python", "miniconda", "conda"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true

        events("started", "passed", "skipped", "failed", "standard_out", "standard_error")
    }
}
