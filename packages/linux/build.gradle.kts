import de.undercouch.gradle.tasks.download.*
import net.legacylauncher.gradle.*
import org.apache.tools.ant.filters.*
import java.time.*
import java.time.format.*
import java.util.*

plugins {
    `java-base` // required for correct variant aware dependency resolution
    alias(libs.plugins.download)
    net.legacylauncher.brand
}

val bootstrapJar: Configuration by configurations.creating {
    isCanBeDeclared = true
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(LegacyLauncherPackaging.ATTRIBUTE, objects.named(LegacyLauncherPackaging.BOOTSTRAP_JAR))
    }
}

val launcherJar: Configuration by configurations.creating {
    isCanBeDeclared = true
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(LegacyLauncherPackaging.ATTRIBUTE, objects.named(LegacyLauncherPackaging.LAUNCHER_JAR))
    }
}

val launcherLibraries: Configuration by configurations.creating {
    isCanBeDeclared = true
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(LegacyLauncherPackaging.ATTRIBUTE, objects.named(LegacyLauncherPackaging.LAUNCHER_LIBRARY))
    }
}

dependencies {
    bootstrapJar(projects.bootstrap)
    launcherJar(projects.launcher)
    launcherLibraries(projects.launcher)
}

PORTABLE_LINUX_ARCHITECTURES.forEach { pkg ->
    val jreArchive = layout.buildDirectory.file("jreDownloads/linuxJre${pkg.archCapitalized}.tar.gz")
    val verifyTask by tasks.register(pkg.verifyTaskName, Verify::class) {
        src(jreArchive)
        algorithm("SHA-256")
        checksum(pkg.jre.sha256)
    }
    tasks.register(pkg.downloadTaskName, Download::class) {
        src(pkg.jre.url)
        dest(jreArchive)
        overwrite(false)
        finalizedBy(verifyTask)
    }
}

val prepareLinuxBaseBuild by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("linuxBase/${brand.brand.get()}"))

    from(file("baseResources"))

    into("launcher") {
        from(bootstrapJar) {
            rename { "bootstrap.jar" }
        }
        from(launcherJar) {
            rename { "launcher.jar" }
        }
        into("libraries") {
            launcherLibraries.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val path = with(artifact.moduleVersion.id) {
                    "${group.replace('.', '/')}/$name/$version"
                }
                into(path) {
                    from(artifact.file)
                }
            }
        }
    }
}

val prepareLinuxBuild by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("linux/${brand.brand.get()}"))

    from(prepareLinuxBaseBuild)

    into("jre") {
        includeEmptyDirs = false

        PORTABLE_LINUX_ARCHITECTURES.forEach { pkg ->
            val task = tasks.named(pkg.downloadTaskName, Download::class)
            dependsOn(task)

            into(pkg.arch) {
                from(task.map { tarTree(resources.gzip(it.dest)) }) {
                    eachFile {
                        // segments here are jre/<arch>/jdk-21.0.7+6-jre/... - drop the
                        // release-named directory Temurin wraps everything in, so the
                        // launch script can rely on jre/<arch>/bin/java whatever the
                        // bundled release happens to be
                        relativePath = relativePath.dropSegments(2..2)
                    }
                }
            }
        }
    }

    from("resources") {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "date" to DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                    .withLocale(Locale.ROOT)
                    .format(LocalDate.now(ZoneOffset.UTC)),
                "version" to projects.launcher.version
            )
        )

        filteringCharset = "UTF-8"
    }
}

/**
 * Tar rather than zip, because it is the only common archive format that carries the
 * executable bit - both for legism.sh and for every binary inside the bundled JRE. A zip
 * would unpack without them and start nothing.
 */
val tarLinuxBuild by tasks.registering(Tar::class) {
    from(prepareLinuxBuild) {
        into("legism")

        filePermissions {
            unix("0644")
        }
        // The launch script, and everything the JRE ships to be run or dlopen'd. Patterns
        // are matched against the path inside this spec, before into("legism") is applied.
        filesMatching(
            listOf(
                "legism.sh",
                "jre/*/bin/**",
                "jre/*/lib/**/*.so",
                "jre/*/lib/jspawnhelper"
            )
        ) {
            permissions {
                unix("0755")
            }
        }
    }
    destinationDirectory = layout.buildDirectory.dir("update/${brand.brand.get()}")
    archiveFileName = "linux.tar.gz"
    compression = Compression.GZIP
    isPreserveFileTimestamps = true
}

val linuxEnabled = System.getenv("LINUX_ENABLED") == "true"

val createLinuxBuild by tasks.registering {
    enabled = linuxEnabled
    dependsOn(tarLinuxBuild)
    outputs.files(tarLinuxBuild)
}

val assemble: Task by tasks.getting {
    if (linuxEnabled) {
        dependsOn(createLinuxBuild)
    }
    doLast {
        tarLinuxBuild.get().destinationDirectory.get().asFile.mkdirs()
    }
}

fun RelativePath.dropSegments(range: IntRange): RelativePath {
    val segments = segments.filterIndexed { idx, _ ->
        idx !in range
    }
    return RelativePath(isFile, *segments.toTypedArray())
}
