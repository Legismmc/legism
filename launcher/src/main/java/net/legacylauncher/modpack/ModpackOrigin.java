package net.legacylauncher.modpack;

/**
 * Which pack, from which library and at which version, an instance was installed from -
 * kept in the instance's descriptor so it can later be offered updates, show its
 * changelog and link back to its page.
 * <p>
 * Instances made before this existed, or imported from a file, simply have none.
 */
public final class ModpackOrigin {
    private String source;
    private String projectId;
    private String name;
    private String versionId;
    private String versionName;
    private String pageUrl;
    private String iconUrl;

    @SuppressWarnings("unused") // gson
    private ModpackOrigin() {
    }

    public ModpackOrigin(String source, String projectId, String name, String versionId, String versionName,
                         String pageUrl, String iconUrl) {
        this.source = source;
        this.projectId = projectId;
        this.name = name;
        this.versionId = versionId;
        this.versionName = versionName;
        this.pageUrl = pageUrl;
        this.iconUrl = iconUrl;
    }

    /**
     * @return the id of the {@link ModpackSource} it came from
     */
    public String getSource() {
        return source;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getVersionName() {
        return versionName == null ? versionId : versionName;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * @return the same pack at another version
     */
    public ModpackOrigin withVersion(String versionId, String versionName) {
        return new ModpackOrigin(source, projectId, name, versionId, versionName, pageUrl, iconUrl);
    }
}
