import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      OmokFrame frame = new OmokFrame();
      frame.setVisible(true);
    });
  }
}

class OmokFrame extends JFrame {
  OmokFrame() {
    super("오목");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

    JLabel statusLabel = new JLabel("게임 모드를 선택하세요", SwingConstants.CENTER);
    statusLabel.setFont(new Font("Dialog", Font.BOLD, 16));

    RenjuGame game = new RenjuGame(statusLabel);
    BoardPanel boardPanel = new BoardPanel(game);
    game.setBoardPanel(boardPanel);

    JButton resetButton = new JButton("새 게임");
    resetButton.addActionListener(e -> game.startNewGameWithModeSelection(this));

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(statusLabel, BorderLayout.CENTER);
    topPanel.add(resetButton, BorderLayout.EAST);

    add(topPanel, BorderLayout.NORTH);
    add(boardPanel, BorderLayout.CENTER);

    pack();
    setLocationRelativeTo(null);
    game.startNewGameWithModeSelection(this);
  }
}

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

class SvgStoneLoader {
  static BufferedImage loadStone(String path) {
    try {
      File file = new File(path);
      if (!file.exists()) {
        return null;
      }
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(false);
      Document doc = dbf.newDocumentBuilder().parse(file);
      Element root = doc.getDocumentElement();

      int width = parseLength(root.getAttribute("width"), 500);
      int height = parseLength(root.getAttribute("height"), 500);
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Map<String, LinearGradientDef> linearDefs = new HashMap<>();
      Map<String, RadialGradientDef> radialDefs = new HashMap<>();
      parseGradientDefs(doc, linearDefs, radialDefs);

      NodeList paths = root.getElementsByTagName("path");
      for (int i = 0; i < paths.getLength(); i++) {
        Element pathEl = (Element) paths.item(i);
        drawPath(g2, pathEl, linearDefs, radialDefs);
      }
      g2.dispose();
      return image;
    } catch (Exception e) {
      return tryRasterLoad(path);
    }
  }

