package net.legacylauncher.ui.images;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

/**
 * Decodes an icon fetched from a content library (Modrinth, CurseForge) into a square
 * image of the given size.
 * <p>
 * Those libraries hand out icons as PNG, JPEG, GIF, WebP or SVG. The JDK's own {@link
 * ImageIO} only reads the first three out of the box, so a raster decode is tried first -
 * WebP works once the TwelveMonkeys plugin on the classpath registers itself - and an SVG
 * rasterization is tried second, rather than trusting the URL's extension, which Modrinth's
 * CDN does not always bother to set correctly.
 */
@Slf4j
public final class IconLoader {

    private IconLoader() {
    }

    /**
     * @return the decoded image, or {@code null} when none of the known formats could make
     * sense of the bytes
     */
    public static BufferedImage decode(byte[] data, String sourceUrl, int size) {
        if (data == null || data.length == 0) {
            return null;
        }
        BufferedImage raster = decodeRaster(data);
        if (raster != null) {
            return raster;
        }
        return decodeSvg(data, sourceUrl, size);
    }

    private static BufferedImage decodeRaster(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            log.debug("Could not decode the icon as a raster image: {}", e.toString());
            return null;
        }
    }

    private static BufferedImage decodeSvg(byte[] data, String sourceUrl, int size) {
        try {
            URI uri = sourceUrl == null ? null : URI.create(sourceUrl);
            SVGDocument document = new SVGLoader().load(
                    new ByteArrayInputStream(data), uri, LoaderContext.createDefault());
            if (document == null) {
                return null;
            }
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                document.render(null, g, new ViewBox(size, size));
            } finally {
                g.dispose();
            }
            return image;
        } catch (Exception e) {
            log.debug("Could not decode the icon as SVG: {}", e.toString());
            return null;
        }
    }
}
