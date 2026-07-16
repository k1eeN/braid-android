import groovy.json.JsonSlurper
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element

@DisableCachingByDefault(because = "The task validates timestamped Maven repository metadata.")
abstract class VerifyMavenPublicationsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val recyclerViewVersion: Property<String>

    @get:Input
    abstract val pagingVersion: Property<String>

    @TaskAction
    fun verify() {
        val repository = repositoryDirectory.get().asFile
        val version = versionName.get()
        requireValidVersion(version)

        val publications =
            listOf(
                ExpectedPublication(
                    artifactId = "braid",
                    displayName = "Braid",
                    description = "A type-safe delegate layer for AndroidX ListAdapter.",
                    requiredSource = "AdapterDelegate.kt",
                    requiredClass = "io/github/k1een/braid/BraidListAdapter.class",
                    requiredDependencies =
                        mapOf(
                            "androidx.recyclerview:recyclerview" to recyclerViewVersion.get()
                        )
                ),
                ExpectedPublication(
                    artifactId = "braid-paging",
                    displayName = "Braid Paging",
                    description =
                        "Paging 3 integration for Braid delegates built on " +
                            "AndroidX PagingDataAdapter.",
                    requiredSource = "BraidPagingDataAdapter.kt",
                    requiredClass =
                        "io/github/k1een/braid/paging/BraidPagingDataAdapter.class",
                    requiredDependencies =
                        mapOf(
                            "io.github.k1een:braid" to version,
                            "androidx.paging:paging-runtime" to pagingVersion.get()
                        )
                )
            )

        verifyRepositoryScope(repository, publications)
        publications.forEach { publication ->
            verifyPublication(repository, version, publication)
        }

        logger.lifecycle(
            "Verified local Maven publications: {}",
            publications.joinToString { "io.github.k1een:${it.artifactId}:$version" }
        )
    }

    private fun verifyRepositoryScope(repository: File, publications: List<ExpectedPublication>) {
        requireThat(repository.isDirectory, "Local Maven repository does not exist: $repository")

        val files = repository.walkTopDown().filter(File::isFile).toList()
        val forbiddenName =
            files.firstOrNull { file ->
                val name = file.name.lowercase()
                name.contains("unspecified") ||
                    name.contains("debug") ||
                    name.endsWith(".apk") ||
                    name.endsWith(".asc")
            }
        requireThat(
            forbiddenName == null,
            "Unexpected local publication file: ${forbiddenName?.relativeTo(repository)}"
        )

        val allowedArtifacts = publications.mapTo(mutableSetOf(), ExpectedPublication::artifactId)
        val groupDirectory = File(repository, "io/github/k1een")
        val artifactDirectories =
            groupDirectory.listFiles()
                ?.filter(File::isDirectory)
                ?.mapTo(mutableSetOf(), File::getName)
                .orEmpty()
        requireThat(
            artifactDirectories == allowedArtifacts,
            "Expected only $allowedArtifacts under io.github.k1een, found $artifactDirectories"
        )
    }

    private fun verifyPublication(repository: File, version: String, expected: ExpectedPublication) {
        val versionDirectory =
            File(repository, "io/github/k1een/${expected.artifactId}/$version")
        requireThat(
            versionDirectory.isDirectory,
            "Missing Maven version directory for ${expected.artifactId}: $versionDirectory"
        )

        val primaryFiles =
            versionDirectory.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        !file.name.startsWith("maven-metadata") &&
                        !CHECKSUM_SUFFIXES.any(file.name::endsWith)
                }
                .orEmpty()

        val aar = primaryFiles.singleFile(expected.artifactId, "release AAR") { it.name.endsWith(".aar") }
        val sources =
            primaryFiles.singleFile(expected.artifactId, "sources JAR") {
                it.name.endsWith("-sources.jar")
            }
        val javadoc =
            primaryFiles.singleFile(expected.artifactId, "javadoc JAR") {
                it.name.endsWith("-javadoc.jar")
            }
        val pom = primaryFiles.singleFile(expected.artifactId, "POM") { it.name.endsWith(".pom") }
        val module =
            primaryFiles.singleFile(expected.artifactId, "Gradle Module Metadata") {
                it.name.endsWith(".module")
            }

        requireThat(
            primaryFiles.size == 5,
            "${expected.artifactId}: expected exactly five primary publication files, " +
                "found ${primaryFiles.map(File::getName)}"
        )

        verifyPom(pom, version, expected)
        verifyModuleMetadata(module, version, expected)
        verifySourcesJar(sources, expected)
        verifyJavadocJar(javadoc, expected)
        verifyAar(aar, expected)
    }

    private fun verifyPom(pom: File, version: String, expected: ExpectedPublication) {
        val factory = secureDocumentBuilderFactory()
        val root = factory.newDocumentBuilder().parse(pom).documentElement

        root.requireText("modelVersion", "4.0.0", expected.artifactId)
        root.requireText("groupId", GROUP_ID, expected.artifactId)
        root.requireText("artifactId", expected.artifactId, expected.artifactId)
        root.requireText("version", version, expected.artifactId)
        root.requireText("packaging", "aar", expected.artifactId)
        root.requireText("name", expected.displayName, expected.artifactId)
        root.requireText("description", expected.description, expected.artifactId)
        root.requireText("url", REPOSITORY_URL, expected.artifactId)

        root.requiredChild("licenses", expected.artifactId)
            .requiredChild("license", expected.artifactId)
            .also { license ->
                license.requireText(
                    "name",
                    "The Apache Software License, Version 2.0",
                    expected.artifactId
                )
                license.requireText(
                    "url",
                    "https://www.apache.org/licenses/LICENSE-2.0.txt",
                    expected.artifactId
                )
                license.requireText("distribution", "repo", expected.artifactId)
            }

        root.requiredChild("developers", expected.artifactId)
            .requiredChild("developer", expected.artifactId)
            .also { developer ->
                developer.requireText("id", "k1eeN", expected.artifactId)
                developer.requireText("name", "Mark Klinitskiy", expected.artifactId)
                developer.requireText("url", "https://github.com/k1eeN", expected.artifactId)
            }

        root.requiredChild("scm", expected.artifactId).also { scm ->
            scm.requireText("url", REPOSITORY_URL, expected.artifactId)
            scm.requireText(
                "connection",
                "scm:git:https://github.com/k1eeN/braid-android.git",
                expected.artifactId
            )
            scm.requireText(
                "developerConnection",
                "scm:git:ssh://git@github.com/k1eeN/braid-android.git",
                expected.artifactId
            )
        }

        root.requiredChild("issueManagement", expected.artifactId).also { issues ->
            issues.requireText("system", "GitHub", expected.artifactId)
            issues.requireText("url", "$REPOSITORY_URL/issues", expected.artifactId)
        }

        val dependencyElements =
            root.requiredChild("dependencies", expected.artifactId)
                .directChildren("dependency")
        val dependencies =
            dependencyElements.associate { dependency ->
                val group = dependency.requiredText("groupId", expected.artifactId)
                val artifact = dependency.requiredText("artifactId", expected.artifactId)
                val dependencyVersion = dependency.requiredText("version", expected.artifactId)
                val scope = dependency.requiredText("scope", expected.artifactId)
                requireThat(
                    dependencyVersion != "unspecified" && dependencyVersion.isNotBlank(),
                    "${expected.artifactId}: invalid dependency version for $group:$artifact"
                )
                requireThat(
                    scope == "compile",
                    "${expected.artifactId}: expected compile scope for $group:$artifact, found $scope"
                )
                "$group:$artifact" to dependencyVersion
            }

        expected.requiredDependencies.forEach { (coordinate, requiredVersion) ->
            requireThat(
                dependencies[coordinate] == requiredVersion,
                "${expected.artifactId}: expected dependency $coordinate:$requiredVersion, " +
                    "found ${dependencies[coordinate]}"
            )
        }
        verifyNoForbiddenDependencies(expected.artifactId, dependencies.keys)

        val rawPom = pom.readText()
        requireThat("project(\"" !in rawPom, "${expected.artifactId}: project dependency leaked into POM")
        requireThat("unspecified" !in rawPom, "${expected.artifactId}: unspecified leaked into POM")
        requireThat(
            !WINDOWS_PATH_REGEX.containsMatchIn(rawPom),
            "${expected.artifactId}: local Windows path leaked into POM"
        )
    }

    private fun verifyModuleMetadata(module: File, version: String, expected: ExpectedPublication) {
        val metadata =
            JsonSlurper().parse(module) as? Map<*, *>
                ?: throw GradleException("${expected.artifactId}: metadata root is not an object")
        requireThat(
            metadata["formatVersion"] == "1.1",
            "${expected.artifactId}: unsupported Gradle Module Metadata format"
        )

        val component = metadata["component"] as? Map<*, *>
            ?: throw GradleException("${expected.artifactId}: missing metadata component")
        requireThat(component["group"] == GROUP_ID, "${expected.artifactId}: wrong metadata group")
        requireThat(
            component["module"] == expected.artifactId,
            "${expected.artifactId}: wrong metadata module"
        )
        requireThat(
            component["version"] == version,
            "${expected.artifactId}: wrong metadata version"
        )

        val variants =
            (metadata["variants"] as? List<*>)
                ?.map { variant ->
                    variant as? Map<*, *>
                        ?: throw GradleException(
                            "${expected.artifactId}: metadata variant is not an object"
                        )
                }
                ?: throw GradleException("${expected.artifactId}: missing metadata variants")
        val usages =
            variants.mapNotNull { variant ->
                val attributes = variant["attributes"] as? Map<*, *>
                attributes?.get("org.gradle.usage") as? String
            }.toSet()
        requireThat(
            setOf("java-api", "java-runtime").all(usages::contains),
            "${expected.artifactId}: release API/runtime variants are missing"
        )

        val dependencies =
            variants.flatMap { variant ->
                (variant["dependencies"] as? List<*>)
                    ?.map { dependency ->
                        dependency as? Map<*, *>
                            ?: throw GradleException(
                                "${expected.artifactId}: metadata dependency is not an object"
                            )
                    }.orEmpty()
            }.associate { dependency ->
                val dependencyVersion = dependency["version"] as? Map<*, *>
                val coordinate = "${dependency["group"]}:${dependency["module"]}"
                coordinate to dependencyVersion?.get("requires")?.toString().orEmpty()
            }
        expected.requiredDependencies.forEach { (coordinate, requiredVersion) ->
            requireThat(
                dependencies[coordinate] == requiredVersion,
                "${expected.artifactId}: metadata dependency $coordinate must use $requiredVersion"
            )
        }
        verifyNoForbiddenDependencies(expected.artifactId, dependencies.keys)

        val rawMetadata = module.readText()
        requireThat(
            "unspecified" !in rawMetadata && "project(\"" !in rawMetadata,
            "${expected.artifactId}: invalid value leaked into Gradle Module Metadata"
        )
    }

    private fun verifySourcesJar(sources: File, expected: ExpectedPublication) {
        val entries = verifiedZipEntries(sources)
        val sourceFiles = entries.filter { it.endsWith(".kt") || it.endsWith(".java") }
        requireThat(sourceFiles.isNotEmpty(), "${expected.artifactId}: sources JAR is empty")
        requireThat(
            sourceFiles.any { it.endsWith("/${expected.requiredSource}") },
            "${expected.artifactId}: sources JAR is missing ${expected.requiredSource}"
        )
        val forbidden =
            sourceFiles.firstOrNull { entry ->
                val normalized = "/${entry.lowercase()}"
                normalized.contains("/test/") ||
                    normalized.contains("/androidtest/") ||
                    normalized.contains("/build/") ||
                    normalized.contains("/sample/") ||
                    normalized.contains("/databinding/") ||
                    normalized.endsWith("binding.java")
            }
        requireThat(
            forbidden == null,
            "${expected.artifactId}: forbidden source entry $forbidden"
        )
    }

    private fun verifyJavadocJar(javadoc: File, expected: ExpectedPublication) {
        val entries = verifiedZipEntries(javadoc)
        requireThat("index.html" in entries, "${expected.artifactId}: javadoc JAR lacks index.html")
        requireThat(
            entries.any { it.startsWith("${expected.artifactId}/") && it.endsWith(".html") },
            "${expected.artifactId}: javadoc JAR lacks module HTML pages"
        )
    }

    private fun verifyAar(aar: File, expected: ExpectedPublication) {
        val entries = verifiedZipEntries(aar)
        requireThat("AndroidManifest.xml" in entries, "${expected.artifactId}: AAR lacks manifest")
        requireThat("classes.jar" in entries, "${expected.artifactId}: AAR lacks classes.jar")
        requireThat(
            "META-INF/com/android/build/gradle/aar-metadata.properties" in entries,
            "${expected.artifactId}: AAR lacks AGP metadata"
        )
        requireThat(
            entries.none { it.contains("sample", ignoreCase = true) || it.contains("androidTest") },
            "${expected.artifactId}: sample or test content leaked into AAR"
        )

        ZipFile(aar).use { zip ->
            val classesEntry = zip.getEntry("classes.jar")
                ?: throw GradleException("${expected.artifactId}: AAR lacks classes.jar")
            val classEntries = mutableSetOf<String>()
            ZipInputStream(ByteArrayInputStream(zip.getInputStream(classesEntry).readBytes())).use { nested ->
                while (true) {
                    val entry = nested.nextEntry ?: break
                    classEntries += entry.name
                    nested.copyTo(NULL_OUTPUT_STREAM)
                }
            }
            requireThat(
                expected.requiredClass in classEntries,
                "${expected.artifactId}: classes.jar is missing ${expected.requiredClass}"
            )
            requireThat(
                classEntries.none { it.contains("/sample/") || it.contains("/test/") },
                "${expected.artifactId}: sample or test class leaked into classes.jar"
            )
        }
    }

    private fun verifiedZipEntries(archive: File): Set<String> {
        requireThat(archive.length() > 0L, "Archive is empty: ${archive.name}")
        ZipFile(archive).use { zip ->
            val entries = mutableSetOf<String>()
            zip.entries().asSequence().forEach { entry ->
                requireThat(entries.add(entry.name), "Duplicate ZIP entry ${entry.name} in ${archive.name}")
                requireThat(
                    !entry.name.startsWith("/") &&
                        !entry.name.contains("../") &&
                        !WINDOWS_PATH_REGEX.containsMatchIn(entry.name),
                    "Unsafe ZIP entry ${entry.name} in ${archive.name}"
                )
                if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { input -> input.copyTo(NULL_OUTPUT_STREAM) }
                }
            }
            requireThat(entries.isNotEmpty(), "Archive has no entries: ${archive.name}")
            return entries
        }
    }

    private fun verifyNoForbiddenDependencies(artifactId: String, dependencies: Set<String>) {
        val forbidden =
            dependencies.firstOrNull { coordinate ->
                val value = coordinate.lowercase()
                value.startsWith("junit:") ||
                    value.startsWith("androidx.test") ||
                    value.contains("espresso") ||
                    value.contains("dokka") ||
                    value.contains("detekt") ||
                    value.contains("spotless") ||
                    value.contains("gradle-plugin") ||
                    value.contains("sample")
            }
        requireThat(forbidden == null, "$artifactId: forbidden dependency $forbidden")
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    }

    private fun Element.requireText(childName: String, expected: String, artifactId: String) {
        val actual = requiredText(childName, artifactId)
        requireThat(
            actual == expected,
            "$artifactId: expected <$childName>$expected</$childName>, found $actual"
        )
    }

    private fun Element.requiredText(childName: String, artifactId: String): String {
        val value = requiredChild(childName, artifactId).textContent.trim()
        requireThat(value.isNotBlank(), "$artifactId: <$childName> must not be blank")
        return value
    }

    private fun Element.requiredChild(childName: String, artifactId: String): Element =
        directChildren(childName).singleOrNull()
            ?: throw GradleException("$artifactId: expected exactly one <$childName> element")

    private fun Element.directChildren(childName: String): List<Element> = (0 until childNodes.length)
        .mapNotNull { index -> childNodes.item(index) as? Element }
        .filter { child -> child.localName == childName || child.nodeName == childName }

    private fun List<File>.singleFile(artifactId: String, label: String, predicate: (File) -> Boolean): File {
        val matches = filter(predicate)
        requireThat(
            matches.size == 1,
            "$artifactId: expected one $label, found ${matches.map(File::getName)}"
        )
        return matches.single()
    }

    private fun requireValidVersion(version: String) {
        requireThat(
            VERSION_REGEX.matches(version),
            "VERSION_NAME must match ${VERSION_REGEX.pattern}, found '$version'"
        )
    }

    private fun requireThat(condition: Boolean, message: String) {
        if (!condition) {
            throw GradleException(message)
        }
    }

    private data class ExpectedPublication(
        val artifactId: String,
        val displayName: String,
        val description: String,
        val requiredSource: String,
        val requiredClass: String,
        val requiredDependencies: Map<String, String>
    )

    private companion object {
        const val GROUP_ID = "io.github.k1een"
        const val REPOSITORY_URL = "https://github.com/k1eeN/braid-android"

        val CHECKSUM_SUFFIXES = listOf(".md5", ".sha1", ".sha256", ".sha512")
        val VERSION_REGEX = Regex("[0-9A-Za-z][0-9A-Za-z._-]*")
        val WINDOWS_PATH_REGEX = Regex("(?:^|[\\s>\"'])[A-Za-z]:[\\\\/]")
        val NULL_OUTPUT_STREAM =
            object : java.io.OutputStream() {
                override fun write(value: Int) = Unit

                override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
            }
    }
}
