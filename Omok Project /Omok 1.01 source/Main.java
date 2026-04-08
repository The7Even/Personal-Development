/*
 * Decompiled with CFR 0.152.
 */
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] stringArray) {
        SwingUtilities.invokeLater(() -> {
            OmokFrame omokFrame = new OmokFrame();
            omokFrame.setVisible(true);
        });
    }
}
