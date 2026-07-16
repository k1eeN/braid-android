import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.android)
}

dokka {
    dokkaPublications.html {
        moduleName.set("braid")
        failOnWarning.set(true)
        suppressObviousFunctions.set(true)
        suppressInheritedMembers.set(false)
    }

    dokkaSourceSets.named("main") {
        documentedVisibilities.set(
            setOf(
                VisibilityModifier.Public,
                VisibilityModifier.Protected
            )
        )
        reportUndocumented.set(true)
        skipEmptyPackages.set(true)
        skipDeprecated.set(false)
        suppressGeneratedFiles.set(true)
        jdkVersion.set(11)
        includes.from(rootProject.file("docs/dokka/braid.md"))

        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/k1eeN/braid-android/tree/main/braid/src/main/java")
            remoteLineSuffix.set("#L")
        }

        externalDocumentationLinks.register("androidx-viewbinding") {
            url(
                "https://cs.android.com/androidx/platform/frameworks/data-binding/+/" +
                    "studio-master-dev:extensions/viewbinding/src/main/java/"
            )
            packageListUrl(
                rootProject.file("docs/dokka/androidx-viewbinding-package-list")
                    .toURI()
                    .toString()
            )
        }
    }
}

android {
    namespace = "io.github.k1een.braid"

    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkTestSources = true
        checkGeneratedSources = false

        textReport = true
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }
}

kotlin {
    explicitApi()

    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.androidx.recyclerview)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
