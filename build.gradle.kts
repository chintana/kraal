/*
 * Copyright 2018-2019 Hewlett Packard Enterprise Development LP
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import com.jfrog.bintray.gradle.BintrayExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date

plugins {
    kotlin("jvm") version "1.3.11"
    id("org.jetbrains.dokka") version "0.9.17"
    id("com.jfrog.bintray") version "1.8.4"
    id("io.gitlab.arturbosch.detekt") version "1.0.0-RC12"
    `maven-publish`
}

description = "Kraal parent module - Kraal enables the use of Kotlin coroutines and GraalVM native-image together"

allprojects {
    group = "com.hpe.kraal"
    version = "0.0.9"

    repositories {
        jcenter()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf("-progressive")
            // disable -Werror with: ./gradlew -PwarningsAsErrors=false
            allWarningsAsErrors = project.findProperty("warningsAsErrors") != "false"
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(
                TestLogEvent.SKIPPED,
                TestLogEvent.FAILED
            )
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}

val notMavenPublished = setOf("gradle-plugin")

configure(subprojects.filter { it.name !in notMavenPublished }) {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "com.jfrog.bintray")


    tasks.withType(DokkaTask::class.java) {
        // lots of Can't find node warnings, possibly: https://github.com/Kotlin/dokka/issues/319
        reportUndocumented = false
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
    }

    val sourcesJar by tasks.creating(Jar::class) {
        dependsOn("classes")
        classifier = "sources"
        from(sourceSets["main"].allSource)
    }

    val javadocJar by tasks.creating(Jar::class) {
        dependsOn("dokka")
        classifier = "javadoc"
        from(buildDir.resolve("javadoc"))
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }

    publishing {
        publications.create<MavenPublication>("KraalPublication") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            groupId = "${this@configure.group}"
            artifactId = this@configure.name
            version = "${this@configure.version}"

            pom {
                name.set(this@configure.name)
                url.set("https://github.com/HewlettPackard/kraal")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("bradnewman")
                        name.set("Brad Newman")
                        organization.set("Hewlett Packard Enterprise")
                        organizationUrl.set("https://hpe.com")
                    }
                }
                scm {
                    url.set("https://github.com/HewlettPackard/kraal")
                }
            }
            // doesn't work in above block for some reason:
            // description.set(this@subprojects.description)
            pom.withXml {
                asNode().apply {
                    appendNode("description", this@configure.description)
                }
            }
        }
    }

    bintray.setPublications("KraalPublication")
    tasks.named("bintrayUpload").configure {
        dependsOn("assemble", "generatePomFileForKraalPublicationPublication")
    }
}

configure(allprojects.filter { it.name !in notMavenPublished }) {
    bintray {
        user = "${properties["bintray.publish.user"]}"
        key = "${properties["bintray.publish.key"]}"

        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = "kraal"
            name = "kraal"
            setLicenses("MIT")
            vcsUrl = "https://github.com/HewlettPackard/kraal"

            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                name = "${project.version}"
                vcsTag = "v$name"
                released = "${Date()}"

                gpg(delegateClosureOf<BintrayExtension.GpgConfig> {
                    sign = true
                })

                mavenCentralSync(delegateClosureOf<BintrayExtension.MavenCentralSyncConfig> {
                    sync = true
                    user = "${properties["sonatype.publish.user"]}"
                    password = "${properties["sonatype.publish.password"]}"
                    close = "1"
                })
            })
        })
    }
}

allprojects {
    dependencies.constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test-common:1.3.11")
        implementation("org.jetbrains.kotlin:kotlin-test-junit5:1.3.11")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.0.1")

        implementation("org.ow2.asm:asm:7.0")
        implementation("org.ow2.asm:asm-analysis:7.0")
        implementation("org.ow2.asm:asm-tree:7.0")
        implementation("org.ow2.asm:asm-util:7.0")

        implementation("org.slf4j:slf4j-api:1.7.25")
        implementation("org.slf4j:slf4j-simple:1.7.25")

        implementation("io.ktor:ktor-server-core:1.0.1")
        implementation("io.ktor:ktor-jackson:1.0.1")
        implementation("io.ktor:ktor-server-netty:1.0.1")
        implementation("io.ktor:ktor-server-jetty:1.0.1")
        implementation("io.ktor:ktor-server-cio:1.0.1")
        implementation("io.ktor:ktor-client-core-jvm:1.0.1")
        implementation("io.ktor:ktor-client-apache:1.0.1")
        implementation("io.ktor:ktor-server-test-host:1.0.1")

        implementation("io.gitlab.arturbosch.detekt:detekt-core:1.0.0-RC12")
        implementation("io.gitlab.arturbosch.detekt:detekt-api:1.0.0-RC12")
        implementation("io.gitlab.arturbosch.detekt:detekt-test:1.0.0-RC12")
        implementation("io.gitlab.arturbosch.detekt:detekt-formatting:1.0.0-RC12")

        implementation("org.quicktheories:quicktheories:0.25")
	}

    dependencies {
        // Maven BOMs create constraints when depended on *outside the constraints section*
        implementation(enforcedPlatform("org.junit:junit-bom:5.3.2"))
    }
}

// run Detekt on all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.0.0-RC12")
    }

    tasks.named<Detekt>("detekt") {
        config = files("$rootDir/detekt.yml")
    }
}

subprojects {
    // dependencies for all projects
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        testImplementation(kotlin("test-junit5"))
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.3.2")
    }
}
