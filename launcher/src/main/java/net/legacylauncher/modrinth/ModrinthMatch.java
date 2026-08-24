package net.legacylauncher.modrinth;

/**
 * What {@link ModrinthInstalledLookup} found out about one installed file: which Modrinth
 * project it is, and - if a newer compatible build exists - what to install instead.
 */
public final class ModrinthMatch {
    private final String projectId;
    private final String title;
    private final String iconUrl;
    private final String installedVersionId;
    private final ModrinthVersion latestVersion;
    private final ContentFile latestFile;

    ModrinthMatch(String projectId, String title, String iconUrl, String installedVersionId,
                  ModrinthVersion latestVersion, ContentFile latestFile) {
        this.projectId = projectId;
        this.title = title;
        this.iconUrl = iconUrl;
        this.installedVersionId = installedVersionId;
        this.latestVersion = latestVersion;
        this.latestFile = latestFile;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * @return the newest version that fits the instance's game version and loader, or
     * {@code null} when that could not be determined
     */
    public ModrinthVersion getLatestVersion() {
        return latestVersion;
    }

    /**
     * The downloadable file for {@link #getLatestVersion()}, ready to hand to
     * {@link ModInstaller#update}; {@code null} exactly when {@link #getLatestVersion()} is.
     */
    public ContentFile getLatestFile() {
        return latestFile;
    }

    /**
     * @return whether {@link #getLatestVersion()} is both known and newer than what is
     * actually installed
     */
    public boolean hasUpdate() {
        return latestVersion != null && latestFile != null
                && !latestVersion.getId().equals(installedVersionId);
    }
}
