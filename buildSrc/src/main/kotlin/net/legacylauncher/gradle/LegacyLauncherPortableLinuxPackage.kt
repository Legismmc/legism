package net.legacylauncher.gradle

data class PortableLinuxJreInfo(val url: String, val sha256: String)

/**
 * @param arch the directory the JRE lands in, and the name the launch script looks for -
 *             these are the values `uname -m` reports, not the Windows spelling, so that
 *             the script can map a host straight onto a directory
 */
data class PortableLinuxArchPackageInfo(val arch: String, val jre: PortableLinuxJreInfo) {
    val archCapitalized = arch.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val downloadTaskName = "downloadLinux$archCapitalized"
    val verifyTaskName = "verifyLinux$archCapitalized"
}

val PORTABLE_LINUX_ARCHITECTURES = listOf(
    PortableLinuxArchPackageInfo(
        "x64",
        PortableLinuxJreInfo(
            "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jre_x64_linux_hotspot_21.0.7_6.tar.gz",
            "6d48379e00d47e6fdd417e96421e973898ac90765ea8ff2d09ae0af6d5d6a1c6"
        )
    ),
    PortableLinuxArchPackageInfo(
        "aarch64",
        PortableLinuxJreInfo(
            "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jre_aarch64_linux_hotspot_21.0.7_6.tar.gz",
            "ab455a401d25e0cd20e652d2ee72e9f56beba0d9faac5a5c62c9b27a19df804b"
        )
    )
)
