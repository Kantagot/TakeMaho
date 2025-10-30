package duel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class Assets {

    public static BufferedImage img(String path) {
        try {
            return ImageIO.read(Assets.class.getResource(path));
        } catch (IOException | IllegalArgumentException e) {
            return null; // ไม่มีภาพก็ยังรันได้
        }
    }

    // โหลดฟอนต์จาก resource; ใช้ได้กับทั้ง "/duel/resources/xxx.ttf" หรือ "xxx.ttf"
    public static Font font(String resourcePath, float size) {
        String rp = resourcePath;
        if (!rp.startsWith("/")) {
            // ช่วยเดาโฟลเดอร์ resource ปกติของโปรเจ็กต์นี้
            rp = "/duel/resources/" + rp;
        }
        try (InputStream in = Assets.class.getResourceAsStream(rp)) {
            if (in == null) {
                return new Font("SansSerif", Font.PLAIN, Math.round(size));
            }
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            return base.deriveFont(size);
        } catch (Exception e) {
            return new Font("SansSerif", Font.PLAIN, Math.round(size));
        }
    }
}
