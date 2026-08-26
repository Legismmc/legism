package net.legacylauncher.ui.modrinth;

import net.legacylauncher.modrinth.ContentProject;

/**
 * What a {@link ModrinthProjectCell} needs from the screen that holds it.
 * <p>
 * The same cell serves both the per-instance content browser and the modpack browser,
 * even though "install" means very different things to them - dropping a file into a game
 * directory in one case, creating a whole new instance in the other.
 */
public interface ContentCellHost {

    /**
     * Installs the project this cell stands for, reporting progress back to the cell.
     * Returns immediately; the work happens off the Swing thread.
     */
    void install(ContentProject project, ModrinthProjectCell cell);

    /**
     * Whether this project is already installed, so the cell can say so instead of
     * offering to install it again.
     */
    boolean isProjectInstalled(String projectId);
}