  private static BufferedImage tryRasterLoad(String path) {
    try {
      return ImageIO.read(new File(path));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void parseGradientDefs(
      Document doc,
      Map<String, LinearGradientDef> linearDefs,
      Map<String, RadialGradientDef> radialDefs
  ) {
    NodeList linear = doc.getElementsByTagName("linearGradient");
    for (int i = 0; i < linear.getLength(); i++) {
      Element el = (Element) linear.item(i);
      String id = el.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      NodeList stops = el.getElementsByTagName("stop");
      List<Float> fractions = new ArrayList<>();
      List<Color> colors = new ArrayList<>();
      for (int s = 0; s < stops.getLength(); s++) {
        Element stopEl = (Element) stops.item(s);
        Map<String, String> style = parseStyle(stopEl.getAttribute("style"));
        float offset = parseFraction(stopEl.getAttribute("offset"));
        String colorHex = style.getOrDefault("stop-color", "#000000");
        float alpha = parseFloat(style.getOrDefault("stop-opacity", "1"), 1f);
        Color c = parseColor(colorHex, alpha);
        fractions.add(offset);
        colors.add(c);
      }
      if (!fractions.isEmpty()) {
        linearDefs.put(id, new LinearGradientDef(toFloatArray(fractions), colors.toArray(new Color[0])));
      }
    }

    NodeList radial = doc.getElementsByTagName("radialGradient");
    for (int i = 0; i < radial.getLength(); i++) {
      Element el = (Element) radial.item(i);
      String id = el.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      String href = el.getAttribute("xlink:href");
      if (href.isEmpty()) {
        href = el.getAttribute("href");
      }
      if (href.startsWith("#")) {
        href = href.substring(1);
      }
      float cx = parseFloat(el.getAttribute("cx"), 0f);
      float cy = parseFloat(el.getAttribute("cy"), 0f);
      float fx = parseFloat(el.getAttribute("fx"), cx);
      float fy = parseFloat(el.getAttribute("fy"), cy);
      float r = parseFloat(el.getAttribute("r"), 1f);
      AffineTransform at = parseMatrix(el.getAttribute("gradientTransform"));
      radialDefs.put(id, new RadialGradientDef(href, cx, cy, fx, fy, r, at));
    }
  }

  private static void drawPath(
      Graphics2D g2,
      Element pathEl,
      Map<String, LinearGradientDef> linearDefs,
      Map<String, RadialGradientDef> radialDefs
  ) {
    float cx = parseFloat(pathEl.getAttribute("sodipodi:cx"), Float.NaN);
    float cy = parseFloat(pathEl.getAttribute("sodipodi:cy"), Float.NaN);
    float rx = parseFloat(pathEl.getAttribute("sodipodi:rx"), Float.NaN);
    float ry = parseFloat(pathEl.getAttribute("sodipodi:ry"), Float.NaN);
    if (Float.isNaN(cx) || Float.isNaN(cy) || Float.isNaN(rx) || Float.isNaN(ry)) {
      return;
    }

    Map<String, String> style = parseStyle(pathEl.getAttribute("style"));
    float opacity = parseFloat(style.getOrDefault("opacity", "1"), 1f);
    float fillOpacity = parseFloat(style.getOrDefault("fill-opacity", "1"), 1f);
    float alphaMultiplier = opacity * fillOpacity;

    String fill = style.getOrDefault("fill", "");
    Paint paint = null;
    if (fill.startsWith("url(#") && fill.endsWith(")")) {
      String gradientId = fill.substring(5, fill.length() - 1);
      RadialGradientDef rg = radialDefs.get(gradientId);
      if (rg != null) {
        LinearGradientDef lg = linearDefs.get(rg.linearRefId);
        if (lg != null && lg.fractions.length >= 2) {
          Color[] adjusted = applyAlpha(lg.colors, alphaMultiplier);
          paint = new RadialGradientPaint(
              new Point2D.Float(rg.cx, rg.cy),
              rg.r,
              new Point2D.Float(rg.fx, rg.fy),
              lg.fractions,
              adjusted,
              RadialGradientPaint.CycleMethod.NO_CYCLE,
              RadialGradientPaint.ColorSpaceType.SRGB,
              rg.gradientTransform
          );
        }
      }
    } else if (fill.startsWith("#")) {
      paint = parseColor(fill, alphaMultiplier);
    }

    if (paint == null) {
      return;
    }

    Shape ellipse = new Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f);
    AffineTransform pathTransform = parseMatrix(pathEl.getAttribute("transform"));
    Shape shape = pathTransform.createTransformedShape(ellipse);
    g2.setPaint(paint);
    g2.fill(shape);
  }

  private static int parseLength(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String cleaned = value.replaceAll("[^0-9.\\-]", "");
    if (cleaned.isEmpty()) {
      return fallback;
    }
    try {
      return Math.max(1, Math.round(Float.parseFloat(cleaned)));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static float parseFraction(String offset) {
    if (offset == null || offset.isBlank()) {
      return 0f;
    }
    String s = offset.trim();
    if (s.endsWith("%")) {
      return clamp01(parseFloat(s.substring(0, s.length() - 1), 0f) / 100f);
    }
    return clamp01(parseFloat(s, 0f));
  }

  private static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  private static float parseFloat(String value, float fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Float.parseFloat(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static AffineTransform parseMatrix(String transform) {
    if (transform == null || transform.isBlank()) {
      return new AffineTransform();
    }
    String t = transform.trim();
    if (!t.startsWith("matrix(") || !t.endsWith(")")) {
      return new AffineTransform();
    }
    String[] parts = t.substring(7, t.length() - 1).split(",");
    if (parts.length != 6) {
      return new AffineTransform();
    }
    double[] m = new double[6];
    for (int i = 0; i < 6; i++) {
      try {
        m[i] = Double.parseDouble(parts[i].trim());
      } catch (NumberFormatException e) {
        return new AffineTransform();
      }
    }
    return new AffineTransform(m);
  }

  private static Map<String, String> parseStyle(String style) {
    Map<String, String> map = new HashMap<>();
    if (style == null || style.isBlank()) {
      return map;
    }
    String[] parts = style.split(";");
    for (String part : parts) {
      int idx = part.indexOf(':');
      if (idx <= 0 || idx >= part.length() - 1) {
        continue;
      }
      map.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
    }
    return map;
  }

  private static Color parseColor(String hex, float alphaMultiplier) {
    String s = hex.trim();
    if (!s.startsWith("#")) {
      return new Color(0, 0, 0, Math.round(255 * clamp01(alphaMultiplier)));
    }
    if (s.length() == 4) {
      int r = Integer.parseInt(s.substring(1, 2) + s.substring(1, 2), 16);
      int g = Integer.parseInt(s.substring(2, 3) + s.substring(2, 3), 16);
      int b = Integer.parseInt(s.substring(3, 4) + s.substring(3, 4), 16);
      int a = Math.round(255 * clamp01(alphaMultiplier));
      return new Color(r, g, b, a);
    }
    int rgb = Integer.parseInt(s.substring(1), 16);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int a = Math.round(255 * clamp01(alphaMultiplier));
    return new Color(r, g, b, a);
  }

  private static Color[] applyAlpha(Color[] colors, float alphaMultiplier) {
    Color[] out = new Color[colors.length];
    for (int i = 0; i < colors.length; i++) {
      int a = Math.round(colors[i].getAlpha() * clamp01(alphaMultiplier));
      out[i] = new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), a);
    }
    return out;
  }

  private static float[] toFloatArray(List<Float> values) {
    float[] arr = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
      arr[i] = values.get(i);
    }
    return arr;
  }

  private record LinearGradientDef(float[] fractions, Color[] colors) {}

  private record RadialGradientDef(
      String linearRefId,
      float cx,
      float cy,
      float fx,
      float fy,
      float r,
      AffineTransform gradientTransform
  ) {}
}

class RenjuGame {
  static final int SIZE = 15;
  static final int EMPTY = 0;
  static final int BLACK = 1;
  static final int WHITE = 2;
  private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
  private static final int CENTER = SIZE / 2;

  private enum RuleMode {
    FREESTYLE,
    RENJU,
    RENJU_ADVANCED
  }

  private enum AdvancedPhase {
    NONE,
    OPENING_1_BLACK_CENTER,
    OPENING_2_WHITE_AROUND_CENTER,
    OPENING_3_BLACK_WITHIN_TWO,
    OPENING_4_WHITE_FOURTH,
    OPENING_5_BLACK_FIRST,
    OPENING_5_BLACK_SECOND,
    OPENING_5_WHITE_REMOVE,
    NORMAL
  }

  final int[][] board = new int[SIZE][SIZE];
  final int[][] moveOrder = new int[SIZE][SIZE];
  private final JLabel statusLabel;
  private BoardPanel boardPanel;
  private boolean blackTurn = true;
  private boolean gameOver = false;
  private int moveCount = 0;
  private int[] lastMove = null;
  private RuleMode mode = RuleMode.RENJU;
  private AdvancedPhase advancedPhase = AdvancedPhase.NONE;
  private final List<int[]> fifthCandidates = new ArrayList<>();
  private final Random random = new Random();
  private boolean computerEnabled = false;
  private int computerDifficulty = 5;
  private int playerColor = BLACK;
  private int computerColor = WHITE;
  private boolean computerThinking = false;

  RenjuGame(JLabel statusLabel) {
    this.statusLabel = statusLabel;
  }

  void setComputerEnabled(boolean enabled) {
    this.computerEnabled = enabled;
    if (!enabled) {
      computerColor = WHITE;
    } else {
      computerColor = (playerColor == BLACK) ? WHITE : BLACK;
    }
  }

  void setComputerDifficulty(int difficulty) {
    this.computerDifficulty = Math.max(1, Math.min(10, difficulty));
  }

  void setPlayerColor(int color) {
    if (color != BLACK && color != WHITE) {
      return;
    }
    playerColor = color;
    computerColor = (playerColor == BLACK) ? WHITE : BLACK;
  }

  void tryComputerMove() {
    if (computerThinking) {
      return;
    }
    computerThinking = true;
    try {
    while (shouldComputerMoveNow()) {
      int[] move = pickComputerMove();
      if (move == null) {
        break;
      }
      play(move[0], move[1], true);
    }
    } finally {
      computerThinking = false;
    }
  }

  void startNewGameWithModeSelection(JFrame parent) {
    JComboBox<String> ruleCombo = new JComboBox<>(new String[]{"무규칙", "렌주", "렌주 보강룰"});
    JCheckBox vsComputerCheckBox = new JCheckBox("컴퓨터와 두기", computerEnabled);
    JComboBox<String> playerColorCombo = new JComboBox<>(new String[]{"플레이어: 흑", "플레이어: 백"});
    playerColorCombo.setSelectedIndex(playerColor == BLACK ? 0 : 1);
    JSlider difficultySlider = new JSlider(SwingConstants.HORIZONTAL, 1, 10, computerDifficulty);
    difficultySlider.setMajorTickSpacing(1);
    difficultySlider.setPaintTicks(true);
    difficultySlider.setPaintLabels(true);

    ruleCombo.setSelectedIndex(mode == RuleMode.FREESTYLE ? 0 : (mode == RuleMode.RENJU ? 1 : 2));
    playerColorCombo.setEnabled(computerEnabled);
    difficultySlider.setEnabled(computerEnabled);
    vsComputerCheckBox.addActionListener(e -> {
      boolean enabled = vsComputerCheckBox.isSelected();
      playerColorCombo.setEnabled(enabled);
      difficultySlider.setEnabled(enabled);
    });

    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
    panel.add(new JLabel("규칙 선택"));
    panel.add(ruleCombo);
    panel.add(vsComputerCheckBox);
    panel.add(playerColorCombo);
    panel.add(new JLabel("컴퓨터 난이도 (1~10)"));
    panel.add(difficultySlider);

    int result = JOptionPane.showConfirmDialog(
        parent,
        panel,
        "게임 시작 설정",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    );
    if (result != JOptionPane.OK_OPTION) {
      return;
    }

    int choice = ruleCombo.getSelectedIndex();
    if (choice == 0) {
      mode = RuleMode.FREESTYLE;
    } else if (choice == 2) {
      mode = RuleMode.RENJU_ADVANCED;
    } else {
      mode = RuleMode.RENJU;
    }
    setComputerEnabled(vsComputerCheckBox.isSelected());
    setPlayerColor(playerColorCombo.getSelectedIndex() == 0 ? BLACK : WHITE);
    setComputerDifficulty(difficultySlider.getValue());
    reset();
  }

  void setBoardPanel(BoardPanel boardPanel) {
    this.boardPanel = boardPanel;
  }

  void reset() {
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        board[r][c] = EMPTY;
        moveOrder[r][c] = 0;
      }
    }
    blackTurn = true;
    gameOver = false;
    moveCount = 0;
    lastMove = null;
    computerColor = (playerColor == BLACK) ? WHITE : BLACK;
    fifthCandidates.clear();
    if (mode == RuleMode.RENJU_ADVANCED) {
      advancedPhase = AdvancedPhase.OPENING_1_BLACK_CENTER;
      statusLabel.setText("[렌주 보강룰] 흑 1수: 천원(중앙)에 두세요.");
    } else {
      advancedPhase = AdvancedPhase.NONE;
      statusLabel.setText(blackTurn ? "흑 차례" : "백 차례");
    }
    repaintBoard();
    tryComputerMove();
  }

