import com.diffplug.spotless.LineEnding
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.spotless)
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

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs formatting, API, static analysis, and Android lint checks."
    dependsOn(
        "spotlessCheck",
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
