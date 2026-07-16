import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

val publicationVersion = providers.gradleProperty("VERSION_NAME").get()

val dokkaHtmlJavadocJar = tasks.register<Jar>("dokkaHtmlJavadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false
        )
    )
    coordinates(
        groupId = "io.github.k1een",
        artifactId = "braid-paging",
        version = publicationVersion
    )

    pom {
        name.set("Braid Paging")
        description.set("Paging 3 integration for Braid delegates built on AndroidX PagingDataAdapter.")
        url.set("https://github.com/k1eeN/braid-android")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("k1eeN")
                name.set("Mark Klinitskiy")
                url.set("https://github.com/k1eeN")
            }
        }

        scm {
            url.set("https://github.com/k1eeN/braid-android")
            connection.set("scm:git:https://github.com/k1eeN/braid-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/k1eeN/braid-android.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/k1eeN/braid-android/issues")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(dokkaHtmlJavadocJar)
    }

    repositories {
        maven {
            name = "Test"
            url = rootProject.layout.buildDirectory.dir("test-maven-repository").get().asFile.toURI()
        }
    }
}
