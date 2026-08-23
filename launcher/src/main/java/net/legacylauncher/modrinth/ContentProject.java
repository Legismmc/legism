package net.legacylauncher.modrinth;

import java.util.Collections;
import java.util.List;

/**
 * One searchable item, as the browser needs it, independent of which library it came from.
 */
public class ContentProject {
    private final String id;
    private final String title;
    private final String description;
    private final String author;
    private final long downloads;
    private final String iconUrl;
    private final List<String> categories;
    private final String pageUrl;

    public ContentProject(String id, String title, String description, String author,
                          long downloads, String iconUrl, List<String> categories, String pageUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.downloads = downloads;
        this.iconUrl = iconUrl;
        this.categories = categories == null ? Collections.<String>emptyList() : categories;
        this.pageUrl = pageUrl;
    }

    /**
     * Identifier within its own library; only that library can make sense of it.
     */
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title == null ? id : title;
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

    public String getIconUrl() {
        return iconUrl;
    }

    public List<String> getCategories() {
        return categories;
    }

    /**
     * The page a user would open in a browser to read about this project.
     */
    public String getPageUrl() {
        return pageUrl;
    }

    @Override
    public String toString() {
        return "ContentProject{" + id + " " + getTitle() + "}";
    }
}
