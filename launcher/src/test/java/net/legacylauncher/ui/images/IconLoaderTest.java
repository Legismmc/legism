package net.legacylauncher.ui.images;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IconLoaderTest {

    @Test
    void decodesAPlainRasterImage() throws IOException {
        byte[] png = encodePng(new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB));

        BufferedImage decoded = IconLoader.decode(png, "https://cdn.modrinth.com/icon.png", 32);

        assertNotNull(decoded);
    }

    @Test
    void decodesAnSvgIconInsteadOfSkippingIt() {
        byte[] svg = ("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'>"
                + "<rect width='10' height='10' fill='#4fd8d8'/></svg>")
                .getBytes(StandardCharsets.UTF_8);

        BufferedImage decoded = IconLoader.decode(svg, "https://cdn.modrinth.com/icon.svg", 32);

        assertNotNull(decoded, "an SVG icon should be rasterized rather than left as null");
        assertEquals(32, decoded.getWidth());
        assertEquals(32, decoded.getHeight());
    }

    @Test
    void returnsNullForGarbageInsteadOfThrowing() {
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};

        assertNull(IconLoader.decode(garbage, "https://cdn.modrinth.com/icon.dat", 32));
    }

    @Test
    void returnsNullForEmptyData() {
        assertNull(IconLoader.decode(new byte[0], "https://cdn.modrinth.com/icon.png", 32));
        assertNull(IconLoader.decode(null, "https://cdn.modrinth.com/icon.png", 32));
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
