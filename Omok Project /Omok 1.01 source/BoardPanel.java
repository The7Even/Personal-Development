import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;

class BoardPanel extends JPanel {
  private static final int PADDING = 36;
  private final RenjuGame game;
  private final BufferedImage blackStoneImage;
  private final BufferedImage whiteStoneImage;

  BoardPanel(RenjuGame game) {
    this.game = game;
    this.blackStoneImage = SvgStoneLoader.loadStone("black.svg");
    this.whiteStoneImage = SvgStoneLoader.loadStone("white.svg");
    setPreferredSize(new Dimension(620, 640));
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int[] rc = toBoardCoordinate(e.getX(), e.getY());
        if (rc != null) {
          game.play(rc[0], rc[1]);
          repaint();
        }
      }
    });
  }

  private int[] toBoardCoordinate(int x, int y) {
    int boardSizePx = Math.min(getWidth(), getHeight()) - PADDING * 2;
    double cell = boardSizePx / (double) (RenjuGame.SIZE - 1);
    int col = (int) Math.round((x - PADDING) / cell);
    int row = (int) Math.round((y - PADDING) / cell);
    if (row < 0 || row >= RenjuGame.SIZE || col < 0 || col >= RenjuGame.SIZE) {
      return null;
    }
    return new int[]{row, col};
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int boardSizePx = Math.min(getWidth(), getHeight()) - PADDING * 2;
    double cell = boardSizePx / (double) (RenjuGame.SIZE - 1);

    g2.setColor(new Color(215, 176, 109));
    g2.fillRect(PADDING - 20, PADDING - 20, boardSizePx + 40, boardSizePx + 40);

    g2.setColor(Color.BLACK);
    for (int i = 0; i < RenjuGame.SIZE; i++) {
      int p = (int) Math.round(PADDING + i * cell);
      g2.drawLine(PADDING, p, PADDING + boardSizePx, p);
      g2.drawLine(p, PADDING, p, PADDING + boardSizePx);
    }

    int[][] stars = {{3, 3}, {3, 7}, {3, 11}, {7, 3}, {7, 7}, {7, 11}, {11, 3}, {11, 7}, {11, 11}};
    for (int[] s : stars) {
      int cx = (int) Math.round(PADDING + s[1] * cell);
      int cy = (int) Math.round(PADDING + s[0] * cell);
      g2.fillOval(cx - 4, cy - 4, 8, 8);
    }

    int stone = (int) Math.max(14, cell - 4);
    if (game.shouldShowOpeningRangeHints()) {
      drawOpeningRangeHints(g2, cell);
    }
    if (game.shouldShowForbiddenHints()) {
      drawForbiddenHints(g2, cell);
    }
    if (game.shouldShowSymmetryForbiddenHints()) {
      drawSymmetryForbiddenHints(g2, cell);
    }
    for (int r = 0; r < RenjuGame.SIZE; r++) {
      for (int c = 0; c < RenjuGame.SIZE; c++) {
        int v = game.board[r][c];
        if (v == RenjuGame.EMPTY) {
          continue;
        }
        int cx = (int) Math.round(PADDING + c * cell);
        int cy = (int) Math.round(PADDING + r * cell);
        BufferedImage img = (v == RenjuGame.BLACK) ? blackStoneImage : whiteStoneImage;
        if (img != null) {
          g2.drawImage(img, cx - stone / 2, cy - stone / 2, stone, stone, null);
        } else {
          g2.setColor(v == RenjuGame.BLACK ? Color.BLACK : Color.WHITE);
          g2.fillOval(cx - stone / 2, cy - stone / 2, stone, stone);
          g2.setColor(Color.BLACK);
          g2.drawOval(cx - stone / 2, cy - stone / 2, stone, stone);
        }
        drawMoveNumber(g2, r, c, v, cx, cy, stone);
      }
    }

    int[] last = game.getLastMove();
    if (last != null) {
      int cx = (int) Math.round(PADDING + last[1] * cell);
      int cy = (int) Math.round(PADDING + last[0] * cell);
      g2.setColor(new Color(220, 40, 40));
      g2.drawRect(cx - stone / 4, cy - stone / 4, stone / 2, stone / 2);
    }

    List<int[]> thinking = game.getThinkingPreviewMoves();
    if (!thinking.isEmpty()) {
      int s = Math.max(10, stone / 3);
      g2.setColor(new Color(255, 240, 40, 235));
      for (int[] p : thinking) {
        int cx = (int) Math.round(PADDING + p[1] * cell);
        int cy = (int) Math.round(PADDING + p[0] * cell);
        g2.fillRect(cx - s / 2, cy - s / 2, s, s);
      }
    }

    g2.dispose();
  }

  private void drawMoveNumber(Graphics2D g2, int row, int col, int stoneColor, int cx, int cy, int stoneSize) {
    int moveNumber = game.getMoveNumber(row, col);
    if (moveNumber <= 0) {
      return;
    }
    int fontSize = Math.max(11, stoneSize / 3);
    g2.setFont(new Font("Dialog", Font.BOLD, fontSize));
    g2.setColor(stoneColor == RenjuGame.BLACK ? Color.WHITE : Color.BLACK);
    String text = String.valueOf(moveNumber);
    FontMetrics fm = g2.getFontMetrics();
    int tx = cx - fm.stringWidth(text) / 2;
    int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
    g2.drawString(text, tx, ty);
  }

  private void drawForbiddenHints(Graphics2D g2, double cell) {
    int xSize = (int) Math.max(8, cell / 3);
    g2.setStroke(new BasicStroke(2f));
    g2.setColor(new Color(220, 30, 30, 200));
    for (int r = 0; r < RenjuGame.SIZE; r++) {
      for (int c = 0; c < RenjuGame.SIZE; c++) {
        if (!game.isForbiddenCellForBlack(r, c)) {
          continue;
        }
        int cx = (int) Math.round(PADDING + c * cell);
        int cy = (int) Math.round(PADDING + r * cell);
        g2.drawLine(cx - xSize, cy - xSize, cx + xSize, cy + xSize);
        g2.drawLine(cx - xSize, cy + xSize, cx + xSize, cy - xSize);
      }
    }
  }

  private void drawSymmetryForbiddenHints(Graphics2D g2, double cell) {
    int xSize = (int) Math.max(8, cell / 3);
    g2.setStroke(new BasicStroke(2f));
    g2.setColor(new Color(40, 90, 220, 210));
    for (int r = 0; r < RenjuGame.SIZE; r++) {
      for (int c = 0; c < RenjuGame.SIZE; c++) {
        if (!game.isSymmetryForbiddenCellForSecondFifth(r, c)) {
          continue;
        }
        int cx = (int) Math.round(PADDING + c * cell);
        int cy = (int) Math.round(PADDING + r * cell);
        g2.drawLine(cx - xSize, cy - xSize, cx + xSize, cy + xSize);
        g2.drawLine(cx - xSize, cy + xSize, cx + xSize, cy - xSize);
      }
    }
  }

  private void drawOpeningRangeHints(Graphics2D g2, double cell) {
    int radius = (int) Math.max(6, cell / 4);
    g2.setStroke(new BasicStroke(2f));
    g2.setColor(new Color(20, 140, 70, 200));
    for (int r = 0; r < RenjuGame.SIZE; r++) {
      for (int c = 0; c < RenjuGame.SIZE; c++) {
        if (!game.isOpeningRangeCell(r, c)) {
          continue;
        }
        int cx = (int) Math.round(PADDING + c * cell);
        int cy = (int) Math.round(PADDING + r * cell);
        g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
      }
    }
  }
}
