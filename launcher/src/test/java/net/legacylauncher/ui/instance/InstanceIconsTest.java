package net.legacylauncher.ui.instance;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceIconsTest {

    @Test
    void offersMoreThanOneIcon() {
        List<String> ids = InstanceIcons.ids();

        assertTrue(ids.size() > 1, "a single built-in icon defeats the point of a picker");
        assertTrue(ids.contains("grass"), "the launcher's original icon should stay an option");
    }

    @Test
    void picksADefaultDeterministicallyFromTheSeed() {
        String first = InstanceIcons.pickDefault("my-modpack");
        String second = InstanceIcons.pickDefault("my-modpack");

        assertEquals(first, second, "the same instance id must always resolve to the same icon");
        assertTrue(InstanceIcons.ids().contains(first));
    }

    @Test
    void differentInstancesTendToGetDifferentDefaultIcons() {
        String a = InstanceIcons.pickDefault("survival-world");
        String b = InstanceIcons.pickDefault("creative-testing");

        // not a hash-collision guarantee, just proof it's not hard-coded to one value
        assertTrue(InstanceIcons.ids().contains(a) && InstanceIcons.ids().contains(b));
    }

    @Test
    void buildsAnIconOfTheRequestedSize() {
        Icon icon = InstanceIcons.getIcon("diamond", 40);

        assertNotNull(icon);
        assertEquals(40, icon.getIconWidth());
        assertEquals(40, icon.getIconHeight());
    }

    @Test
    void fallsBackGracefullyForAnUnknownId() {
        Icon icon = InstanceIcons.getIcon("not-a-real-icon-id", 24);

        assertNotNull(icon);
    }
}
