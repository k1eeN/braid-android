import com.diffplug.spotless.LineEnding
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.Delete

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless)
}

dokka {
    dokkaPublications.html {
        moduleName.set("Braid")
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        failOnWarning.set(true)
        suppressObviousFunctions.set(true)
        suppressInheritedMembers.set(false)
    }
}

dependencies {
    dokka(project(":braid"))
    dokka(project(":braid-paging"))
}

spotless {
    lineEndings = LineEnding.UNIX

    kotlin {
        target("**/*.kt")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.idea/**",
            "**/generated/**",
            "**/.consumer*/**"
        )

        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            "**/generated/**",
            "**/.consumer*/**"
        )

        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        extensions.configure<DetektExtension> {
            toolVersion = libs.versions.detekt.get()
            source.setFrom(
                project.files(
                    "src/main/java",
                    "src/main/kotlin",
                    "src/test/java",
                    "src/test/kotlin",
                    "src/androidTest/java",
                    "src/androidTest/kotlin"
                )
            )
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
            ignoreFailures = false
            basePath = rootProject.projectDir.absolutePath
        }

        tasks.withType<Detekt>().configureEach {
            exclude(
                "**/build/**",
                "**/generated/**",
                "**/.consumer*/**"
            )

            reports {
                html.required.set(true)
                xml.required.set(true)
                sarif.required.set(true)
                txt.required.set(true)
                md.required.set(false)
            }
        }
    }
}

apiValidation {
    ignoredProjects.add("sample")
}

val publicationVersion =
    providers.gradleProperty("VERSION_NAME")
        .orElse("0.1.0-SNAPSHOT")
val testMavenRepository = layout.buildDirectory.dir("test-maven-repository")

val cleanTestMavenRepository = tasks.register<Delete>("cleanTestMavenRepository") {
    delete(testMavenRepository)
    delete(layout.buildDirectory.dir("publishing-consumer"))
}

subprojects {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        tasks.matching { it.name == "publishMavenPublicationToTestRepository" }.configureEach {
            dependsOn(cleanTestMavenRepository)
        }
    }
}

val verifyMavenPublications =
    tasks.register<VerifyMavenPublicationsTask>("verifyMavenPublications") {
        dependsOn(
            ":braid:publishMavenPublicationToTestRepository",
            ":braid-paging:publishMavenPublicationToTestRepository"
        )
        repositoryDirectory.set(testMavenRepository)
        versionName.set(publicationVersion)
        recyclerViewVersion.set(libs.versions.recyclerview)
        pagingVersion.set(libs.versions.paging)
    }

val publishingCheck = tasks.register("publishingCheck") {
    group = "verification"
    description = "Builds and validates local Maven publications without uploading them."
    dependsOn(verifyMavenPublications)
}

tasks.register("qualityCheck") {
    group = "verification"
    description =
        "Runs formatting, API, documentation, publishing, static analysis, and Android lint checks."
    dependsOn(
        "spotlessCheck",
        publishingCheck,
        ":dokkaGenerate",
        ":braid:apiCheck",
        ":braid-paging:apiCheck",
        ":braid:detekt",
        ":braid-paging:detekt",
        ":sample:detekt",
        ":braid:lintDebug",
        ":braid-paging:lintDebug",
        ":sample:lintDebug"
    )
}
