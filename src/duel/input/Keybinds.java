package duel.input;

import java.awt.event.KeyEvent;
import java.util.Set;

public class Keybinds {
    public static final Set<Integer> P1_MOVE = Set.of(
            KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D);
    public static final Set<Integer> P1_COMBO = Set.of(
            KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_L);

    public static final Set<Integer> P2_MOVE = Set.of(
            KeyEvent.VK_UP, KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT);
    public static final Set<Integer> P2_COMBO = Set.of(
            KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
            KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD3);
}
