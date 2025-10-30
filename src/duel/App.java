package duel;

import javax.swing.*;
import java.awt.*;

public class App extends JFrame {
    private final JPanel root = new JPanel(new CardLayout());
    private MenuPanel menuPanel;
    private GamePanel game;

    public App() {
        super("Take Maho");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        menuPanel = new MenuPanel(this);
        root.add(menuPanel, "menu");

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);

        showMenu();
    }

    public void showMenu() {
        ((CardLayout)root.getLayout()).show(root, "menu");
        root.requestFocusInWindow();
        pack();
    }

    public void showGame(GameMode mode) {
        if (game != null) root.remove(game);
        game = new GamePanel(mode, this::showMenu);
        root.add(game, "game");
        ((CardLayout)root.getLayout()).show(root, "game");
        game.requestFocusInWindow();
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().setVisible(true));
    }
}
