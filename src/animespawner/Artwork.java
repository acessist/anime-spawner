package animespawner;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** App icon and rendering hints. Animation content comes from imports. */
final class Artwork {
    static BufferedImage icon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        quality(g);
        g.setColor(new Color(117, 100, 225));
        g.fillRoundRect(2, 2, 60, 60, 19, 19);
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double radius = i % 2 == 0 ? 23 : 8;
            double x = 32 + Math.cos(angle) * radius;
            double y = 32 + Math.sin(angle) * radius;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);
        g.dispose();
        return image;
    }

    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
}