  int[] getLastMove() {
    return lastMove;
  }

  int getMoveNumber(int row, int col) {
    return moveOrder[row][col];
  }

  boolean shouldShowForbiddenHints() {
    if (gameOver || !blackTurn || !isRenjuStyle()) {
      return false;
    }
    if (mode == RuleMode.RENJU_ADVANCED) {
      return advancedPhase == AdvancedPhase.NORMAL;
    }
    return true;
  }

  boolean shouldShowOpeningRangeHints() {
    return mode == RuleMode.RENJU_ADVANCED
        && !gameOver
        && (advancedPhase == AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER
        || advancedPhase == AdvancedPhase.OPENING_3_BLACK_WITHIN_TWO);
  }

  boolean isOpeningRangeCell(int row, int col) {
    if (!shouldShowOpeningRangeHints() || !inRange(row, col) || board[row][col] != EMPTY) {
      return false;
    }
    if (advancedPhase == AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER) {
      return isAroundCenter8(row, col);
    }
    return isWithinCenterFiveByFive(row, col);
  }

  boolean shouldShowSymmetryForbiddenHints() {
    return mode == RuleMode.RENJU_ADVANCED
        && !gameOver
        && advancedPhase == AdvancedPhase.OPENING_5_BLACK_SECOND
        && fifthCandidates.size() == 1;
  }

