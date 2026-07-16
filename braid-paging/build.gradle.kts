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
        moduleName.set("braid-paging")
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
        includes.from(rootProject.file("docs/dokka/braid-paging.md"))

        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/k1eeN/braid-android/tree/main/braid-paging/src/main/java")
            remoteLineSuffix.set("#L")
        }

        externalDocumentationLinks.register("kotlinx-coroutines") {
            url("https://kotlinlang.org/api/kotlinx.coroutines/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.coroutines/package-list")
        }
    }
}

android {
    namespace = "io.github.k1een.braid.paging"

    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
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
    api(project(":braid"))
    api(libs.androidx.paging.runtime)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
