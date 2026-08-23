package net.legacylauncher.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * One page of search results.
 */
public class ModrinthSearchResult {
    private List<ModrinthProject> hits;
    private int offset;
    private int limit;
    @SerializedName("total_hits")
    private int totalHits;

    public List<ModrinthProject> getHits() {
        return hits == null ? Collections.<ModrinthProject>emptyList() : hits;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public boolean hasMore() {
        return offset + getHits().size() < totalHits;
    }
}
