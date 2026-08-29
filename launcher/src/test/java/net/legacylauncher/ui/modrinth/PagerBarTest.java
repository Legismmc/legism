package net.legacylauncher.ui.modrinth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The page count is where the two libraries' very different limits meet, so it is worth
 * pinning down: Modrinth answers at any depth, CurseForge stops dead partway through.
 */
class PagerBarTest {

    private static final int PAGE = 20;
    private static final int CURSEFORGE_DEPTH = 10000;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    @Test
    @DisplayName("counts whole and partial pages")
    void countsPages() {
        assertEquals(0, PagerBar.pageCount(0, PAGE, NO_LIMIT));
        assertEquals(1, PagerBar.pageCount(1, PAGE, NO_LIMIT));
        assertEquals(1, PagerBar.pageCount(20, PAGE, NO_LIMIT));
        assertEquals(2, PagerBar.pageCount(21, PAGE, NO_LIMIT));
        assertEquals(3711, PagerBar.pageCount(74216, PAGE, NO_LIMIT));
    }

    @Test
    @DisplayName("stops at the deepest page CurseForge will answer")
    void respectsCurseForgeDepth() {
        // measured against the live API: index=9980 is answered, index=9990 is a 400, so
        // page 500 (offset 9980) is the last one that can be asked for
        assertEquals(500, PagerBar.pageCount(10000, PAGE, CURSEFORGE_DEPTH));
        assertEquals(500, PagerBar.pageCount(999999, PAGE, CURSEFORGE_DEPTH));
    }

    @Test
    @DisplayName("offers no page the provider cannot serve")
    void everyPageIsReachable() {
        int pages = PagerBar.pageCount(999999, PAGE, CURSEFORGE_DEPTH);
        int lastOffset = (pages - 1) * PAGE;
        assertEquals(9980, lastOffset);
        // the request the last page makes has to stay inside the documented window
        assertEquals(true, lastOffset + PAGE <= CURSEFORGE_DEPTH);
    }

    @Test
    @DisplayName("a short result set is not padded out to the limit")
    void fewerResultsWin() {
        assertEquals(3, PagerBar.pageCount(50, PAGE, CURSEFORGE_DEPTH));
    }
}
