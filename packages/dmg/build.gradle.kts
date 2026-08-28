import de.undercouch.gradle.tasks.download.*
import net.legacylauncher.gradle.*
import org.apache.tools.ant.filters.*

plugins {
    base
    alias(libs.plugins.download)
    net.legacylauncher.brand
}

// A .app bundle carries exactly one runtime, so the architecture is chosen at build time
// and each one produces its own disk image. Defaults to Apple Silicon, which is what
// nearly every Mac sold since 2020 is; pass -PdmgArch=x64 for an Intel build.
val dmgArch = (findProperty("dmgArch") as String? ?: "aarch64").also {
    require(it == "aarch64" || it == "x64") { "dmgArch must be aarch64 or x64, got '$it'" }
}

// Checksums are Azul's own, from api.azul.com/metadata/v1/zulu/packages.
val jreZipSha256 = when (dmgArch) {
    "aarch64" -> "7c10a9ca05bb19d0b7179e65d3ad7e7f514afa4a53030b5af83d8f8f06be7f1b"
    else -> "49c9ab085278660c7f3236a70be07a9d15077ce6815f97239d3d3a066c6ad1dd"
}
val jreZipName = "zulu21.32.17-ca-fx-jre21.0.2-macosx_$dmgArch"
val jreZipDownload = "https://cdn.azul.com/zulu/bin/$jreZipName.zip"
val jreZipEntry = "$jreZipName/zulu-21.jre"
val jreZipFile = layout.buildDirectory.file("jreZip/macOsJre-$dmgArch.zip")

val bundleName = "${brand.productName.get()} ${brand.displayName.get()}"

evaluationDependsOn(projects.launcher.path)

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

dependencies {
    bootstrapJar(projects.bootstrap)
}

val tokens = mapOf(
    "bundle_name" to bundleName,
    "short_brand" to brand.brand.get(),
    "full_brand" to brand.displayName.get(),
    "version" to projects.launcher.version,
    "arch" to dmgArch
)

val verifyMacOsJre by tasks.registering(Verify::class) {
    src(jreZipFile)
    algorithm("SHA-256")
    checksum(jreZipSha256)
}

val downloadMacOsJre by tasks.registering(Download::class) {
    src(jreZipDownload)
    dest(jreZipFile)
    overwrite(false)
    finalizedBy(verifyMacOsJre)
}

val prepareDmgBuild by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("dmg/${brand.brand.get()}"))

    from("TL.icns")
    from("background/background.tiff")
    from("script") {
        filter<ReplaceTokens>("tokens" to tokens)
        filteringCharset = "UTF-8"
    }

    into("$bundleName.app/Contents") {
        from("contents/binary")
        from("contents/textual") {
            filter<ReplaceTokens>("tokens" to tokens)
            filteringCharset = "UTF-8"
        }

        into("Resources") {
            from("TL.icns")
        }

        into("app") {
            from(bootstrapJar) {
                rename { "bootstrap.jar" }
            }
        }

        into("runtime") {
            dependsOn(downloadMacOsJre)
            from(zipTree(downloadMacOsJre.get().dest)) {
                eachFile {
                    if (relativePath.segments.size <= 5) exclude()
                    else relativePath = relativePath.dropSegments(3..4)
                }
                includeEmptyDirs = false
            }
        }
    }
}

val assemble: Task by tasks.getting {
    if (System.getenv("DMG_ENABLED") == "true") {
        dependsOn(prepareDmgBuild)
    }
    doLast {
        prepareDmgBuild.get().destinationDir.mkdirs()
    }
}

fun RelativePath.dropSegments(range: IntRange): RelativePath {
    val segments = segments.filterIndexed { idx, _ ->
        idx !in range
    }
    return RelativePath(isFile, *segments.toTypedArray())
}
