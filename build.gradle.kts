import kotlin.math.min

plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.6"
    jacoco
    id("me.champeau.jmh") version "0.7.2"
    id("com.github.spotbugs") version "6.2.5"
    pmd
}

group = "com.amannmalik"
version = "0.1.0"

val picocliVersion by extra("4.7.7")
val junitVersion by extra("5.13.3")
val jmhVersion by extra("1.37")
val slf4jVersion by extra("2.0.16")
val jettyVersion by extra("12.0.23")
val jakartaServletVersion by extra("6.1.0")
val bouncyCastleVersion by extra("1.78.1")

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("org.eclipse.parsson:parsson:1.1.7")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:slf4j-nop:$slf4jVersion")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:$jettyVersion")
    implementation("org.eclipse.jetty:jetty-util:$jettyVersion")
    implementation("jakarta.servlet:jakarta.servlet-api:$jakartaServletVersion")
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.cucumber:cucumber-java:7.23.0")
    testImplementation("io.cucumber:datatable:7.13.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.23.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.13.3")
    jmh("org.openjdk.jmh:jmh-core:$jmhVersion")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

application {
    mainClass.set("com.amannmalik.acp.cli.Entrypoint")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        nativeImageCapable = true
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
}

tasks.test {
    failOnNoDiscoveredTests = false
    useJUnitPlatform {
        includeEngines("junit-platform-suite", "cucumber", "junit-jupiter")
    }
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    dependsOn(tasks.jar)
    val agentFile = configurations.jacocoAgent.get().singleFile.absolutePath.replace(".jar", "-runtime.jar")
    systemProperty("jacoco.agent.jar", agentFile)
    systemProperty("jacoco.exec.file", "${layout.buildDirectory.get()}/jacoco/test.exec")
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            imageName.set("acp")
            javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
            val buildThreads = min(4, Runtime.getRuntime().availableProcessors())
            buildArgs.addAll(
                "--no-fallback",
                "--enable-http",
                "--enable-https",
                "-R:MaxHeapSize=2g",
                "--initialize-at-run-time=org.eclipse.jetty.util",
                "-H:IncludeResourceBundles=org.eclipse.parsson.messages",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+AddAllCharsets",
                "-Ob",
                "--gc=serial",
                "--parallelism=${buildThreads}"
            )
        }
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.amannmalik.acp.cli.Entrypoint"
    }
    from({
        configurations.runtimeClasspath.get()
            .filter { it.extension == "jar" }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    executionData(fileTree("${layout.buildDirectory.get()}/jacoco").include("**/*.exec"))
    reports {
        xml.required.set(true)
        csv.required.set(false)
    }
    doFirst {
        mkdir("${layout.buildDirectory.get()}/jacoco")
    }
}

tasks.check {
    dependsOn(tasks.test)
    dependsOn(tasks.jacocoTestReport)
}

jmh {
    warmupIterations.set(1)
    iterations.set(3)
    fork.set(1)
}

tasks.register<JavaExec>("generateManPage") {
    dependsOn(tasks.classes)
    group = "documentation"
    description = "Generate acp(1) man page"
    classpath = configurations.annotationProcessor.get() + sourceSets.main.get().runtimeClasspath
    mainClass.set("picocli.codegen.docgen.manpage.ManPageGenerator")
    args("-d", "${projectDir}/man", "com.amannmalik.acp.cli.Entrypoint")
}

spotbugs {
    ignoreFailures = true
    showStackTraces = false
    showProgress = true
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    reportsDir = layout.buildDirectory.dir("reports/spotbugs")
    excludeFilter = file("config/spotbugs/exclude.xml")
    toolVersion = "4.9.4"
}

pmd {
    isIgnoreFailures = true
    isConsoleOutput = true
    toolVersion = "7.16.0"
    ruleSets = listOf()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    reportsDir = layout.buildDirectory.dir("reports/pmd").get().asFile
}
