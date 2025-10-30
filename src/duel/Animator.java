package duel;

public class Animator {
    private final SpriteSheet sheet;
    private final int ticksPerFrame;
    private int tick = 0;
    private int frame = 0;

    public Animator(SpriteSheet sheet, int ticksPerFrame) {
        this.sheet = sheet;
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
    }

    public void update() {
        if (sheet.frameCount() <= 1) return;
        if (++tick >= ticksPerFrame) {
            tick = 0;
            frame++;
            if (frame >= sheet.frameCount()) frame = 0;
        }
    }

    public int frame() { return frame; }
    public SpriteSheet sheet() { return sheet; }
}
