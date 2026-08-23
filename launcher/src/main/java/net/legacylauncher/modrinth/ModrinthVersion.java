package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * A published version of a Modrinth project.
 */
public class ModrinthVersion {
    private String id;
    @SerializedName("project_id")
    private String projectId;
    private String name;
    @SerializedName("version_number")
    private String versionNumber;
    @SerializedName("version_type")
    private String versionType;
    @SerializedName("date_published")
    private String datePublished;
    private long downloads;
    @SerializedName("game_versions")
    private List<String> gameVersions;
    private List<String> loaders;
    private List<ModrinthFile> files;
    private List<ModrinthDependency> dependencies;

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name == null ? versionNumber : name;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public String getVersionType() {
        return versionType;
    }

    public String getDatePublished() {
        return datePublished;
    }

    public long getDownloads() {
        return downloads;
    }

    public List<String> getGameVersions() {
        return gameVersions == null ? Collections.<String>emptyList() : gameVersions;
    }

    public List<String> getLoaders() {
        return loaders == null ? Collections.<String>emptyList() : loaders;
    }

    public List<ModrinthFile> getFiles() {
        return files == null ? Collections.<ModrinthFile>emptyList() : files;
    }

    public List<ModrinthDependency> getDependencies() {
        return dependencies == null ? Collections.<ModrinthDependency>emptyList() : dependencies;
    }

    /**
     * @return the file Modrinth marks as primary, or the first one; {@code null} if the
     * version carries no files at all
     */
    public ModrinthFile getPrimaryFile() {
        ModrinthFile first = null;
        for (ModrinthFile file : getFiles()) {
            if (file.getUrl() == null) {
                continue;
            }
            if (file.isPrimary()) {
                return file;
            }
            if (first == null) {
                first = file;
            }
        }
        return first;
    }

    @Override
    public String toString() {
        return "ModrinthVersion{" + id + " " + versionNumber + "}";
    }
}
