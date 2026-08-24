package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * A single hit of the Modrinth search index.
 *
 * @see <a href="https://docs.modrinth.com/api/operations/searchprojects/">Modrinth API: search</a>
 */
public class ModrinthProject {
    // search hits key this "project_id"; the /projects batch endpoint keys it "id" instead
    @SerializedName(value = "project_id", alternate = {"id"})
    private String projectId;
    @SerializedName("project_type")
    private String projectType;
    private String slug;
    private String title;
    private String description;
    private String author;
    private long downloads;
    private long follows;
    @SerializedName("icon_url")
    private String iconUrl;
    @SerializedName("display_categories")
    private List<String> displayCategories;
    private List<String> categories;
    @SerializedName("client_side")
    private String clientSide;
    @SerializedName("server_side")
    private String serverSide;
    private List<String> versions;

    public String getProjectId() {
        return projectId;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title == null ? slug : title;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getAuthor() {
        return author == null ? "" : author;
    }

    public long getDownloads() {
        return downloads;
    }

    public long getFollows() {
        return follows;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public List<String> getDisplayCategories() {
        if (displayCategories != null && !displayCategories.isEmpty()) {
            return displayCategories;
        }
        return categories == null ? Collections.<String>emptyList() : categories;
    }

    public String getClientSide() {
        return clientSide;
    }

    public String getServerSide() {
        return serverSide;
    }

    public List<String> getVersions() {
        return versions == null ? Collections.<String>emptyList() : versions;
    }

    /**
     * @return the page a user would open in a browser to read about this project
     */
    public String getPageUrl() {
        String type = projectType == null ? "mod" : projectType;
        return "https://modrinth.com/" + type + "/" + (slug == null ? projectId : slug);
    }

    @Override
    public String toString() {
        return "ModrinthProject{" + projectId + " " + getTitle() + "}";
    }
}
