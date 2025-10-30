package duel;

import javax.swing.*;
import java.awt.*;

public class MenuPanel extends JPanel {
    private Image bgMenu;

    public MenuPanel(Runnable onPvp, Runnable onPvc) {
        setPreferredSize(new Dimension(GamePanel.W, GamePanel.H));
        setLayout(new GridBagLayout());

        // โหลดพื้นหลังเมนู
        try {
            bgMenu = Assets.img("/duel/resources/Menu1.png");
        } catch (Throwable t) {
            bgMenu = new ImageIcon("src/duel/resources/Menu1.png").getImage();
        }

        GridBagConstraints c = new GridBagConstraints();
        c.gridx=0; c.fill=GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 0, 10, 0);

        JLabel title = new JLabel("Take Maho", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 40f));

        JButton pvp = new JButton("Play vs Player");
        JButton pvc = new JButton("Play vs COM");

        pvp.addActionListener(e -> onPvp.run());
        pvc.addActionListener(e -> onPvc.run());

        c.gridy=0; add(title, c);
        c.gridy=1; add(pvp, c);
        c.gridy=2; add(pvc, c);

        setOpaque(false); // ให้พื้นหลังวาดเอง
    }

    public MenuPanel(App app) {
        this(
                () -> app.showGame(GameMode.PVP),
                () -> app.showGame(GameMode.PVC)
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        // วาดพื้นหลังเมนูให้เต็มจอ
        if (bgMenu != null) {
            g.drawImage(bgMenu, 0, 0, getWidth(), getHeight(), this);
        } else {
            g.setColor(new Color(18,20,26));
            g.fillRect(0,0,getWidth(),getHeight());
        }
        super.paintComponent(g);
    }
}
