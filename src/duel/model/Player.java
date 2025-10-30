package duel.model;

import java.awt.*;
import java.util.*;

public class Player {
    public static final int START_HP = 1;

    public final String name;
    public double x,y, vx,vy;
    public boolean left,right,up,down;
    public boolean facingRight = true;

    public int hp = START_HP;
    public int kills = 0;

    public boolean isBot = false;

    public final Cooldowns cds = new Cooldowns();

    // states
    public boolean reflectOn=false, stoneOn=false, blinkIFrameOn=false;
    public long reflectUntil=0, stoneUntil=0, blinkIFrameUntil=0, lastBlinkAt=0;
    public long castUntil=0;
    public double animPhase = 0;

    // combo input
    public final Set<Integer> comboKeys;
    private final Deque<Character> buffer = new ArrayDeque<>();
    public final String S_STRAIGHT, S_HOMING, S_BLINK, S_REFLECT, S_STONE;

    // key labels on HUD
    public final String mapJ, mapK, mapL, mapI;

    // colors (unused by HUD here, but handy)
    public final Color mainColor, ghostColor;

    public Player(String name, double x, double y,
                  Set<Integer> moveKeys, Set<Integer> comboKeys,
                  String mapJ, String mapK, String mapL, String mapI,
                  String straight, String homing, String blink, String reflect, String stone,
                  Color mainColor, Color ghostColor) {
        this.name = name;
        this.x = x; this.y = y;
        this.comboKeys = comboKeys;
        this.S_STRAIGHT = straight;
        this.S_HOMING   = homing;
        this.S_BLINK    = blink;
        this.S_REFLECT  = reflect;
        this.S_STONE    = stone;
        this.mapJ = mapJ; this.mapK = mapK; this.mapL = mapL; this.mapI = mapI;
        this.mainColor = mainColor; this.ghostColor = ghostColor;
    }

    public void pushCombo(char c, int max) {
        buffer.addLast(c);
        while (buffer.size() > max) buffer.removeFirst();
    }
    public String comboString() {
        StringBuilder sb = new StringBuilder(buffer.size());
        for (char c : buffer) sb.append(c);
        return sb.toString();
    }
    public void clearCombo() { buffer.clear(); }
}