  boolean isForbiddenCellForBlack(int row, int col) {
    if (!isRenjuStyle()) {
      return false;
    }
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return false;
    }
    board[row][col] = BLACK;
    boolean forbidden;
    if (isOverline(row, col, BLACK)) {
      forbidden = true;
    } else if (isExactFive(row, col, BLACK)) {
      forbidden = false;
    } else {
      forbidden = isForbiddenByRenju(row, col);
    }
    board[row][col] = EMPTY;
    return forbidden;
  }

  boolean isSymmetryForbiddenCellForSecondFifth(int row, int col) {
    if (!shouldShowSymmetryForbiddenHints()) {
      return false;
    }
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return false;
    }
    return isSecondFifthSymmetryEquivalent(row, col);
  }

  void play(int row, int col) {
    play(row, col, false);
  }

  private void play(int row, int col, boolean fromComputer) {
    if (gameOver || !inRange(row, col)) {
      return;
    }
    if (!fromComputer && shouldComputerMoveNow()) {
      return;
    }
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
      handleAdvancedRemoveChoice(row, col);
      return;
    }
    if (board[row][col] != EMPTY) {
      return;
    }
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase != AdvancedPhase.NONE && advancedPhase != AdvancedPhase.NORMAL) {
      handleAdvancedOpeningPlacement(row, col);
      return;
    }
    placeStandardMove(row, col);
  }

  private boolean shouldComputerMoveNow() {
    if (!computerEnabled || gameOver) {
      return false;
    }
    return getCurrentTurnColor() == computerColor;
  }

  private int getCurrentTurnColor() {
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
      return WHITE;
    }
    return blackTurn ? BLACK : WHITE;
  }

  private int[] pickComputerMove() {
    List<int[]> legal = getLegalMovesForCurrentTurn();
    if (legal.isEmpty()) {
      return null;
    }

    int turnColor = getCurrentTurnColor();
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
      return pickRemoveCandidateMove();
    }

    int[] winNow = findImmediateWinningMove(legal, turnColor);
    if (winNow != null) {
      return winNow;
    }
    int[] blockNow = findImmediateWinningMove(legal, turnColor == BLACK ? WHITE : BLACK);
    if (blockNow != null) {
      return blockNow;
    }

    int bestScore = Integer.MIN_VALUE;
    List<int[]> ranked = new ArrayList<>();
    List<Integer> scores = new ArrayList<>();
    for (int[] mv : legal) {
      int score = evaluateMove(mv[0], mv[1], turnColor);
      scores.add(score);
      ranked.add(mv);
      bestScore = Math.max(bestScore, score);
    }

    if (ranked.isEmpty()) {
      return null;
    }

    List<int[]> best = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      if (scores.get(i) == bestScore) {
        best.add(ranked.get(i));
      }
    }
    if (!best.isEmpty()) {
      return best.get(random.nextInt(best.size()));
    }

    int topBand = Math.max(1, 7 - (computerDifficulty / 2));
    int threshold = bestScore - (topBand * 30);
    List<int[]> candidateBand = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      if (scores.get(i) >= threshold) {
        candidateBand.add(ranked.get(i));
      }
    }
    if (candidateBand.isEmpty()) {
      candidateBand = ranked;
    }
    return candidateBand.get(random.nextInt(candidateBand.size()));
  }

  private List<int[]> getLegalMovesForCurrentTurn() {
    List<int[]> moves = new ArrayList<>();
    int color = getCurrentTurnColor();
    if (mode == RuleMode.RENJU_ADVANCED) {
      switch (advancedPhase) {
        case OPENING_1_BLACK_CENTER -> {
          if (board[CENTER][CENTER] == EMPTY) {
            moves.add(new int[]{CENTER, CENTER});
          }
          return moves;
        }
        case OPENING_2_WHITE_AROUND_CENTER -> {
          for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
              if (board[r][c] == EMPTY && isAroundCenter8(r, c)) {
                moves.add(new int[]{r, c});
              }
            }
          }
          return moves;
        }
        case OPENING_3_BLACK_WITHIN_TWO -> {
          for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
              if (board[r][c] == EMPTY && isWithinCenterFiveByFive(r, c)) {
                moves.add(new int[]{r, c});
              }
            }
          }
          return moves;
        }
        case OPENING_4_WHITE_FOURTH, OPENING_5_BLACK_FIRST -> {
        }
        case OPENING_5_BLACK_SECOND -> {
          for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
              if (board[r][c] != EMPTY) {
                continue;
              }
              if (!isSecondFifthSymmetryEquivalent(r, c)) {
                moves.add(new int[]{r, c});
              }
            }
          }
          return moves;
        }
        case OPENING_5_WHITE_REMOVE -> {
          for (int[] p : fifthCandidates) {
            moves.add(new int[]{p[0], p[1]});
          }
          return moves;
        }
        case NONE, NORMAL -> {
        }
      }
    }

    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] != EMPTY) {
          continue;
        }
        if (color == BLACK && isRenjuStyle() && (mode != RuleMode.RENJU_ADVANCED || advancedPhase == AdvancedPhase.NORMAL)) {
          if (isForbiddenCellForBlack(r, c)) {
            continue;
          }
        }
        moves.add(new int[]{r, c});
      }
    }
    return moves;
  }

  private int[] pickRemoveCandidateMove() {
    if (fifthCandidates.size() != 2) {
      return null;
    }
    int[] a = fifthCandidates.get(0);
    int[] b = fifthCandidates.get(1);
    int scoreA = localShapeScore(a[0], a[1], BLACK);
    int scoreB = localShapeScore(b[0], b[1], BLACK);
    int target = scoreA >= scoreB ? 0 : 1;
    if (random.nextInt(10) >= computerDifficulty) {
      target = 1 - target;
    }
    int[] pick = fifthCandidates.get(target);
    return new int[]{pick[0], pick[1]};
  }

  private int[] findImmediateWinningMove(List<int[]> legal, int color) {
    for (int[] mv : legal) {
      if (isWinningIfPlaced(mv[0], mv[1], color)) {
        return mv;
      }
    }
    return null;
  }

  private boolean isWinningIfPlaced(int row, int col, int color) {
    int prev = board[row][col];
    board[row][col] = color;
    boolean win;
    if (color == BLACK && isRenjuStyle() && (mode != RuleMode.RENJU_ADVANCED || advancedPhase == AdvancedPhase.NORMAL)) {
      win = !isOverline(row, col, BLACK) && isExactFive(row, col, BLACK) && !isForbiddenByRenju(row, col);
    } else {
      win = isFiveOrMore(row, col, color);
    }
    board[row][col] = prev;
    return win;
  }

  private int evaluateMove(int row, int col, int color) {
    int enemy = (color == BLACK) ? WHITE : BLACK;
    int ownLine = localShapeScore(row, col, color);
    int ownOpen = localOpenEndedScore(row, col, color);
    int blockLine = localShapeScore(row, col, enemy);
    int blockOpen = localOpenEndedScore(row, col, enemy);
    int nearby = adjacentStoneCount(row, col, color) + adjacentStoneCount(row, col, enemy);
    int centerBias = 14 - (Math.abs(row - CENTER) + Math.abs(col - CENTER));
    return ownLine * 140 + ownOpen * 45 + blockLine * 110 + blockOpen * 35 + nearby * 8 + centerBias;
  }

  private int localShapeScore(int row, int col, int color) {
    int prev = board[row][col];
    board[row][col] = color;
    int best = 0;
    for (int[] d : DIRS) {
      int len = countContinuous(row, col, d[0], d[1], color);
      best = Math.max(best, len);
    }
    board[row][col] = prev;
    return best;
  }

  private int localOpenEndedScore(int row, int col, int color) {
    int prev = board[row][col];
    board[row][col] = color;
    int score = 0;
    for (int[] d : DIRS) {
      int forward = countOneDirection(row, col, d[0], d[1], color);
      int backward = countOneDirection(row, col, -d[0], -d[1], color);
      int len = forward + backward + 1;
      boolean openForward = isOpenEnd(row, col, d[0], d[1], forward, color);
      boolean openBackward = isOpenEnd(row, col, -d[0], -d[1], backward, color);
      int openEnds = (openForward ? 1 : 0) + (openBackward ? 1 : 0);
      if (len >= 4 && openEnds >= 1) {
        score += 7;
      } else if (len == 3 && openEnds == 2) {
        score += 5;
      } else if (len == 3 && openEnds == 1) {
        score += 3;
      } else if (len == 2 && openEnds == 2) {
        score += 2;
      }
    }
    board[row][col] = prev;
    return score;
  }

  private boolean isOpenEnd(int row, int col, int dr, int dc, int continuousCount, int color) {
    int r = row + dr * (continuousCount + 1);
    int c = col + dc * (continuousCount + 1);
    if (!inRange(r, c)) {
      return false;
    }
    return board[r][c] == EMPTY;
  }

  private int adjacentStoneCount(int row, int col, int color) {
    int count = 0;
    for (int dr = -1; dr <= 1; dr++) {
      for (int dc = -1; dc <= 1; dc++) {
        if (dr == 0 && dc == 0) {
          continue;
        }
        int nr = row + dr;
        int nc = col + dc;
        if (inRange(nr, nc) && board[nr][nc] == color) {
          count++;
        }
      }
    }
    return count;
  }

  private void placeStandardMove(int row, int col) {
    int[] previousLastMove = lastMove == null ? null : new int[]{lastMove[0], lastMove[1]};
    int previousMoveCount = moveCount;
    int color = blackTurn ? BLACK : WHITE;
    board[row][col] = color;
    moveOrder[row][col] = ++moveCount;
    lastMove = new int[]{row, col};

    if (color == BLACK && isRenjuStyle()) {
      if (isOverline(row, col, BLACK)) {
        rollbackMove(row, col, previousMoveCount, previousLastMove);
        JOptionPane.showMessageDialog(boardPanel, "장목(6목 이상)은 흑 금수입니다.");
        return;
      }
      if (isExactFive(row, col, BLACK)) {
        gameOver = true;
        statusLabel.setText("흑 승리");
        JOptionPane.showMessageDialog(boardPanel, "흑 승리");
        return;
      }
      if (isForbiddenByRenju(row, col)) {
        rollbackMove(row, col, previousMoveCount, previousLastMove);
        JOptionPane.showMessageDialog(boardPanel, "흑 금수입니다. (3-3 또는 4-4)");
        return;
      }
    } else if (color == BLACK) {
      if (isFiveOrMore(row, col, BLACK)) {
        gameOver = true;
        statusLabel.setText("흑 승리");
        JOptionPane.showMessageDialog(boardPanel, "흑 승리");
        return;
      }
    } else {
      if (isFiveOrMore(row, col, WHITE)) {
        gameOver = true;
        statusLabel.setText("백 승리");
        JOptionPane.showMessageDialog(boardPanel, "백 승리");
        return;
      }
    }

    blackTurn = !blackTurn;
    updateTurnStatus();
    repaintBoard();
    tryComputerMove();
  }

  private void handleAdvancedOpeningPlacement(int row, int col) {
    switch (advancedPhase) {
      case OPENING_1_BLACK_CENTER -> {
        if (row != CENTER || col != CENTER) {
          statusLabel.setText("[렌주 보강룰] 흑 1수는 천원(중앙)에 둬야 합니다.");
          repaintBoard();
          return;
        }
        placeForcedMove(row, col, BLACK, 1);
        blackTurn = false;
        advancedPhase = AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER;
        statusLabel.setText("[렌주 보강룰] 백 2수: 중앙 주변 8곳 중 하나에 두세요.");
      }
      case OPENING_2_WHITE_AROUND_CENTER -> {
        if (!isAroundCenter8(row, col)) {
          statusLabel.setText("[렌주 보강룰] 백 2수는 중앙 주변 8곳 중 하나여야 합니다.");
          repaintBoard();
          return;
        }
        placeForcedMove(row, col, WHITE, 2);
        blackTurn = true;
        advancedPhase = AdvancedPhase.OPENING_3_BLACK_WITHIN_TWO;
        statusLabel.setText("[렌주 보강룰] 흑 3수: 중앙 기준 5x5 범위(1·2수 제외)에 두세요.");
      }
      case OPENING_3_BLACK_WITHIN_TWO -> {
        if (!isWithinCenterFiveByFive(row, col)) {
          statusLabel.setText("[렌주 보강룰] 흑 3수는 중앙 기준 5x5 범위(1·2수 제외)여야 합니다.");
          repaintBoard();
          return;
        }
        placeForcedMove(row, col, BLACK, 3);
        repaintBoardNow();
        boolean swap;
        if (isComputerControllingColor(WHITE)) {
          swap = shouldComputerSwapAfterThirdMove();
        } else {
          int answer = JOptionPane.showConfirmDialog(
              boardPanel,
              "백이 흑/백 교체(스왑)하시겠습니까?",
              "렌주 보강룰 - 스왑",
              JOptionPane.YES_NO_OPTION
          );
          swap = (answer == JOptionPane.YES_OPTION);
        }
        if (swap) {
          if (!isComputerControllingColor(WHITE)) {
            JOptionPane.showMessageDialog(
                boardPanel,
                "플레이어의 흑/백 역할만 교체됩니다. 돌 색은 유지됩니다."
            );
          }
          int prevPlayer = playerColor;
          playerColor = (prevPlayer == BLACK) ? WHITE : BLACK;
          computerColor = (playerColor == BLACK) ? WHITE : BLACK;
        }
        blackTurn = false;
        advancedPhase = AdvancedPhase.OPENING_4_WHITE_FOURTH;
        statusLabel.setText("[렌주 보강룰] 백 4수를 두세요.");
      }
      case OPENING_4_WHITE_FOURTH -> {
        placeForcedMove(row, col, WHITE, 4);
        blackTurn = true;
        advancedPhase = AdvancedPhase.OPENING_5_BLACK_FIRST;
        statusLabel.setText("[렌주 보강룰] 흑 5수 후보 1개를 두세요.");
      }
      case OPENING_5_BLACK_FIRST -> {
        placeForcedMove(row, col, BLACK, 5);
        fifthCandidates.clear();
        fifthCandidates.add(new int[]{row, col});
        blackTurn = true;
        advancedPhase = AdvancedPhase.OPENING_5_BLACK_SECOND;
        statusLabel.setText("[렌주 보강룰] 흑 5수 후보 2번째를 두세요. (대칭 금지)");
      }
      case OPENING_5_BLACK_SECOND -> {
        if (isSecondFifthSymmetryEquivalent(row, col)) {
          statusLabel.setText("[렌주 보강룰] 두 후보는 대칭으로 같은 자리 취급이라 불가합니다.");
          repaintBoard();
          return;
        }
        placeForcedMove(row, col, BLACK, 5);
        fifthCandidates.add(new int[]{row, col});
        blackTurn = false;
        advancedPhase = AdvancedPhase.OPENING_5_WHITE_REMOVE;
        statusLabel.setText("[렌주 보강룰] 백이 제거할 흑 5수 후보를 클릭하세요.");
      }
      default -> {
      }
    }
    repaintBoard();
    tryComputerMove();
  }

  private void handleAdvancedRemoveChoice(int row, int col) {
    if (fifthCandidates.size() != 2) {
      return;
    }
    int idx = indexOfFifthCandidate(row, col);
    if (idx < 0) {
      statusLabel.setText("[렌주 보강룰] 흑 5수 후보 2개 중 제거할 수를 클릭하세요.");
      repaintBoard();
      return;
    }
    int[] removed = fifthCandidates.get(idx);
    int[] kept = fifthCandidates.get(1 - idx);
    board[removed[0]][removed[1]] = EMPTY;
    moveOrder[removed[0]][removed[1]] = 0;
    moveCount = 5;
    lastMove = new int[]{kept[0], kept[1]};
    fifthCandidates.clear();
    advancedPhase = AdvancedPhase.NORMAL;
    blackTurn = false;
    statusLabel.setText("보강 오프닝 종료: 백 차례");
    repaintBoard();
    tryComputerMove();
  }

  private int indexOfFifthCandidate(int row, int col) {
    for (int i = 0; i < fifthCandidates.size(); i++) {
      int[] p = fifthCandidates.get(i);
      if (p[0] == row && p[1] == col) {
        return i;
      }
    }
    return -1;
  }

  private void placeForcedMove(int row, int col, int color, int number) {
    board[row][col] = color;
    moveOrder[row][col] = number;
    moveCount = Math.max(moveCount, number);
    lastMove = new int[]{row, col};
  }

  private void rollbackMove(int row, int col, int previousMoveCount, int[] previousLastMove) {
    board[row][col] = EMPTY;
    moveOrder[row][col] = 0;
    moveCount = previousMoveCount;
    lastMove = previousLastMove;
    repaintBoard();
  }

  private void updateTurnStatus() {
    statusLabel.setText(blackTurn ? "흑 차례" : "백 차례");
  }

  private void repaintBoard() {
    if (boardPanel != null) {
      boardPanel.repaint();
    }
  }

  private void repaintBoardNow() {
    if (boardPanel != null) {
      boardPanel.repaint();
      boardPanel.paintImmediately(boardPanel.getVisibleRect());
    }
  }

  private boolean isRenjuStyle() {
    return mode == RuleMode.RENJU || mode == RuleMode.RENJU_ADVANCED;
  }

  private boolean isComputerControllingColor(int color) {
    return computerEnabled && computerColor == color;
  }

  private boolean shouldComputerSwapAfterThirdMove() {
    int blackScore = openingColorScore(BLACK);
    int whiteScore = openingColorScore(WHITE);
    int diff = blackScore - whiteScore;
    int noiseRange = Math.max(0, 11 - computerDifficulty);
    int noise = noiseRange == 0 ? 0 : random.nextInt(noiseRange * 2 + 1) - noiseRange;
    return diff + noise >= 2;
  }

  private int openingColorScore(int color) {
    int score = 0;
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] != color) {
          continue;
        }
        int center = 14 - (Math.abs(r - CENTER) + Math.abs(c - CENTER));
        int connect = 0;
        for (int[] d : DIRS) {
          connect += countOneDirection(r, c, d[0], d[1], color);
          connect += countOneDirection(r, c, -d[0], -d[1], color);
        }
        score += center + connect * 3;
      }
    }
    return score;
  }

  private boolean isAroundCenter8(int row, int col) {
    int dr = Math.abs(row - CENTER);
    int dc = Math.abs(col - CENTER);
    return (dr <= 1 && dc <= 1) && !(dr == 0 && dc == 0);
  }

  private boolean isWithinCenterFiveByFive(int row, int col) {
    int dr = row - CENTER;
    int dc = col - CENTER;
    return Math.max(Math.abs(dr), Math.abs(dc)) <= 2;
  }

  private boolean isSecondFifthSymmetryEquivalent(int secondRow, int secondCol) {
    if (fifthCandidates.size() != 1) {
      return false;
    }
    int[] first = fifthCandidates.get(0);
    int firstRow = first[0];
    int firstCol = first[1];
    if (!inRange(secondRow, secondCol) || board[secondRow][secondCol] != EMPTY) {
      return false;
    }

    int[][] target = new int[SIZE][SIZE];
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        target[r][c] = board[r][c];
      }
    }
    target[firstRow][firstCol] = EMPTY;
    target[secondRow][secondCol] = BLACK;

    for (int t = 0; t < 8; t++) {
      if (matchesUnderTransform(target, t)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesUnderTransform(int[][] target, int transformId) {
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] == EMPTY) {
          continue;
        }
        int[] mapped = applySymmetry(r, c, transformId);
        if (!inRange(mapped[0], mapped[1])) {
          return false;
        }
        if (target[mapped[0]][mapped[1]] != board[r][c]) {
          return false;
        }
      }
    }
    return true;
  }

  private int[] applySymmetry(int row, int col, int transformId) {
    int dr = row - CENTER;
    int dc = col - CENTER;
    int ndr;
    int ndc;
    switch (transformId) {
      case 0 -> {
        ndr = dr;
        ndc = dc;
      }
      case 1 -> {
        ndr = dr;
        ndc = -dc;
      }
      case 2 -> {
        ndr = -dr;
        ndc = dc;
      }
      case 3 -> {
        ndr = -dr;
        ndc = -dc;
      }
      case 4 -> {
        ndr = dc;
        ndc = dr;
      }
      case 5 -> {
        ndr = dc;
        ndc = -dr;
      }
      case 6 -> {
        ndr = -dc;
        ndc = dr;
      }
      default -> {
        ndr = -dc;
        ndc = -dr;
      }
    }
    return new int[]{CENTER + ndr, CENTER + ndc};
  }

  private boolean isForbiddenByRenju(int row, int col) {
    int openThreeCount = 0;
    int fourCount = 0;
    for (int[] d : DIRS) {
      String line = buildLine(row, col, d[0], d[1], 5);
      if (hasAnyPatternAroundCenter(line, 5,
          List.of("BBBB.", "BBB.B", "BB.BB", "B.BBB", ".BBBB"))) {
        fourCount++;
      }
      if (hasAnyPatternAroundCenter(line, 5,
          List.of(".BBB.", ".BB.B.", ".B.BB.", "..BBB..", "..BB.B..", "..B.BB..", ".B.B.B."))) {
        openThreeCount++;
      }
    }
    return openThreeCount >= 2 || fourCount >= 2;
  }

  private boolean hasAnyPatternAroundCenter(String line, int center, List<String> patterns) {
    for (String p : patterns) {
      int len = p.length();
      for (int start = 0; start + len <= line.length(); start++) {
        if (start <= center && center < start + len && line.regionMatches(start, p, 0, len)) {
          return true;
        }
      }
    }
    return false;
  }

  private String buildLine(int row, int col, int dr, int dc, int radius) {
    StringBuilder sb = new StringBuilder();
    for (int i = -radius; i <= radius; i++) {
      int r = row + dr * i;
      int c = col + dc * i;
      if (!inRange(r, c)) {
        sb.append('X');
      } else if (board[r][c] == EMPTY) {
        sb.append('.');
      } else if (board[r][c] == BLACK) {
        sb.append('B');
      } else {
        sb.append('W');
      }
    }
    return sb.toString();
  }

  private boolean isExactFive(int row, int col, int color) {
    for (int[] d : DIRS) {
      int len = countContinuous(row, col, d[0], d[1], color);
      if (len == 5) {
        return true;
      }
    }
    return false;
  }

  private boolean isFiveOrMore(int row, int col, int color) {
    for (int[] d : DIRS) {
      if (countContinuous(row, col, d[0], d[1], color) >= 5) {
        return true;
      }
    }
    return false;
  }

  private boolean isOverline(int row, int col, int color) {
    for (int[] d : DIRS) {
      if (countContinuous(row, col, d[0], d[1], color) >= 6) {
        return true;
      }
    }
    return false;
  }

  private int countContinuous(int row, int col, int dr, int dc, int color) {
    int total = 1;
    total += countOneDirection(row, col, dr, dc, color);
    total += countOneDirection(row, col, -dr, -dc, color);
    return total;
  }

  private int countOneDirection(int row, int col, int dr, int dc, int color) {
    int cnt = 0;
    int r = row + dr;
    int c = col + dc;
    while (inRange(r, c) && board[r][c] == color) {
      cnt++;
      r += dr;
      c += dc;
    }
    return cnt;
  }

  private boolean inRange(int r, int c) {
    return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
  }
}
