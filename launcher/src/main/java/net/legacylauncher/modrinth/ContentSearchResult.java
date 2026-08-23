package net.legacylauncher.modrinth;

import java.util.Collections;
import java.util.List;

/**
 * One page of search results from a content library.
 */
public class ContentSearchResult {
    private final List<ContentProject> hits;
    private final int offset;
    private final int total;

    public ContentSearchResult(List<ContentProject> hits, int offset, int total) {
        this.hits = hits == null ? Collections.<ContentProject>emptyList() : hits;
        this.offset = offset;
        this.total = total;
    }

    public List<ContentProject> getHits() {
        return hits;
    }

    public int getOffset() {
        return offset;
    }

    public int getTotal() {
        return total;
    }

    public boolean hasMore() {
        return offset + hits.size() < total;
    }
}
