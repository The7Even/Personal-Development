/*
 * Decompiled with CFR 0.152.
 */
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

class OmokFrame
extends JFrame {
    OmokFrame() {
        super("\uc624\ubaa9");
        this.setDefaultCloseOperation(3);
        this.setLayout(new BorderLayout());
        JLabel jLabel = new JLabel("\uac8c\uc784 \ubaa8\ub4dc\ub97c \uc120\ud0dd\ud558\uc138\uc694", 0);
        jLabel.setFont(new Font("Dialog", 1, 16));
        RenjuGame renjuGame = new RenjuGame(jLabel);
        BoardPanel boardPanel = new BoardPanel(renjuGame);
        renjuGame.setBoardPanel(boardPanel);
        JButton jButton = new JButton("\uc0c8 \uac8c\uc784");
        jButton.addActionListener(actionEvent -> renjuGame.startNewGameWithModeSelection(this));
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.add((Component)jLabel, "Center");
        jPanel.add((Component)jButton, "East");
        this.add((Component)jPanel, "North");
        this.add((Component)boardPanel, "Center");
        this.pack();
        this.setLocationRelativeTo(null);
        renjuGame.startNewGameWithModeSelection(this);
    }
}
