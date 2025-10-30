package duel;

import duel.input.Keybinds;
import duel.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener, KeyListener {
    // ====== Screen & Loop ======
    public static final int W = 1280, H = 720;
    public static final int FPS_MS = 16; // ~60 FPS

    // ====== Rules ======
    public static final double PLAYER_SPEED = 4.0;
    public static final int MAX_BUFFER = 8;
    public static final int KILL_TO_WIN = 10;

    // Cooldowns (ms)
    public static final int CD_STRAIGHT = 700;
    public static final int CD_HOMING   = 1200;
    public static final int CD_REFLECT  = 700;
    public static final int CD_STONE    = 1000;
    public static final int CD_BLINK    = 900;

    // Durations (ms)
    public static final int DUR_REFLECT = 1000;
    public static final int DUR_STONE   = 1000;
    public static final int DUR_BLINK_IFRAME = 200;
    public static final int REFLECT_LENIENCY = 100;

    // Bullets
    public static final double BULLET_SPEED     = 7.0;
    public static final double HOMING_TURN_RATE = 0.12;

    // Blink tune
    public static final double BLINK_DISTANCE = 200.0;
    public static final double BLINK_ESCAPE_RADIUS = 72.0;
    public static final int    BLINK_ESCAPE_WINDOW_MS = 220;

    // Rendering
    private static final int SPR_SCALE = 2;

    private final Player[] ps = new Player[2];
    private final List<Bullet> bullets = new ArrayList<>();
    private final javax.swing.Timer loop = new javax.swing.Timer(FPS_MS, this);

    // ยก/แมตช์
    private final int[] matchScore = new int[]{0,0};
    private boolean pendingReset = false;
    private boolean resetKeepKills = true;
    private boolean matchOver = false;

    private final GameMode mode;
    private final Random rng = new Random();

    // Pause / back to menu
    private boolean paused = false;
    private final Runnable onExitToMenu;

    // พื้นหลังฉากต่อสู้
    private Image bgBattle;

    // Character animators / overlays
    private Animator p1Idle, p1Run, p1Cast, p2Idle, p2Run, p2Cast;
    private BufferedImage shieldBlue, shieldRed, stoneBlue, stoneRed;

    // ===== PvC bot pacing =====
    private long botLastThinkAt = 0;
    private long botLastCastAt  = 0;
    private static final int BOT_THINK_MS = 90;
    private static final int BOT_CAST_GAP = 650;

    /* ----------------- Bullet ----------------- */
    static class Bullet {
        double x,y,vx,vy;
        int ownerId;
        boolean homing;
        boolean dead;
        Color color;
        int circleR  = 6;
        int diaLen   = 20;
        int diaWidth = 10;

        Bullet(double x,double y,double vx,double vy,int ownerId,boolean homing, Color color){
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.ownerId=ownerId; this.homing=homing; this.color=color;
            if (homing) { this.diaLen = 20; this.diaWidth = 10; }
            else        { this.circleR = 6; }
        }
        double ang(){ return Math.atan2(vy, vx); }
    }

    /* ----------------- ctor ----------------- */
    public GamePanel(GameMode mode, Runnable onExitToMenu) {
        this.mode = mode;
        this.onExitToMenu = onExitToMenu;

        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(20,22,26));
        setDoubleBuffered(true);
        setFocusable(true);
        addKeyListener(this);

        // โหลดพื้นหลังสนาม
        try {
            bgBattle = Assets.img("/duel/resources/Bg.png");
        } catch (Throwable t) {
            // สำรองถ้าไม่มี Assets หรือพาธไม่ตรง
            bgBattle = new ImageIcon("src/duel/resources/Bg.png").getImage();
        }

        // Blue (JKL)
        ps[0] = new Player(
                "P1", W*0.25, H*0.5,
                Keybinds.P1_MOVE, Keybinds.P1_COMBO,
                "J","K","L","I",
                "jkl","lkj","kjl","klj","ljk",
                new Color(120,170,255), new Color(120,200,255,120)
        );

        // Red (ลูกศร + 123)
        String p2Name = (mode==GameMode.PVC) ? "COM" : "P2";
        ps[1] = new Player(
                p2Name, W*0.75, H*0.5,
                Keybinds.P2_MOVE, Keybinds.P2_COMBO,
                "1","2","3","5",
                "123","321","213","231","312",
                new Color(255,160,120), new Color(255,180,120,120)
        );
        ps[1].isBot = (mode==GameMode.PVC);

        // Sprites
        p1Idle = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_blue_idle.png"), 64, 64), 10);
        p1Run  = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_blue_run.png"),  64, 64), 12);
        p1Cast = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_blue_cast.png"), 64, 64), 12);
        p2Idle = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_red_idle.png"), 64, 64), 10);
        p2Run  = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_red_run.png"),  64, 64), 12);
        p2Cast = new Animator(new SpriteSheet(Assets.img("/duel/resources/wizard_red_cast.png"), 64, 64), 12);

        shieldBlue = Assets.img("/duel/resources/shield_blue.png");
        shieldRed  = Assets.img("/duel/resources/shield_red.png");
        stoneBlue  = Assets.img("/duel/resources/stone_blue.png");
        stoneRed   = Assets.img("/duel/resources/stone_red.png");

        loop.start();
    }

    /* ----------------- Loop ----------------- */
    @Override public void actionPerformed(ActionEvent e) {
        if (paused) { repaint(); return; }
        if (matchOver) { repaint(); return; }
        long now = System.currentTimeMillis();

        // อัปเดตผู้เล่น
        for (Player p : ps) {
            p.stoneOn       = now < p.stoneUntil;
            p.reflectOn     = now < p.reflectUntil;
            p.blinkIFrameOn = now < p.blinkIFrameUntil;

            if (!p.stoneOn) {
                p.vx = (p.right?1:0) - (p.left?1:0);
                p.vy = (p.down?1:0)  - (p.up?1:0);
                double len = Math.hypot(p.vx, p.vy);
                if (len>0) { p.vx = p.vx/len*PLAYER_SPEED; p.vy = p.vy/len*PLAYER_SPEED; }
                p.x = clamp(p.x + p.vx, 40, W-40);
                p.y = clamp(p.y + p.vy, 60, H-60);
            } else { p.vx = p.vy = 0; }

            p.animPhase += 0.1; if (p.animPhase>Math.PI*2) p.animPhase-=Math.PI*2;
        }

        // หันหน้าเข้าหากัน
        ps[0].facingRight = (ps[1].x > ps[0].x);
        ps[1].facingRight = (ps[0].x < ps[1].x);

        // บอท (ถ้าเล่น PvC)
        if (ps[1].isBot) botTick(now);

        // กระสุน + ชน
        for (int bi = 0; bi < bullets.size(); bi++) {
            Bullet b = bullets.get(bi);
            if (b.homing) {
                int tid = (b.ownerId==0?1:0);
                Player t = ps[tid];

                double d = Math.hypot(t.x - b.x, t.y - b.y);
                if (d <= BLINK_ESCAPE_RADIUS && (now - t.lastBlinkAt) <= BLINK_ESCAPE_WINDOW_MS) {
                    double angStay = Math.atan2(b.vy, b.vx);
                    b.vx = Math.cos(angStay) * BULLET_SPEED;
                    b.vy = Math.sin(angStay) * BULLET_SPEED;
                    b.homing = false;
                } else {
                    double desired = Math.atan2(t.y - b.y, t.x - b.x);
                    double cur = Math.atan2(b.vy, b.vx);
                    double delta = normalizeAngle(desired - cur);
                    double turn = clamp(delta, -HOMING_TURN_RATE, HOMING_TURN_RATE);
                    double ang = cur + turn;
                    b.vx = Math.cos(ang)*BULLET_SPEED;
                    b.vy = Math.sin(ang)*BULLET_SPEED;
                }
            }

            b.x += b.vx; b.y += b.vy;
            if (b.x < -40 || b.x > W+40 || b.y < -40 || b.y > H+40) { b.dead = true; continue; }

            for (int i=0;i<2;i++) {
                if (i == b.ownerId) continue;
                Player target = ps[i];

                if (isShieldActive(target) && nearShield(target, b.x, b.y)) {
                    reflectToOther(b, i);
                    recolorToOwner(b);
                    Sfx.play("reflect");
                    continue;
                }

                if (bodyHit(target, b.x, b.y)) {
                    if (target.blinkIFrameOn || target.stoneOn) { b.dead = true; continue; }
                    if (now <= target.reflectUntil + REFLECT_LENIENCY) {
                        reflectToOther(b, i);
                        recolorToOwner(b);
                        Sfx.play("reflect");
                        continue;
                    }
                    target.hp = 0; b.dead = true; onKill((i==0)?1:0);
                    break;
                }
            }
        }
        bullets.removeIf(bb -> bb.dead);

        if (pendingReset) {
            resetRound(resetKeepKills);
            pendingReset = false;
            requestFocusInWindow();
        }

        p1Idle.update(); p1Run.update(); p1Cast.update();
        p2Idle.update(); p2Run.update(); p2Cast.update();

        repaint();
    }

    /* ----------------- PvC Bot ----------------- */
    private void botTick(long now) {
        if (now - botLastThinkAt < BOT_THINK_MS) return;
        botLastThinkAt = now;

        Player me  = ps[1];
        Player you = ps[0];

        double dx = you.x - me.x, dy = you.y - me.y;
        double dist = Math.hypot(dx, dy);
        me.left = me.right = me.up = me.down = false;

        if (dist > 420) {
            if (dx>16) me.right = true; else if (dx<-16) me.left=true;
            if (dy>16) me.down  = true; else if (dy<-16) me.up  =true;
        } else if (dist < 320) {
            if (dx>16) me.left=true; else if (dx<-16) me.right=true;
            if (dy>16) me.up=true;   else if (dy<-16) me.down =true;
        }

        Bullet imminent = closestThreatTo(me, 110);
        if (imminent != null) {
            double px = -imminent.vy, py = imminent.vx;
            if ((px*(me.x-imminent.x) + py*(me.y-imminent.y)) < 0) { px = -px; py = -py; }
            if (Math.abs(px) > Math.abs(py)) { if (px>0) me.right = true; else me.left = true; }
            else { if (py>0) me.down = true; else me.up = true; }
        }

        if (now - botLastCastAt >= BOT_CAST_GAP) {
            if (me.cds.ready(now, Skill.REFLECT)) {
                for (Bullet b : bullets) {
                    if (b.ownerId==1) continue;
                    double d = Math.hypot(b.x-me.x, b.y-me.y);
                    if (d < 85) {
                        tryCast(me, 1, Skill.REFLECT);
                        botLastCastAt = now;
                        return;
                    }
                }
            }
            if (me.cds.ready(now, Skill.BLINK)) {
                for (Bullet b : bullets) {
                    if (b.ownerId==1) continue;
                    double d = Math.hypot(b.x-me.x, b.y-me.y);
                    boolean scary = b.homing ? d < 140 : d < 90;
                    if (scary && !me.cds.ready(now, Skill.REFLECT)) {
                        tryCast(me, 1, Skill.BLINK);
                        botLastCastAt = now;
                        return;
                    }
                }
            }
            if (me.cds.ready(now, Skill.STONE) && dist < 180) {
                int enemyBulletsNear = 0;
                for (Bullet b : bullets) if (b.ownerId==0 && Math.hypot(b.x-me.x,b.y-me.y) < 150) enemyBulletsNear++;
                if (enemyBulletsNear >= 1 && rng.nextFloat() < 0.35f) {
                    tryCast(me, 1, Skill.STONE);
                    botLastCastAt = now;
                    return;
                }
            }
            if (me.cds.ready(now, Skill.HOMING) && (dist > 220 || Math.hypot(you.vx,you.vy) > 2.8)) {
                tryCast(me, 1, Skill.HOMING);
                botLastCastAt = now;
                return;
            }
            if (me.cds.ready(now, Skill.STRAIGHT) && dist > 120) {
                double tx = you.x, ty = you.y;
                double leadT = Math.min(0.38, dist / (BULLET_SPEED*18.0));
                tx += you.vx * (leadT * 60.0);
                ty += you.vy * (leadT * 60.0);
                double ang = Math.atan2(ty - me.y, tx - me.x);
                Color col = new Color(255,170,120);
                bullets.add(new Bullet(me.x, me.y, Math.cos(ang)*BULLET_SPEED, Math.sin(ang)*BULLET_SPEED, 1, false, col));
                me.cds.set(now, Skill.STRAIGHT, CD_STRAIGHT);
                me.castUntil = now + 120;
                Sfx.play("shoot");
                botLastCastAt = now;
            }
        }
    }

    private Bullet closestThreatTo(Player p, double within) {
        Bullet best = null; double bestD = Double.MAX_VALUE;
        for (Bullet b : bullets) {
            if (b.ownerId == (p==ps[0]?0:1)) continue;
            double d = Math.hypot(b.x - p.x, b.y - p.y);
            if (d < within && d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /* ----------------- เมื่อมีการฆ่า ----------------- */
    private void onKill(int killerId) {
        ps[killerId].kills++;
        if (ps[killerId].kills >= KILL_TO_WIN) {
            matchScore[killerId]++;
            matchOver = true;
            Sfx.play("win");
        } else {
            pendingReset = true;
            resetKeepKills = true;
        }
    }

    /* ----------------- Render ----------------- */
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g.create();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // พื้นหลังฉากต่อสู้
        if (bgBattle != null) g2.drawImage(bgBattle, 0, 0, W, H, this);
        else {
            g2.setColor(new Color(18,20,26));
            g2.fillRect(0,0,W,H);
        }

        // กรอบนุ่ม ๆ
        g2.setColor(new Color(255,255,255,28));
        g2.drawRoundRect(20,20,W-40,H-40,20,20);

        // สกอร์รวม
        String sc = "Score  P1 " + matchScore[0] + " - " + matchScore[1] + " " + (ps[1].isBot?"COM":"P2");
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
        g2.drawString(sc, (W - g2.getFontMetrics().stringWidth(sc))/2, 32);

        // ผู้เล่น
        for (int i=0;i<2;i++) {
            Player p = ps[i];
            int bob = (int)Math.round(Math.sin(p.animPhase)*2);
            boolean moving  = Math.abs(p.vx)+Math.abs(p.vy) > 0.1;
            boolean casting = System.currentTimeMillis() < p.castUntil;
            Animator an = casting ? (i==0? p1Cast : p2Cast)
                    : (moving  ? (i==0? p1Run  : p2Run )
                    : (i==0? p1Idle : p2Idle));

            int px = (int)Math.round(p.x);
            int py = (int)Math.round(p.y) + bob;
            an.sheet().drawFrame(g2, an.frame(), px, py, SPR_SCALE, p.facingRight);

            if (p.reflectOn) {
                BufferedImage sh = (i==0)? shieldBlue : shieldRed;
                if (sh!=null) g2.drawImage(sh, px - (sh.getWidth()*SPR_SCALE)/2, py - (sh.getHeight()*SPR_SCALE)/2,
                        sh.getWidth()*SPR_SCALE, sh.getHeight()*SPR_SCALE, null);
            }
            if (p.stoneOn) {
                BufferedImage st = (i==0)? stoneBlue : stoneRed;
                if (st!=null) g2.drawImage(st, px - (st.getWidth()*SPR_SCALE)/2, py - (st.getHeight()*SPR_SCALE)/2,
                        st.getWidth()*SPR_SCALE, st.getHeight()*SPR_SCALE, null);
            }
        }

        // กระสุน
        for (Bullet b : bullets) {
            int cx = (int)Math.round(b.x);
            int cy = (int)Math.round(b.y);
            g2.setColor(b.color);
            if (b.homing) fillDiamond(g2, cx, cy, b.diaLen, b.diaWidth, b.ang());
            else { int r=b.circleR; g2.fillOval(cx-r, cy-r, r*2, r*2); }
        }

        // Kills
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(14f));
        g2.drawString(ps[0].name + "  Kills: " + ps[0].kills + "/" + KILL_TO_WIN, 40, 50);
        String rLabel = ps[1].name + "  Kills: " + ps[1].kills + "/" + KILL_TO_WIN;
        g2.drawString(rLabel, W - 40 - g2.getFontMetrics().stringWidth(rLabel), 50);

        // HUD CD
        int pillH = 18, pillW = 205;
        int rowY1 = H - 5* (pillH+6) - 40;
        drawCooldownPanel(g2, ps[0], 40, rowY1,  pillW, pillH, true);
        drawCooldownPanel(g2, ps[1], W-40-pillu(pillW), rowY1,  pillW, pillH, false);

        // Win banner
        if (matchOver) {
            String msg = (ps[0].kills > ps[1].kills) ? "P1 WINS!" : (ps[1].isBot ? "COM WINS!" : "P2 WINS!");
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 54f));
            int w2 = g2.getFontMetrics().stringWidth(msg);
            g2.setColor(new Color(255,255,255,230));
            g2.drawString(msg, (W-w2)/2, H/2);

            g2.setFont(g2.getFont().deriveFont(18f));
            String tip = "Press R to start next game   •   ESC for Menu";
            int w3 = g2.getFontMetrics().stringWidth(tip);
            g2.drawString(tip, (W-w3)/2, H/2 + 36);
        }

        // Pause overlay
        if (paused) {
            g2.setColor(new Color(0,0,0,160));
            g2.fillRect(0,0,W,H);
            g2.setColor(Color.WHITE);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 28f));
            String t = "PAUSED";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (W-tw)/2, H/2 - 24);

            g2.setFont(g2.getFont().deriveFont(16f));
            String[] lines = { "[R] Rematch", "[M] Main Menu", "[ESC] Resume" };
            int y = H/2 + 8;
            for (String s : lines) {
                int sw = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, (W-sw)/2, y);
                y += 22;
            }
        }

        g2.dispose();
    }

    private static int pillu(int w){ return w; }

    private void drawCooldownPanel(Graphics2D g2, Player p, int startX, int startY, int pillW, int pillH, boolean leftSide){
        Skill[] order = new Skill[]{ Skill.STRAIGHT, Skill.HOMING, Skill.BLINK, Skill.REFLECT, Skill.STONE };
        String[] labels = new String[]{
                "Straight ("+p.S_STRAIGHT.toUpperCase()+")",
                "Homing ("  +p.S_HOMING.toUpperCase()+")",
                "Blink ("   +p.S_BLINK.toUpperCase()+")",
                "Reflect (" +p.S_REFLECT.toUpperCase()+")",
                "Stone ("   +p.S_STONE.toUpperCase()+")"
        };
        int y = startY;
        for (int i=0;i<order.length;i++){
            drawCooldownPill(g2, p, order[i], labels[i], leftSide? startX : startX, y, pillW, pillH, leftSide);
            y += (pillH + 6);
        }
    }

    private void drawCooldownPill(Graphics2D g2, Player p, Skill s, String label,
                                  int x, int y, int w, int h, boolean leftSide) {
        long now = System.currentTimeMillis();
        long left = p.cds.remaining(now, s);
        long total = cdOf(s);
        float ratio = total==0 ? 0f : Math.min(1f, (float)left / (float)total);

        g2.setColor(new Color(255,255,255,22));
        g2.fillRoundRect(x, y, w, h, h, h);
        g2.setColor(new Color(255,255,255,60));
        g2.drawRoundRect(x, y, w, h, h, h);

        int fillW = (int)((w-2) * (1f - ratio));
        g2.setColor(left==0 ? new Color(60,200,120) : new Color(120,120,120));
        g2.fillRoundRect(x+1, y+1, Math.max(0, fillW), h-2, h-2, h-2);

        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(12f));
        String timeTxt = (left==0) ? "READY" : String.format("%.1fs", left/1000f);
        String txt = label + "  " + timeTxt;

        int tx = leftSide ? (x + 8) : (x + w - 8 - g2.getFontMetrics().stringWidth(txt));
        int ty = y + h - 5;
        g2.drawString(txt, tx, ty);
    }

    private long cdOf(Skill s){
        return switch (s){
            case STRAIGHT -> CD_STRAIGHT;
            case HOMING   -> CD_HOMING;
            case REFLECT  -> CD_REFLECT;
            case STONE    -> CD_STONE;
            case BLINK    -> CD_BLINK;
        };
    }

    /* ----------------- Helpers ----------------- */
    private static void fillDiamond(Graphics2D g2, int cx, int cy, int len, int wid, double ang) {
        int halfW = wid/2;
        Path2D.Float poly = new Path2D.Float();
        poly.moveTo( len/2.0, 0);
        poly.lineTo( 0, -halfW);
        poly.lineTo(-len/2.0, 0);
        poly.lineTo( 0,  halfW);
        poly.closePath();

        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(ang);
        g2.fill(at.createTransformedShape(poly));
    }

    private void reflectToOther(Bullet b, int targetId) {
        int enemyId  = (targetId==0)?1:0;
        Player enemy = ps[enemyId];

        double ang = Math.atan2(enemy.y - b.y, enemy.x - b.x);
        b.vx = Math.cos(ang) * BULLET_SPEED;
        b.vy = Math.sin(ang) * BULLET_SPEED;
        b.ownerId = targetId;
    }

    private void recolorToOwner(Bullet b) {
        b.color = (b.ownerId==0) ? new Color(120,200,255) : new Color(255,170,120);
    }

    private boolean isShieldActive(Player p) {
        long now = System.currentTimeMillis();
        return now < p.reflectUntil;
    }

    private boolean nearShield(Player p, double bx, double by) {
        double cx = p.x, cy = p.y;
        double r;
        BufferedImage sh = (p == ps[0]) ? shieldBlue : shieldRed;
        if (sh != null) {
            double rw = (sh.getWidth()  * SPR_SCALE) / 2.0;
            double rh = (sh.getHeight() * SPR_SCALE) / 2.0;
            r = Math.min(rw, rh) * 0.75;
        } else {
            r = 42.0;
        }
        double dx = bx - cx, dy = by - cy;
        return Math.hypot(dx, dy) <= (r + 6.0);
    }

    private boolean bodyHit(Player p, double bx, double by) {
        double cx = p.x;
        double cy = p.y + 8 * SPR_SCALE;
        double rx = 18 * SPR_SCALE;
        double ry = 26 * SPR_SCALE;

        double dx = (bx - cx) / rx;
        double dy = (by - cy) / ry;
        return dx*dx + dy*dy <= 1.0;
    }

    /* ----------------- Casting skills ----------------- */
    private void tryCast(Player caster, int casterId, Skill s) {
        long now = System.currentTimeMillis();
        if (!caster.cds.ready(now, s)) return;
        if (caster.stoneOn && s != Skill.STONE) return;

        switch (s) {
            case STRAIGHT -> {
                Player tgt = ps[casterId==0?1:0];
                double ang = Math.atan2(tgt.y - caster.y, tgt.x - caster.x);
                Color col = (casterId==0)? new Color(120,200,255) : new Color(255,170,120);
                bullets.add(new Bullet(
                        caster.x, caster.y,
                        Math.cos(ang)*BULLET_SPEED, Math.sin(ang)*BULLET_SPEED,
                        casterId, false, col
                ));
                caster.cds.set(now, Skill.STRAIGHT, CD_STRAIGHT);
                caster.castUntil = now + 120;
                Sfx.play("shoot");
            }
            case HOMING -> {
                Player tgt = ps[casterId==0?1:0];
                double ang = Math.atan2(tgt.y - caster.y, tgt.x - caster.x);
                Color col = (casterId==0)? new Color(120,200,255) : new Color(255,170,120);
                bullets.add(new Bullet(
                        caster.x, caster.y,
                        Math.cos(ang)*BULLET_SPEED, Math.sin(ang)*BULLET_SPEED,
                        casterId, true, col
                ));
                caster.cds.set(now, Skill.HOMING, CD_HOMING);
                caster.castUntil = now + 140;
                Sfx.play("homing");
            }
            case REFLECT -> {
                caster.reflectUntil = now + DUR_REFLECT;
                caster.cds.set(now, Skill.REFLECT, CD_REFLECT);
                caster.castUntil = now + 100;
                Sfx.play("reflect");
            }
            case STONE -> {
                caster.stoneUntil = now + DUR_STONE;
                caster.cds.set(now, Skill.STONE, CD_STONE);
                caster.castUntil = now + 100;
                Sfx.play("stone");
            }
            case BLINK -> {
                Player tgt = ps[casterId==0?1:0];
                double dx = caster.vx, dy = caster.vy;
                double len = Math.hypot(dx, dy);
                if (len == 0) { dx = tgt.x - caster.x; dy = tgt.y - caster.y; len = Math.hypot(dx, dy); }
                if (len > 0) { dx/=len; dy/=len; }
                caster.x = clamp(caster.x + dx*BLINK_DISTANCE, 40, W-40);
                caster.y = clamp(caster.y + dy*BLINK_DISTANCE, 60, H-60);
                caster.blinkIFrameUntil = now + DUR_BLINK_IFRAME;
                caster.lastBlinkAt = now;
                caster.cds.set(now, Skill.BLINK, CD_BLINK);
                caster.castUntil = now + 90;
                Sfx.play("blink");
            }
        }
    }

    /* ----------------- Input ----------------- */
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        // PAUSE toggle
        if (k==KeyEvent.VK_ESCAPE) {
            if (matchOver) {
                if (onExitToMenu != null) onExitToMenu.run();
            } else {
                paused = !paused;
                repaint();
            }
            return;
        }

        if (paused) {
            if (k==KeyEvent.VK_R) { resetRound(false); paused=false; }
            else if (k==KeyEvent.VK_M) { if (onExitToMenu != null) onExitToMenu.run(); }
            return;
        }

        if (matchOver) {
            if (k==KeyEvent.VK_R) {
                matchOver = false;
                resetRound(false);
            }
            return;
        }

        if (k==KeyEvent.VK_A) ps[0].left=true;
        if (k==KeyEvent.VK_D) ps[0].right=true;
        if (k==KeyEvent.VK_W) ps[0].up=true;
        if (k==KeyEvent.VK_S) ps[0].down=true;

        if (!ps[1].isBot) {
            if (k==KeyEvent.VK_LEFT)  ps[1].left=true;
            if (k==KeyEvent.VK_RIGHT) ps[1].right=true;
            if (k==KeyEvent.VK_UP)    ps[1].up=true;
            if (k==KeyEvent.VK_DOWN)  ps[1].down=true;
        }

        onComboKey(k);

        if (k==KeyEvent.VK_R) resetRound(false);
    }
    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k==KeyEvent.VK_A) ps[0].left=false;
        if (k==KeyEvent.VK_D) ps[0].right=false;
        if (k==KeyEvent.VK_W) ps[0].up=false;
        if (k==KeyEvent.VK_S) ps[0].down=false;

        if (!ps[1].isBot) {
            if (k==KeyEvent.VK_LEFT)  ps[1].left=false;
            if (k==KeyEvent.VK_RIGHT) ps[1].right=false;
            if (k==KeyEvent.VK_UP)    ps[1].up=false;
            if (k==KeyEvent.VK_DOWN)  ps[1].down=false;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    private void onComboKey(int keyCode) {
        for (int id=0; id<2; id++) {
            Player p = ps[id];
            if (p.isBot) continue;
            if (!p.comboKeys.contains(keyCode)) continue;
            if (p.stoneOn) continue;

            char c = mapComboChar(p, keyCode);
            if (c=='\0') continue;

            p.pushCombo(c, MAX_BUFFER);
            String s = p.comboString();

            if      (s.endsWith(p.S_STRAIGHT)) { tryCast(p, id, Skill.STRAIGHT); p.clearCombo(); }
            else if (s.endsWith(p.S_HOMING))   { tryCast(p, id, Skill.HOMING);   p.clearCombo(); }
            else if (s.endsWith(p.S_BLINK))    { tryCast(p, id, Skill.BLINK);    p.clearCombo(); }
            else if (s.endsWith(p.S_REFLECT))  { tryCast(p, id, Skill.REFLECT);  p.clearCombo(); }
            else if (s.endsWith(p.S_STONE))    { tryCast(p, id, Skill.STONE);    p.clearCombo(); }
        }
    }

    private char mapComboChar(Player p, int keyCode) {
        if (p.mapJ.equals("J") && keyCode==KeyEvent.VK_J) return 'j';
        if (p.mapK.equals("K") && keyCode==KeyEvent.VK_K) return 'k';
        if (p.mapL.equals("L") && keyCode==KeyEvent.VK_L) return 'l';

        if (p.mapJ.equals("1") && (keyCode==KeyEvent.VK_NUMPAD1 || keyCode==KeyEvent.VK_1)) return '1';
        if (p.mapK.equals("2") && (keyCode==KeyEvent.VK_NUMPAD2 || keyCode==KeyEvent.VK_2)) return '2';
        if (p.mapL.equals("3") && (keyCode==KeyEvent.VK_NUMPAD3 || keyCode==KeyEvent.VK_3)) return '3';

        return '\0';
    }

    private void resetRound(boolean keepKills) {
        for (int i=0;i<2;i++) {
            ps[i].x = (i==0? W*0.25 : W*0.75);
            ps[i].y = H*0.5;
            ps[i].hp = Player.START_HP;
            ps[i].clearCombo();
            ps[i].reflectOn=ps[i].stoneOn=ps[i].blinkIFrameOn=false;
            ps[i].reflectUntil=ps[i].stoneUntil=ps[i].blinkIFrameUntil=0;
            ps[i].lastBlinkAt = 0L;
            ps[i].castUntil = 0;
            ps[i].left=ps[i].right=ps[i].up=ps[i].down=false;
            if (!keepKills) ps[i].kills = 0;
        }
        bullets.clear();
    }

    /* ----------------- Utils ----------------- */
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double normalizeAngle(double a) { while (a>Math.PI) a-=2*Math.PI; while (a<-Math.PI) a+=2*Math.PI; return a; }
}
