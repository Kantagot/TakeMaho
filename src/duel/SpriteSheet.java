package duel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class SpriteSheet {
    private final BufferedImage sheet;
    private final int fw, fh;
    private final int cols, rows, frames;
    private final BufferedImage[] slice; // pre-cut frames

    public SpriteSheet(BufferedImage sheet, int frameW, int frameH) {
        this.sheet = sheet;
        this.fw = frameW;
        this.fh = frameH;

        if (sheet == null || frameW <= 0 || frameH <= 0) {
            cols = rows = frames = 0;
            slice = new BufferedImage[0];
            return;
        }

        cols = Math.max(1, sheet.getWidth()  / fw);
        rows = Math.max(1, sheet.getHeight() / fh);
        frames = cols * rows;

        slice = new BufferedImage[frames];
        // ตัดภาพล่วงหน้าแบบปลอดภัย ไม่เกินขอบ
        for (int i = 0; i < frames; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = col * fw;
            int sy = row * fh;
            int ww = Math.min(fw, sheet.getWidth()  - sx);
            int hh = Math.min(fh, sheet.getHeight() - sy);
            // กันกรณีไฟล์ผิดขนาด
            if (ww <= 0 || hh <= 0) {
                slice[i] = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
                continue;
            }
            slice[i] = sheet.getSubimage(sx, sy, ww, hh);
        }
    }

    public int frameCount() { return frames; }

    private static int wrap(int i, int n) {
        if (n <= 0) return 0;
        int r = i % n;
        return r < 0 ? r + n : r;
    }

    /** วาดเฟรม โดย (cx,cy) คือจุดกึ่งกลางสไปรต์ */
    public void drawFrame(Graphics2D g2, int frame, int cx, int cy, int scale, boolean faceRight) {
        if (slice.length == 0) return;
        int idx = wrap(frame, slice.length);
        BufferedImage img = slice[idx];
        int w = img.getWidth()  * Math.max(1, scale);
        int h = img.getHeight() * Math.max(1, scale);
        int x = cx - w/2;
        int y = cy - h/2;

        if (faceRight) {
            g2.drawImage(img, x, y, w, h, null);
        } else {
            // พลิกซ้ายขวาด้วย drawImage แบบความกว้างติดลบ
            g2.drawImage(img, x + w, y, -w, h, null);
        }
    }
}
