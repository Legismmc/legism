package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;

/**
 * A dependency declared by a {@link ModrinthVersion}.
 * <p>
 * {@code dependency_type} is one of {@code required}, {@code optional},
 * {@code incompatible} or {@code embedded}; only required ones are installed
 * automatically.
 */
public class ModrinthDependency {
    @SerializedName("version_id")
    private String versionId;
    @SerializedName("project_id")
    private String projectId;
    @SerializedName("file_name")
    private String fileName;
    @SerializedName("dependency_type")
    private String dependencyType;

    public String getVersionId() {
        return versionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public boolean isRequired() {
        return "required".equals(dependencyType);
    }

    @Override
    public String toString() {
        return "ModrinthDependency{" + dependencyType + " " + (versionId == null ? projectId : versionId) + "}";
    }
}
