package net.legacylauncher.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class LegacyLauncherBrandPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<LegacyLauncherBrandExtension>("brand")

        extension.brand.convention(System.getenv("SHORT_BRAND") ?: "develop")
        extension.displayName.convention(extension.brand.map { brand ->
            when (brand) {
                "develop" -> "Dev"
                "tgsko" -> "Stable"
                "tgsko_beta" -> "Beta"
                else -> brand
            }
        })
        extension.productName.convention(System.getenv("PRODUCT_NAME") ?: "Legacy by tgsko")
        extension.version.convention(extension.brand.map { brand ->
            "${project.version}+${brand.replace(Regex("[^\dA-Za-z\-]"), "-")}${System.getenv("VERSION_SUFFIX") ?: ""}"
        })

        // set SUPPORT_EMAIL in the environment before building a release
        extension.supportEmail.convention(System.getenv("SUPPORT_EMAIL") ?: "support@example.invalid")
    }
}
