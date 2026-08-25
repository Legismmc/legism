package net.legacylauncher.ui.server;

import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.server.ServerInstance;
import net.legacylauncher.server.ServerInstanceManager;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.modrinth.ModrinthPanel;
import net.legacylauncher.ui.modrinth.ModrinthStrings;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * Everything about one local server: its console, its {@code server.properties}, and - for
 * cores that support them - a Modrinth plugin browser aimed straight at its own
 * {@code plugins} folder.
 */
public class ServerDetailPanel extends JPanel {
    public ServerDetailPanel(MainPane pane, ServerInstanceManager manager, ServerInstance server) {
        super(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(ModrinthStrings.get("server.tab.console"), new ServerConsolePanel(manager, server));
        tabs.addTab(ModrinthStrings.get("server.tab.properties"), new ServerPropertiesPanel(manager, server));
        tabs.addTab(ModrinthStrings.get("server.tab.plugins"), buildPluginsTab(pane, server));

        add(tabs, BorderLayout.CENTER);
    }

    private static JPanel buildPluginsTab(MainPane pane, ServerInstance server) {
        if (!server.getCore().supportsPlugins()) {
            JPanel hint = new JPanel(new BorderLayout());
            JLabel label = new JLabel(ModrinthStrings.get("server.plugins.vanilla-hint"));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            hint.add(label, BorderLayout.CENTER);
            return hint;
        }
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new ModrinthPanel(pane, ContentType.PLUGIN,
                () -> ModTarget.ofVersionId(server.getCoreVersion(), server.getFolder()), false), BorderLayout.CENTER);
        return wrapper;
    }
}
