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
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
  private static final String THINKING_SUFFIX = " (계산중...)";
  private long searchDeadlineNanos = Long.MAX_VALUE;
  private boolean searchTimedOut = false;
  private final Set<Integer> thinkingPreviewMarks = new LinkedHashSet<>();
  private int openingStyleBiasA = 0;
  private int openingStyleBiasB = 0;
  private boolean hasStartedGame = false;

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
    updateWindowTitle();
  }

  void setComputerDifficulty(int difficulty) {
    this.computerDifficulty = Math.max(1, Math.min(10, difficulty));
    updateWindowTitle();
  }

  void setPlayerColor(int color) {
    if (color != BLACK && color != WHITE) {
      return;
    }
    playerColor = color;
    computerColor = (playerColor == BLACK) ? WHITE : BLACK;
    updateWindowTitle();
  }

  void tryComputerMove() {
    if (computerThinking || !shouldComputerMoveNow()) {
      return;
    }
    computerThinking = true;
    try {
      while (shouldComputerMoveNow()) {
        int[] instant = pickInstantForcedMove();
        if (instant != null) {
          thinkingPreviewMarks.clear();
          repaintBoardNow();
          play(instant[0], instant[1], true);
          continue;
        }

        setThinkingStatus(true);
        int[] move;
        try {
          move = pickComputerMove();
        } finally {
          setThinkingStatus(false);
        }
        if (move == null) {
          break;
        }
        thinkingPreviewMarks.clear();
        repaintBoardNow();
        play(move[0], move[1], true);
      }
    } finally {
      computerThinking = false;
      setThinkingStatus(false);
    }
  }

  private int[] pickInstantForcedMove() {
    if (!shouldComputerMoveNow()) {
      return null;
    }
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
      return null;
    }
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase != AdvancedPhase.NONE && advancedPhase != AdvancedPhase.NORMAL) {
      return null;
    }

    int turnColor = getCurrentTurnColor();
    int enemyColor = oppositeColor(turnColor);
    AiProfile profile = getAiProfile(computerDifficulty);

    List<int[]> legal = getLocalizedAIMovesForColor(turnColor, profile.localRadius);
    if (legal.isEmpty()) {
      legal = getLegalMovesForColor(turnColor);
    }
    if (legal.isEmpty()) {
      return null;
    }

    List<int[]> myWins = findImmediateWinningMoves(legal, turnColor);
    if (myWins.size() == 1) {
      return new int[]{myWins.get(0)[0], myWins.get(0)[1]};
    }
    if (myWins.size() > 1) {
      return chooseTopMoveWithNoise(myWins, turnColor, profile, 1);
    }

    List<int[]> enemyWins = findImmediateWinningMoves(getLegalMovesForColor(enemyColor), enemyColor);
    if (enemyWins.isEmpty()) {
      return null;
    }
    List<int[]> forcedBlocks = intersectMoves(legal, enemyWins);
    if (forcedBlocks.size() == 1) {
      return new int[]{forcedBlocks.get(0)[0], forcedBlocks.get(0)[1]};
    }
    if (forcedBlocks.size() > 1) {
      return chooseTopMoveWithNoise(forcedBlocks, turnColor, profile, 1);
    }
    return null;
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
      if (!hasStartedGame) {
        parent.dispose();
        System.exit(0);
      }
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
    openingStyleBiasA = random.nextInt(9) - 4;
    openingStyleBiasB = random.nextInt(9) - 4;
    hasStartedGame = true;
    updateWindowTitle();
    repaintBoardNow();
    tryComputerMove();
  }

  private String getRuleNameForTitle() {
    return switch (mode) {
      case FREESTYLE -> "무규칙";
      case RENJU -> "렌주룰";
      case RENJU_ADVANCED -> "렌주 보강룰";
    };
  }

  private void updateWindowTitle() {
    Window window = SwingUtilities.getWindowAncestor(statusLabel);
    if (window == null && boardPanel != null) {
      window = SwingUtilities.getWindowAncestor(boardPanel);
    }
    if (!(window instanceof JFrame frame)) {
      return;
    }
    String ruleName = getRuleNameForTitle();
    String title = computerEnabled
        ? String.format("오목 - 컴퓨터와 플레이 (%d단계 - %s)", computerDifficulty, ruleName)
        : String.format("오목 (%s)", ruleName);
    frame.setTitle(title);
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
    AiProfile profile = getAiProfile(computerDifficulty);
    searchTimedOut = false;
    searchDeadlineNanos = System.nanoTime() + getSearchTimeLimitMillis(profile) * 1_000_000L;

    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
      return pickRemoveCandidateMove();
    }
    if (mode == RuleMode.RENJU_ADVANCED && advancedPhase != AdvancedPhase.NONE && advancedPhase != AdvancedPhase.NORMAL) {
      return chooseTopMoveWithNoise(legal, turnColor, profile, 1);
    }

    legal = getLocalizedAIMovesForColor(turnColor, profile.localRadius);
    if (legal.isEmpty()) {
      legal = getLegalMovesForColor(turnColor);
    }

    int enemyColor = oppositeColor(turnColor);
    List<int[]> forcedSet = null;
    boolean hardForced = false;
    int minCandidates = getMinSearchCandidates(profile);

    List<int[]> myWins = findImmediateWinningMoves(legal, turnColor);
    if (!myWins.isEmpty()) {
      forcedSet = myWins;
      hardForced = true;
    } else {
      List<int[]> enemyWins = findImmediateWinningMoves(getLegalMovesForColor(enemyColor), enemyColor);
      if (!enemyWins.isEmpty()) {
        List<int[]> forcedBlocks = intersectMoves(legal, enemyWins);
        if (!forcedBlocks.isEmpty()) {
          forcedSet = forcedBlocks;
          hardForced = true;
        }
      }
    }

    TacticalSnapshot enemyThreat = new TacticalSnapshot(0, new ArrayList<>());
    TacticalSnapshot myThreat = new TacticalSnapshot(0, new ArrayList<>());
    if (forcedSet == null) {
      boolean earlyDefenseBoost = computerDifficulty >= 2 && moveCount <= 20;
      boolean earlyPerfectDefense = computerDifficulty >= 2 && computerDifficulty <= 9 && moveCount <= 15;
      boolean strictDefenseVision = computerDifficulty >= 6;
      List<int[]> enemyTacticalScope = (profile.searchDepth >= 5 || earlyDefenseBoost || strictDefenseVision)
          ? getLegalMovesForColor(enemyColor)
          : getLocalizedAIMovesForColor(enemyColor, profile.localRadius);
      enemyThreat = collectTacticalSnapshot(enemyTacticalScope, enemyColor);
      myThreat = collectTacticalSnapshot(legal, turnColor);
      ForkThreatSnapshot enemyForkThreat = collectForkThreatSnapshot(enemyTacticalScope, enemyColor);
      ForkThreatSnapshot myForkThreat = collectForkThreatSnapshot(legal, turnColor);
      BuildUpThreatSnapshot enemyBuildUp = collectBuildUpThreatSnapshot(enemyTacticalScope, enemyColor, profile, 12);
      BuildUpThreatSnapshot myBuildUp = collectBuildUpThreatSnapshot(legal, turnColor, profile, 12);
      PatternStats enemyNowPatterns = evaluatePatternsForColor(enemyColor);
      int enemyLineDangerNow = enemyNowPatterns.open4 * 6 + enemyNowPatterns.closed4 * 3 + enemyNowPatterns.open3 * 2;
      int defenseThreshold = profile.defenseAwarenessSeverity;
      if (moveCount <= 20 && computerDifficulty >= 2) {
        defenseThreshold = (computerDifficulty <= 5) ? Math.max(defenseThreshold, 1) : Math.max(defenseThreshold, 2);
      }
      int boostedEnemySeverity = enemyThreat.maxSeverity;
      if (earlyDefenseBoost && boostedEnemySeverity >= 2) {
        // 2~9단: 15수까지는 사실상 100% 인식 강제, 16~20수에서 단계별로 완만히 하향.
        int boostNumerator;
        if (earlyPerfectDefense) {
          boostNumerator = 18;
        } else {
          int target = switch (computerDifficulty) {
            case 9, 8, 7 -> 14;
            case 6, 5, 4 -> 13;
            case 3, 2 -> 12;
            case 10 -> 15;
            default -> 11;
          };
          int step = Math.max(0, Math.min(5, moveCount - 15));
          boostNumerator = (18 * (5 - step) + target * step + 2) / 5;
        }
        boostedEnemySeverity = (boostedEnemySeverity * boostNumerator + 9) / 10;
      }
      if (forcedSet == null && earlyPerfectDefense && enemyThreat.maxSeverity >= 2 && !enemyThreat.maxSeverityMoves.isEmpty()) {
        if (computerDifficulty <= 5) {
          int[] urgentDefense = chooseBestThreatReductionMove(legal, turnColor, profile);
          if (urgentDefense != null) {
            return urgentDefense;
          }
        }
        List<int[]> perfectBlocks = intersectMoves(legal, enemyThreat.maxSeverityMoves);
        if (!perfectBlocks.isEmpty()) {
          forcedSet = perfectBlocks;
          hardForced = true;
        }
      }
      // 저/중단(2~7단)은 초반 라인 위협이 보이면 탐색보다 즉시 방어를 우선한다.
      if (computerDifficulty >= 2 && computerDifficulty <= 7 && moveCount <= 25 && enemyLineDangerNow >= 2) {
        int[] urgentDefense = chooseBestThreatReductionMove(legal, turnColor, profile);
        if (urgentDefense != null) {
          return urgentDefense;
        }
      }
      if (boostedEnemySeverity >= defenseThreshold) {
        List<int[]> blocks = intersectMoves(legal, enemyThreat.maxSeverityMoves);
        if (!blocks.isEmpty()) {
          if (boostedEnemySeverity >= 4) {
            forcedSet = blocks;
            hardForced = true;
          } else if (earlyDefenseBoost || computerDifficulty <= 6 || myThreat.maxSeverity < enemyThreat.maxSeverity) {
            forcedSet = blocks;
          }
        }
      }
      if (computerDifficulty >= 6 && moveCount <= 20 && enemyThreat.maxSeverity >= 2) {
        List<int[]> directBlocks = intersectMoves(legal, enemyThreat.maxSeverityMoves);
        List<int[]> riskBlocks = chooseThreatReductionMoves(legal, turnColor, profile, Math.max(4, Math.min(10, profile.candidateCount / 2)));
        List<int[]> mergedBlocks = mergeDistinctMoves(directBlocks, riskBlocks, Math.max(6, Math.min(12, profile.candidateCount)));
        if (!mergedBlocks.isEmpty()) {
          forcedSet = mergedBlocks;
          hardForced = true;
        }
      }
      if (forcedSet == null && boostedEnemySeverity >= 2 && (earlyDefenseBoost || myThreat.maxSeverity < 4) && !enemyThreat.maxSeverityMoves.isEmpty()) {
        List<int[]> openThreeBlocks = intersectMoves(legal, enemyThreat.maxSeverityMoves);
        if (!openThreeBlocks.isEmpty()) {
          forcedSet = openThreeBlocks;
        }
      }
      if (forcedSet == null && computerDifficulty >= 6 && moveCount <= 20 && enemyLineDangerNow >= 2) {
        List<int[]> strictBlocks = chooseStrictLineDefenseMoves(legal, turnColor, profile);
        if (!strictBlocks.isEmpty()) {
          forcedSet = strictBlocks;
          hardForced = true;
        }
      }
      if (forcedSet == null && computerDifficulty >= 6 && (enemyNowPatterns.open4 > 0 || enemyNowPatterns.open3 > 0)) {
        List<int[]> defenseSet = chooseThreatReductionMoves(legal, turnColor, profile, Math.max(4, Math.min(10, profile.candidateCount / 2)));
        if (!defenseSet.isEmpty()) {
          forcedSet = defenseSet;
          hardForced = true;
        }
      }
      if (forcedSet == null && computerDifficulty >= 6 && boostedEnemySeverity >= 2) {
        List<int[]> defenseSet = chooseThreatReductionMoves(legal, turnColor, profile, Math.max(3, Math.min(8, profile.candidateCount / 2)));
        if (!defenseSet.isEmpty()) {
          forcedSet = defenseSet;
          hardForced = true;
        }
      }
      if (forcedSet == null && enemyForkThreat.maxForkSeverity >= 4 && !enemyForkThreat.maxForkMoves.isEmpty()) {
        List<int[]> forkBlocks = intersectMoves(legal, enemyForkThreat.maxForkMoves);
        if (!forkBlocks.isEmpty()) {
          forcedSet = forkBlocks;
          hardForced = true;
        }
      }
      if (forcedSet == null && enemyBuildUp.maxBuildSeverity >= 4 && !enemyBuildUp.maxBuildMoves.isEmpty()) {
        List<int[]> buildBlocks = intersectMoves(legal, enemyBuildUp.maxBuildMoves);
        if (!buildBlocks.isEmpty()) {
          forcedSet = buildBlocks;
          hardForced = true;
        }
      }
      if (forcedSet == null && myThreat.maxSeverity >= 4 && !myThreat.maxSeverityMoves.isEmpty()) {
        forcedSet = myThreat.maxSeverityMoves;
      } else if (forcedSet == null && myForkThreat.maxForkSeverity >= 4 && !myForkThreat.maxForkMoves.isEmpty()) {
        forcedSet = myForkThreat.maxForkMoves;
      } else if (forcedSet == null && myBuildUp.maxBuildSeverity >= 4 && !myBuildUp.maxBuildMoves.isEmpty()) {
        forcedSet = myBuildUp.maxBuildMoves;
      }
    }

    List<int[]> candidates;
    if (forcedSet != null) {
      if (hardForced) {
        int forcedLimit = Math.max(2, Math.min(profile.candidateCount, forcedSet.size()));
        candidates = selectSearchCandidates(forcedSet, turnColor, forcedLimit);
      } else {
        int mixLimit = Math.max(4, Math.min(profile.candidateCount, Math.max(6, forcedSet.size())));
        List<int[]> tactical = selectSearchCandidates(forcedSet, turnColor, mixLimit);
        List<int[]> strategic = selectSearchCandidates(legal, turnColor, Math.max(profile.candidateCount, minCandidates));
        candidates = mergeDistinctMoves(tactical, strategic, Math.max(profile.candidateCount, minCandidates));
      }
    } else {
      candidates = selectSearchCandidates(legal, turnColor, Math.max(profile.candidateCount, minCandidates));
    }
    candidates = expandCandidateBreadth(candidates, legal, turnColor, profile, minCandidates);
    candidates = injectCriticalTacticalCandidates(
        candidates,
        legal,
        turnColor,
        profile,
        Math.max(profile.candidateCount + 6, minCandidates + 4),
        true
    );

    if (profile.searchDepth >= 7) {
      List<int[]> enemyThreatSeeds = selectThreatSeedMoves(enemyColor, profile, 10);
      List<int[]> comboBlocks = intersectMoves(legal, enemyThreatSeeds);
      if (!comboBlocks.isEmpty()) {
        candidates = mergeDistinctMoves(candidates, comboBlocks, Math.max(profile.candidateCount + 8, minCandidates + 8));
      }
    }

    if (profile.searchDepth >= 5) {
      int safeMinKeep = hardForced ? 1 : minCandidates;
      candidates = filterUnsafeCandidates(candidates, turnColor, profile, safeMinKeep);
      candidates = expandCandidateBreadth(candidates, legal, turnColor, profile, minCandidates);
      candidates = injectCriticalTacticalCandidates(
          candidates,
          legal,
          turnColor,
          profile,
          Math.max(profile.candidateCount + 6, minCandidates + 4),
          true
      );
    }
    if (profile.searchDepth >= 7) {
      candidates = enforceForkSafetyOnCandidates(candidates, legal, turnColor, profile, hardForced ? 1 : minCandidates);
    }
    int fallbackTopN = (computerDifficulty >= 10) ? 1 : 2;
    int[] fallback = chooseTopMoveWithNoise(candidates, turnColor, profile, fallbackTopN);
    int[] bestStable = fallback;
    for (int depth = 1; depth <= profile.searchDepth; depth++) {
      if (isSearchTimeUp()) {
        break;
      }
      int[] bestAtDepth = searchBestMoveAtDepth(candidates, turnColor, profile, depth);
      if (!searchTimedOut && bestAtDepth != null) {
        bestStable = bestAtDepth;
      } else {
        break;
      }
    }
    if (profile.searchDepth >= 5) {
      bestStable = refineByBlunderCheck(bestStable, candidates, turnColor, profile);
    }
    return enforceCriticalMovePriority(bestStable, turnColor, legal, candidates, profile);
  }

  private int[] enforceCriticalMovePriority(int[] chosen, int turnColor, List<int[]> legal, List<int[]> candidates, AiProfile profile) {
    List<int[]> ownLegal = (legal == null || legal.isEmpty()) ? getLegalMovesForColor(turnColor) : legal;
    List<int[]> ownWins = findImmediateWinningMoves(ownLegal, turnColor);
    if (!ownWins.isEmpty()) {
      return chooseTopMoveWithNoise(ownWins, turnColor, profile, 1);
    }

    int enemy = oppositeColor(turnColor);
    List<int[]> enemyWins = findImmediateWinningMoves(getLegalMovesForColor(enemy), enemy);
    if (!enemyWins.isEmpty()) {
      List<int[]> blocks = intersectMoves(ownLegal, enemyWins);
      if (!blocks.isEmpty()) {
        if (chosen == null || !containsMove(blocks, chosen[0], chosen[1])) {
          return chooseTopMoveWithNoise(blocks, turnColor, profile, 1);
        }
      }
    }

    if (chosen != null) {
      return chosen;
    }
    if (candidates != null && !candidates.isEmpty()) {
      return chooseTopMoveWithNoise(candidates, turnColor, profile, 1);
    }
    return null;
  }

  private boolean containsMove(List<int[]> moves, int row, int col) {
    for (int[] mv : moves) {
      if (mv[0] == row && mv[1] == col) {
        return true;
      }
    }
    return false;
  }

  private int[] refineByBlunderCheck(int[] chosen, List<int[]> candidates, int color, AiProfile profile) {
    if (chosen == null || candidates.isEmpty()) {
      return chosen;
    }
    int chosenEnemyWins = enemyImmediateWinningCountAfterMove(chosen[0], chosen[1], color, profile);
    if (chosenEnemyWins > 0) {
      int[] bestSafe = null;
      int bestEvalSafe = Integer.MIN_VALUE;
      for (int[] mv : candidates) {
        int enemyWins = enemyImmediateWinningCountAfterMove(mv[0], mv[1], color, profile);
        if (enemyWins != 0) {
          continue;
        }
        int eval = evaluateMove(mv[0], mv[1], color, profile);
        if (eval > bestEvalSafe) {
          bestEvalSafe = eval;
          bestSafe = mv;
        }
      }
      if (bestSafe != null) {
        return bestSafe;
      }
    }

    int chosenRisk = enemyMaxTacticalSeverityAfterMove(chosen[0], chosen[1], color, profile);
    int chosenForkRisk = (profile.searchDepth >= 5) ? enemyForkThreatScoreAfterMove(chosen[0], chosen[1], color, profile) : 0;
    int chosenBuildRisk = (profile.searchDepth >= 7) ? enemyBuildUpSeverityAfterMove(chosen[0], chosen[1], color, profile) : 0;
    if (chosenRisk <= 3 && chosenForkRisk <= 2 && chosenBuildRisk <= 3) {
      return chosen;
    }

    int bestRisk = chosenRisk;
    int bestBuildRisk = chosenBuildRisk;
    int bestEval = Integer.MIN_VALUE;
    int[] best = chosen;
    for (int[] mv : candidates) {
      int risk = enemyMaxTacticalSeverityAfterMove(mv[0], mv[1], color, profile);
      int forkRisk = (profile.searchDepth >= 5) ? enemyForkThreatScoreAfterMove(mv[0], mv[1], color, profile) : 0;
      int buildRisk = (profile.searchDepth >= 7) ? enemyBuildUpSeverityAfterMove(mv[0], mv[1], color, profile) : 0;
      if (buildRisk > bestBuildRisk) {
        continue;
      }
      if (risk > bestRisk) {
        continue;
      }
      int eval = evaluateMove(mv[0], mv[1], color, profile);
      int adjustedEval = eval - forkRisk * 1200 - buildRisk * 6000;
      if (buildRisk < bestBuildRisk || risk < bestRisk || adjustedEval > bestEval) {
        bestBuildRisk = buildRisk;
        bestRisk = risk;
        bestEval = adjustedEval;
        best = mv;
      }
    }
    return best;
  }

  private List<int[]> filterUnsafeCandidates(List<int[]> candidates, int color, AiProfile profile, int minKeep) {
    if (candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    int keep = Math.max(1, minKeep);
    List<int[]> safe = new ArrayList<>();
    List<RiskMove> risky = new ArrayList<>();
    int bestRisk = Integer.MAX_VALUE;
    for (int i = 0; i < candidates.size(); i++) {
      int[] mv = candidates.get(i);
      int immediateLoss = enemyImmediateWinningCountAfterMove(mv[0], mv[1], color, profile);
      int risk = enemyMaxTacticalSeverityAfterMove(mv[0], mv[1], color, profile);
      int forkRisk = (profile.searchDepth >= 5) ? enemyForkThreatScoreAfterMove(mv[0], mv[1], color, profile) : 0;
      int buildRisk = (profile.searchDepth >= 7 && i < 10) ? enemyBuildUpSeverityAfterMove(mv[0], mv[1], color, profile) : 0;
      int brick = brickStackPenalty(mv[0], mv[1], color);
      boolean structuralTrap = (brick >= 320 && risk >= 2) || forkRisk >= 4 || buildRisk >= 4;
      int score = risk + immediateLoss * 3 + forkRisk * 2 + buildRisk * 3 + (structuralTrap ? 2 : 0);
      if (immediateLoss == 0 && risk <= 4 && buildRisk <= 3 && !structuralTrap) {
        safe.add(mv);
      }
      risky.add(new RiskMove(mv[0], mv[1], score));
      bestRisk = Math.min(bestRisk, score);
    }
    if (safe.size() >= keep) {
      return safe;
    }
    if (!safe.isEmpty()) {
      Set<String> seen = new HashSet<>();
      for (int[] mv : safe) {
        seen.add(mv[0] + "," + mv[1]);
      }
      risky.sort((a, b) -> Integer.compare(a.score, b.score));
      for (RiskMove rm : risky) {
        if (safe.size() >= keep) {
          break;
        }
        String key = rm.row + "," + rm.col;
        if (seen.contains(key)) {
          continue;
        }
        safe.add(new int[]{rm.row, rm.col});
        seen.add(key);
      }
      return safe;
    }

    List<int[]> leastBad = new ArrayList<>();
    for (RiskMove rm : risky) {
      if (rm.score == bestRisk) {
        leastBad.add(new int[]{rm.row, rm.col});
      }
    }
    return leastBad.isEmpty() ? candidates : leastBad;
  }

  private List<int[]> expandCandidateBreadth(List<int[]> candidates, List<int[]> legal, int color, AiProfile profile, int minCount) {
    if (candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    int target = Math.min(legal.size(), Math.max(1, minCount));
    if (candidates.size() >= target) {
      return candidates;
    }
    List<int[]> expanded = new ArrayList<>(candidates);
    Set<String> seen = new HashSet<>();
    for (int[] mv : expanded) {
      seen.add(mv[0] + "," + mv[1]);
    }
    List<int[]> ranked = selectSearchCandidates(legal, color, legal.size());
    for (int[] mv : ranked) {
      if (expanded.size() >= target) {
        break;
      }
      String key = mv[0] + "," + mv[1];
      if (seen.contains(key)) {
        continue;
      }
      expanded.add(mv);
      seen.add(key);
    }
    return expanded;
  }

  private List<int[]> injectCriticalTacticalCandidates(
      List<int[]> base,
      List<int[]> legal,
      int toMove,
      AiProfile profile,
      int limit,
      boolean includeComboSeeds
  ) {
    if (base == null || base.isEmpty()) {
      return base;
    }
    int enemy = oppositeColor(toMove);
    List<int[]> tacticalScope = getLocalizedAIMovesForColor(enemy, profile.localRadius + 1);
    if (tacticalScope.isEmpty()) {
      tacticalScope = legal;
    }
    TacticalSnapshot myNow = collectTacticalSnapshot(legal, toMove);
    TacticalSnapshot enemyNow = collectTacticalSnapshot(tacticalScope, enemy);

    List<int[]> out = new ArrayList<>(base);
    if (myNow.maxSeverity >= 4 && !myNow.maxSeverityMoves.isEmpty()) {
      List<int[]> ownWinRace = intersectMoves(legal, myNow.maxSeverityMoves);
      out = mergeDistinctMoves(out, ownWinRace, limit);
    }
    if (profile.searchDepth >= 7) {
      List<int[]> ownComboSeeds = selectThreatSeedMoves(toMove, profile, 8);
      out = mergeDistinctMoves(out, ownComboSeeds, limit);
    }
    if (enemyNow.maxSeverity >= 3 && !enemyNow.maxSeverityMoves.isEmpty()) {
      List<int[]> directBlocks = intersectMoves(legal, enemyNow.maxSeverityMoves);
      out = mergeDistinctMoves(out, directBlocks, limit);
    }
    if (includeComboSeeds && profile.searchDepth >= 7 && enemyNow.maxSeverity >= 2) {
      List<int[]> comboSeeds = selectThreatSeedMoves(enemy, profile, 8);
      List<int[]> comboBlocks = intersectMoves(legal, comboSeeds);
      out = mergeDistinctMoves(out, comboBlocks, limit);
    }
    return out;
  }

  private List<int[]> selectThreatSeedMoves(int color, AiProfile profile, int limit) {
    List<int[]> legal = getLocalizedAIMovesForColor(color, profile.localRadius + 1);
    if (legal.isEmpty()) {
      legal = getLegalMovesForColor(color);
    }
    if (legal.isEmpty()) {
      return new ArrayList<>();
    }
    // Keep this lightweight: only score a bounded high-quality subset.
    List<int[]> scope = selectSearchCandidates(legal, color, Math.min(24, legal.size()));
    List<ScoredMove> scored = new ArrayList<>();
    for (int[] mv : scope) {
      MoveTactics t = analyzeMoveTactics(mv[0], mv[1], color);
      int sev = tacticalSeverity(t);
      int fork = forkSeverityIfPlaced(mv[0], mv[1], color);
      int arms = strongThreatArms(mv[0], mv[1], color);
      int build = (profile.searchDepth >= 7) ? projectedBuildUpSeverityIfPlaced(mv[0], mv[1], color, profile) : 0;
      int score = build * 220_000 + fork * 180_000 + sev * 70_000 + arms * 9_000;
      scored.add(new ScoredMove(mv[0], mv[1], score));
    }
    scored.sort((a, b) -> Integer.compare(b.score, a.score));
    int n = Math.min(limit, scored.size());
    List<int[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(new int[]{scored.get(i).row, scored.get(i).col});
    }
    return out;
  }

  private BuildUpThreatSnapshot collectBuildUpThreatSnapshot(
      List<int[]> candidates,
      int color,
      AiProfile profile,
      int limit
  ) {
    if (candidates == null || candidates.isEmpty()) {
      return new BuildUpThreatSnapshot(0, new ArrayList<>());
    }
    List<int[]> scope = selectSearchCandidates(candidates, color, Math.min(limit, candidates.size()));
    int maxBuildSeverity = 0;
    List<int[]> maxBuildMoves = new ArrayList<>();
    for (int[] mv : scope) {
      int sev = projectedBuildUpSeverityIfPlaced(mv[0], mv[1], color, profile);
      if (sev > maxBuildSeverity) {
        maxBuildSeverity = sev;
        maxBuildMoves.clear();
        maxBuildMoves.add(mv);
      } else if (sev == maxBuildSeverity && sev > 0) {
        maxBuildMoves.add(mv);
      }
    }
    return new BuildUpThreatSnapshot(maxBuildSeverity, maxBuildMoves);
  }

  private int projectedBuildUpSeverityIfPlaced(int row, int col, int color, AiProfile profile) {
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return 0;
    }
    int prev = board[row][col];
    board[row][col] = color;

    MoveTactics first = analyzeMoveTactics(row, col, color);
    if (tacticalSeverity(first) >= 5) {
      board[row][col] = prev;
      return 5;
    }

    List<int[]> follow = getLocalizedAIMovesForColor(color, profile.localRadius + 1);
    if (follow.isEmpty()) {
      follow = getLegalMovesForColor(color);
    }
    follow = selectSearchCandidates(follow, color, Math.min(8, follow.size()));
    int best = 0;
    for (int[] f : follow) {
      int sev = forkSeverityIfPlaced(f[0], f[1], color);
      if (sev > best) {
        best = sev;
      }
      if (best >= 5) {
        break;
      }
    }
    board[row][col] = prev;
    return best;
  }

  private List<int[]> mergeDistinctMoves(List<int[]> primary, List<int[]> secondary, int limit) {
    List<int[]> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int[] mv : primary) {
      if (out.size() >= limit) {
        return out;
      }
      String key = mv[0] + "," + mv[1];
      if (seen.add(key)) {
        out.add(mv);
      }
    }
    for (int[] mv : secondary) {
      if (out.size() >= limit) {
        break;
      }
      String key = mv[0] + "," + mv[1];
      if (seen.add(key)) {
        out.add(mv);
      }
    }
    return out;
  }

  private List<int[]> enforceForkSafetyOnCandidates(
      List<int[]> candidates,
      List<int[]> legal,
      int color,
      AiProfile profile,
      int minCandidates
  ) {
    if (candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    List<int[]> safe = new ArrayList<>();
    List<RiskMove> scored = new ArrayList<>();
    int minFork = Integer.MAX_VALUE;
    int minBuild = Integer.MAX_VALUE;
    for (int[] mv : candidates) {
      int forkRisk = enemyForkThreatScoreAfterMove(mv[0], mv[1], color, profile);
      int buildRisk = enemyBuildUpSeverityAfterMove(mv[0], mv[1], color, profile);
      if (forkRisk < minFork) {
        minFork = forkRisk;
      }
      if (buildRisk < minBuild) {
        minBuild = buildRisk;
      }
      scored.add(new RiskMove(mv[0], mv[1], forkRisk * 10 + buildRisk));
    }
    int thresholdFork = Math.max(3, minFork);
    int thresholdBuild = Math.max(3, minBuild);
    for (RiskMove rm : scored) {
      int fork = rm.score / 10;
      int build = rm.score % 10;
      if ((fork <= thresholdFork && build <= thresholdBuild) || isWinningIfPlaced(rm.row, rm.col, color)) {
        safe.add(new int[]{rm.row, rm.col});
      }
    }
    if (safe.isEmpty()) {
      for (RiskMove rm : scored) {
        int fork = rm.score / 10;
        int build = rm.score % 10;
        if (fork == minFork && build == minBuild) {
          safe.add(new int[]{rm.row, rm.col});
        }
      }
    }
    safe = expandCandidateBreadth(safe, legal, color, profile, minCandidates);
    return safe.isEmpty() ? candidates : safe;
  }

  private List<int[]> enforceBuildUpDefenseCandidates(
      List<int[]> candidates,
      List<int[]> legal,
      int color,
      AiProfile profile,
      int minCandidates
  ) {
    if (candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    List<int[]> ranked = selectSearchCandidates(candidates, color, Math.min(14, candidates.size()));
    List<int[]> safe = new ArrayList<>();
    int bestRisk = Integer.MAX_VALUE;
    List<int[]> best = new ArrayList<>();
    for (int[] mv : ranked) {
      if (isSearchTimeUp()) {
        break;
      }
      int risk = worstEnemyBuildUpLineAfterMyMove(mv[0], mv[1], color, profile);
      if (risk < bestRisk) {
        bestRisk = risk;
        best.clear();
        best.add(mv);
      } else if (risk == bestRisk) {
        best.add(mv);
      }
      if (risk <= 4) {
        safe.add(mv);
      }
    }
    List<int[]> out = !safe.isEmpty() ? safe : best;
    if (out.isEmpty()) {
      return candidates;
    }
    out = expandCandidateBreadth(out, legal, color, profile, minCandidates);
    return out.isEmpty() ? candidates : out;
  }

  private int worstEnemyBuildUpLineAfterMyMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;

    List<int[]> enemyLegal = getLegalMovesForColor(enemy);
    if (enemyLegal.isEmpty()) {
      board[row][col] = prev;
      return 0;
    }
    List<int[]> enemyLines = selectThreatSeedMoves(enemy, profile, 10);
    enemyLines = mergeDistinctMoves(enemyLines, selectSearchCandidates(enemyLegal, enemy, 10), 14);

    int worst = 0;
    for (int[] em : enemyLines) {
      if (isSearchTimeUp()) {
        break;
      }
      int prevEnemy = board[em[0]][em[1]];
      board[em[0]][em[1]] = enemy;

      int enemyForkNow = forkSeverityIfPlaced(em[0], em[1], enemy);
      int enemyBuildNow = projectedBuildUpSeverityIfPlaced(em[0], em[1], enemy, profile);
      int lineThreat = Math.max(enemyForkNow, enemyBuildNow);
      if (lineThreat >= 5) {
        board[em[0]][em[1]] = prevEnemy;
        board[row][col] = prev;
        return 5;
      }

      List<int[]> myDefenseLegal = getLegalMovesForColor(color);
      int bestDefense = 6;
      if (!myDefenseLegal.isEmpty()) {
        List<int[]> myDefense = selectSearchCandidates(myDefenseLegal, color, 10);
        for (int[] d : myDefense) {
          int prevDef = board[d[0]][d[1]];
          board[d[0]][d[1]] = color;
          List<int[]> enemyFollow = selectThreatSeedMoves(enemy, profile, 8);
          int followBest = 0;
          for (int[] ef : enemyFollow) {
            int sev = Math.max(
                forkSeverityIfPlaced(ef[0], ef[1], enemy),
                projectedBuildUpSeverityIfPlaced(ef[0], ef[1], enemy, profile)
            );
            if (sev > followBest) {
              followBest = sev;
            }
            if (followBest >= 5) {
              break;
            }
          }
          board[d[0]][d[1]] = prevDef;
          if (followBest < bestDefense) {
            bestDefense = followBest;
          }
          if (bestDefense <= 2) {
            break;
          }
        }
      }

      if (bestDefense > worst) {
        worst = bestDefense;
      }
      board[em[0]][em[1]] = prevEnemy;
      if (worst >= 5) {
        break;
      }
    }

    board[row][col] = prev;
    return worst;
  }

  private int getMinSearchCandidates(AiProfile profile) {
    if (moveCount <= 14) {
      return Math.min(Math.max(12, profile.candidateCount), 18);
    }
    return Math.min(Math.max(10, profile.candidateCount / 2), 16);
  }

  private int enemyMaxTacticalSeverityAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;
    TacticalSnapshot enemyThreat = collectTacticalSnapshot(getLocalizedAIMovesForColor(enemy, profile.localRadius), enemy);
    board[row][col] = prev;
    return enemyThreat.maxSeverity;
  }

  private int enemyImmediateWinningCountAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;
    List<int[]> enemyLegal = getLocalizedAIMovesForColor(enemy, profile.localRadius);
    int count = 0;
    for (int[] mv : enemyLegal) {
      if (isWinningIfPlaced(mv[0], mv[1], enemy)) {
        count++;
        if (count >= 2) {
          break;
        }
      }
    }
    board[row][col] = prev;
    return count;
  }

  private int enemyForkThreatScoreAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;
    List<int[]> scope = getLocalizedAIMovesForColor(enemy, profile.localRadius + 2);
    if (scope.isEmpty()) {
      scope = getLegalMovesForColor(enemy);
    }
    scope = mergeDistinctMoves(scope, selectSearchCandidates(getLegalMovesForColor(enemy), enemy, Math.min(12, Math.max(6, profile.candidateCount / 2))), 18);
    int best = 0;
    for (int[] mv : scope) {
      int sev = forkSeverityIfPlaced(mv[0], mv[1], enemy);
      if (sev > best) {
        best = sev;
      }
      if (best >= 5) {
        break;
      }
    }
    board[row][col] = prev;
    return best;
  }

  private int enemyBuildUpSeverityAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;
    List<int[]> scope = getLocalizedAIMovesForColor(enemy, profile.localRadius + 2);
    if (scope.isEmpty()) {
      scope = getLegalMovesForColor(enemy);
    }
    BuildUpThreatSnapshot snap = collectBuildUpThreatSnapshot(scope, enemy, profile, 14);
    board[row][col] = prev;
    return snap.maxBuildSeverity;
  }

  private int[] chooseBestThreatReductionMove(List<int[]> legal, int color, AiProfile profile) {
    if (legal == null || legal.isEmpty()) {
      return null;
    }
    int bestRisk = Integer.MAX_VALUE;
    int bestEval = Integer.MIN_VALUE;
    int[] best = null;
    for (int[] mv : legal) {
      int immediateLoss = enemyImmediateWinningCountAfterMove(mv[0], mv[1], color, profile);
      int sev = enemyMaxTacticalSeverityAfterMove(mv[0], mv[1], color, profile);
      int fork = (profile.searchDepth >= 5) ? enemyForkThreatScoreAfterMove(mv[0], mv[1], color, profile) : 0;
      int build = (profile.searchDepth >= 7) ? enemyBuildUpSeverityAfterMove(mv[0], mv[1], color, profile) : 0;
      int risk = immediateLoss * 200 + sev * 30 + fork * 7 + build * 6;
      int eval = evaluateMove(mv[0], mv[1], color, profile);
      if (risk < bestRisk || (risk == bestRisk && eval > bestEval)) {
        bestRisk = risk;
        bestEval = eval;
        best = mv;
      }
    }
    return best;
  }

  private List<int[]> chooseThreatReductionMoves(List<int[]> legal, int color, AiProfile profile, int limit) {
    List<int[]> out = new ArrayList<>();
    if (legal == null || legal.isEmpty() || limit <= 0) {
      return out;
    }
    List<ScoredMove> scored = new ArrayList<>();
    for (int[] mv : legal) {
      int immediateLoss = enemyImmediateWinningCountAfterMove(mv[0], mv[1], color, profile);
      int sev = enemyMaxTacticalSeverityAfterMove(mv[0], mv[1], color, profile);
      int fork = (profile.searchDepth >= 5) ? enemyForkThreatScoreAfterMove(mv[0], mv[1], color, profile) : 0;
      int build = (profile.searchDepth >= 7) ? enemyBuildUpSeverityAfterMove(mv[0], mv[1], color, profile) : 0;
      int lineDanger = enemyLineDangerAfterMove(mv[0], mv[1], color);
      int risk = immediateLoss * 240 + lineDanger * 40 + sev * 28 + fork * 7 + build * 6;
      int eval = evaluateMove(mv[0], mv[1], color, profile);
      scored.add(new ScoredMove(mv[0], mv[1], -risk * 1000 + eval));
    }
    scored.sort((a, b) -> Integer.compare(b.score, a.score));
    int n = Math.min(limit, scored.size());
    for (int i = 0; i < n; i++) {
      out.add(new int[]{scored.get(i).row, scored.get(i).col});
    }
    return out;
  }

  private List<int[]> chooseStrictLineDefenseMoves(List<int[]> legal, int color, AiProfile profile) {
    List<int[]> out = new ArrayList<>();
    if (legal == null || legal.isEmpty()) {
      return out;
    }
    int baseline = enemyLineDangerAfterMove(-1, -1, color);
    int best = Integer.MAX_VALUE;
    for (int[] mv : legal) {
      int danger = enemyLineDangerAfterMove(mv[0], mv[1], color);
      if (danger < best) {
        best = danger;
      }
    }
    if (best >= baseline) {
      return out;
    }
    for (int[] mv : legal) {
      int danger = enemyLineDangerAfterMove(mv[0], mv[1], color);
      if (danger == best) {
        out.add(new int[]{mv[0], mv[1]});
      }
    }
    return out;
  }

  private int enemyLineDangerAfterMove(int row, int col, int color) {
    int enemy = oppositeColor(color);
    int prev = EMPTY;
    if (row >= 0 && col >= 0) {
      prev = board[row][col];
      board[row][col] = color;
    }
    PatternStats p = evaluatePatternsForColor(enemy);
    if (row >= 0 && col >= 0) {
      board[row][col] = prev;
    }
    return p.open4 * 6 + p.closed4 * 3 + p.open3 * 2;
  }

  private int enemyTwoStepComboRiskAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;

    List<int[]> enemyLegal = getLegalMovesForColor(enemy);
    if (enemyLegal.isEmpty()) {
      board[row][col] = prev;
      return 0;
    }
    List<int[]> enemySeeds = selectSearchCandidates(enemyLegal, enemy, 12);
    enemySeeds = mergeDistinctMoves(enemySeeds, selectComboMoves(enemy, profile, 8), 14);

    int worst = 0;
    for (int[] seed : enemySeeds) {
      int prevSeed = board[seed[0]][seed[1]];
      board[seed[0]][seed[1]] = enemy;

      MoveTactics first = analyzeMoveTactics(seed[0], seed[1], enemy);
      int firstSev = tacticalSeverity(first);
      if (firstSev >= 5) {
        board[seed[0]][seed[1]] = prevSeed;
        board[row][col] = prev;
        return 5;
      }

      List<int[]> myDefenseLegal = getLegalMovesForColor(color);
      int bestDefenseOutcome = 6;
      if (!myDefenseLegal.isEmpty()) {
        List<int[]> defenseMoves = selectSearchCandidates(myDefenseLegal, color, 8);
        for (int[] d : defenseMoves) {
          int prevD = board[d[0]][d[1]];
          board[d[0]][d[1]] = color;
          int followSev = maxThreatSeverityForColor(enemy, profile, 8);
          board[d[0]][d[1]] = prevD;
          if (followSev < bestDefenseOutcome) {
            bestDefenseOutcome = followSev;
          }
          if (bestDefenseOutcome <= 2) {
            break;
          }
        }
      }

      if (bestDefenseOutcome >= 5) {
        worst = Math.max(worst, 5);
      } else if (bestDefenseOutcome >= 4 && firstSev >= 2) {
        worst = Math.max(worst, 4);
      } else if (bestDefenseOutcome >= 3 && firstSev >= 2) {
        worst = Math.max(worst, 3);
      }

      board[seed[0]][seed[1]] = prevSeed;
      if (worst >= 5) {
        break;
      }
    }
    board[row][col] = prev;
    return worst;
  }

  private int ownTwoStepComboGainAfterMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    int prev = board[row][col];
    board[row][col] = color;

    List<int[]> myLegal = getLegalMovesForColor(color);
    if (myLegal.isEmpty()) {
      board[row][col] = prev;
      return 0;
    }
    List<int[]> mySeeds = selectSearchCandidates(myLegal, color, 12);
    mySeeds = mergeDistinctMoves(mySeeds, selectComboMoves(color, profile, 10), 14);

    int best = 0;
    for (int[] seed : mySeeds) {
      int prevSeed = board[seed[0]][seed[1]];
      board[seed[0]][seed[1]] = color;

      MoveTactics first = analyzeMoveTactics(seed[0], seed[1], color);
      int firstSev = tacticalSeverity(first);
      if (firstSev >= 5) {
        board[seed[0]][seed[1]] = prevSeed;
        board[row][col] = prev;
        return 5;
      }

      List<int[]> enemyDefenseLegal = getLegalMovesForColor(enemy);
      int worstForMe = 6;
      if (!enemyDefenseLegal.isEmpty()) {
        List<int[]> enemyDefense = selectSearchCandidates(enemyDefenseLegal, enemy, 8);
        for (int[] d : enemyDefense) {
          int prevD = board[d[0]][d[1]];
          board[d[0]][d[1]] = enemy;
          int followSev = maxThreatSeverityForColor(color, profile, 8);
          board[d[0]][d[1]] = prevD;
          if (followSev < worstForMe) {
            worstForMe = followSev;
          }
          if (worstForMe <= 2) {
            break;
          }
        }
      }

      if (worstForMe >= 5) {
        best = Math.max(best, 5);
      } else if (worstForMe >= 4 && firstSev >= 2) {
        best = Math.max(best, 4);
      } else if (worstForMe >= 3 && firstSev >= 2) {
        best = Math.max(best, 3);
      }

      board[seed[0]][seed[1]] = prevSeed;
      if (best >= 5) {
        break;
      }
    }
    board[row][col] = prev;
    return best;
  }

  private int forkSeverityIfPlaced(int row, int col, int color) {
    MoveTactics t = analyzeMoveTactics(row, col, color);
    if (t.winNow) {
      return 6;
    }
    int winContinuations = countImmediateWinningContinuationsAfterMove(row, col, color, 2);
    if (winContinuations >= 2) {
      return 5;
    }
    int arms = strongThreatArms(row, col, color);
    if (t.doubleThreat43) {
      return 5;
    }
    if (t.open4 > 0 && arms >= 2) {
      return 5;
    }
    if (t.open3 >= 2) {
      return 4;
    }
    if (arms >= 2 && (t.open3 > 0 || t.closed4 > 0)) {
      return 3;
    }
    return 0;
  }

  private int countImmediateWinningContinuationsAfterMove(int row, int col, int color, int limit) {
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return 0;
    }
    int prev = board[row][col];
    board[row][col] = color;
    List<int[]> follow = getLocalizedAIMovesForColor(color, 3);
    if (follow.isEmpty()) {
      follow = getLegalMovesForColor(color);
    }
    int wins = 0;
    for (int[] mv : follow) {
      if (isWinningIfPlaced(mv[0], mv[1], color)) {
        wins++;
        if (wins >= limit) {
          break;
        }
      }
    }
    board[row][col] = prev;
    return wins;
  }

  private int strongThreatArms(int row, int col, int color) {
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return 0;
    }
    int prev = board[row][col];
    board[row][col] = color;
    int arms = 0;
    for (int[] d : DIRS) {
      int f = countOneDirection(row, col, d[0], d[1], color);
      int b = countOneDirection(row, col, -d[0], -d[1], color);
      int len = f + b + 1;
      boolean openF = isOpenEnd(row, col, d[0], d[1], f, color);
      boolean openB = isOpenEnd(row, col, -d[0], -d[1], b, color);
      int open = (openF ? 1 : 0) + (openB ? 1 : 0);
      if ((len >= 4 && open >= 1) || (len == 3 && open == 2)) {
        arms++;
      }
    }
    board[row][col] = prev;
    return arms;
  }

  private int[] searchBestMoveAtDepth(List<int[]> candidates, int turnColor, AiProfile profile, int depth) {
    int bestScore = Integer.MIN_VALUE;
    List<int[]> bestMoves = new ArrayList<>();
    for (int[] mv : candidates) {
      if (isSearchTimeUp()) {
        searchTimedOut = true;
        break;
      }
      updateThinkingCandidate(mv[0], mv[1]);
      int prev = board[mv[0]][mv[1]];
      board[mv[0]][mv[1]] = turnColor;
      int score = -negamax(depth - 1, oppositeColor(turnColor), turnColor,
          Integer.MIN_VALUE / 4, Integer.MAX_VALUE / 4, profile, true);
      board[mv[0]][mv[1]] = prev;

      if (searchTimedOut) {
        break;
      }

      if (profile.searchDepth >= 7) {
        TacticalSnapshot enemyAfter = collectTacticalSnapshot(
            getLocalizedAIMovesForColor(oppositeColor(turnColor), profile.localRadius),
            oppositeColor(turnColor)
        );
        int enemyForkRisk = enemyForkThreatScoreAfterMove(mv[0], mv[1], turnColor, profile);
        int enemyBuildRisk = enemyBuildUpSeverityAfterMove(mv[0], mv[1], turnColor, profile);
        if (enemyAfter.maxSeverity >= 5) {
          score -= 3_000_000;
        } else if (enemyAfter.maxSeverity >= 4) {
          score -= 500_000;
        }
        if (enemyForkRisk >= 5) {
          score -= 1_800_000;
        } else if (enemyForkRisk >= 4) {
          score -= 450_000;
        }
        if (enemyBuildRisk >= 5) {
          score -= 1_400_000;
        } else if (enemyBuildRisk >= 4) {
          score -= 380_000;
        }
      }
      if (score > bestScore) {
        bestScore = score;
        bestMoves.clear();
        bestMoves.add(mv);
      } else if (score == bestScore) {
        bestMoves.add(mv);
      }
    }
    if (bestMoves.isEmpty()) {
      return null;
    }
    return chooseTopMoveWithNoise(bestMoves, turnColor, profile, 1);
  }

  private int negamax(int depth, int toMove, int rootColor, int alpha, int beta, AiProfile profile, boolean canExtend) {
    if (isSearchTimeUp()) {
      searchTimedOut = true;
      return evaluateBoard(rootColor, profile);
    }
    List<int[]> legal = getLocalizedAIMovesForColor(toMove, profile.localRadius);
    if (legal.isEmpty()) {
      legal = getLegalMovesForColor(toMove);
    }
    if (legal.isEmpty()) {
      return evaluateBoard(rootColor, profile);
    }
    if (hasImmediateWinningMove(legal, toMove)) {
      int winScore = 5_000_000 - (profile.searchDepth - depth) * 100;
      return toMove == rootColor ? winScore : -winScore;
    }
    if (depth <= 0) {
      if (canExtend && profile.searchDepth >= 5) {
        TacticalSnapshot now = collectTacticalSnapshot(getLocalizedAIMovesForColor(toMove, profile.localRadius), toMove);
        TacticalSnapshot opp = collectTacticalSnapshot(getLocalizedAIMovesForColor(oppositeColor(toMove), profile.localRadius), oppositeColor(toMove));
        if (now.maxSeverity >= 4 || opp.maxSeverity >= 4) {
          depth = 1;
          canExtend = false;
        } else {
          return evaluateBoard(rootColor, profile);
        }
      } else {
        return evaluateBoard(rootColor, profile);
      }
    }

    List<int[]> candidates = selectSearchCandidates(legal, toMove, Math.max(6, profile.candidateCount - (profile.searchDepth - depth) * 3));
    candidates = injectCriticalTacticalCandidates(
        candidates,
        legal,
        toMove,
        profile,
        Math.max(8, profile.candidateCount - (profile.searchDepth - depth) * 2),
        depth >= Math.max(2, profile.searchDepth - 3)
    );
    int value = Integer.MIN_VALUE / 4;
    for (int[] mv : candidates) {
      if (isSearchTimeUp()) {
        searchTimedOut = true;
        break;
      }
      int prev = board[mv[0]][mv[1]];
      board[mv[0]][mv[1]] = toMove;
      int score = -negamax(depth - 1, oppositeColor(toMove), rootColor, -beta, -alpha, profile, canExtend);
      board[mv[0]][mv[1]] = prev;

      if (score > value) {
        value = score;
      }
      if (score > alpha) {
        alpha = score;
      }
      if (alpha >= beta) {
        break;
      }
    }
    return value;
  }

  private int evaluateBoard(int rootColor, AiProfile profile) {
    int enemy = oppositeColor(rootColor);
    PatternStats mine = evaluatePatternsForColor(rootColor);
    PatternStats theirs = evaluatePatternsForColor(enemy);

    int attack =
        mine.five * 1_000_000 +
        mine.doubleThreat43 * 80_000 +
        mine.open4 * 50_000 +
        mine.closed4 * 14_000 +
        mine.open3 * 4_000 +
        mine.closed3 * 900 +
        mine.open2 * 180;
    int defense =
        theirs.five * 1_000_000 +
        theirs.doubleThreat43 * 85_000 +
        theirs.open4 * 55_000 +
        theirs.closed4 * 16_000 +
        theirs.open3 * 4_500 +
        theirs.closed3 * 950 +
        theirs.open2 * 200;

    int center = centerMassScore(rootColor) - centerMassScore(enemy);
    return attack * profile.attackWeight - defense * profile.defenseWeight + center * 4;
  }

  private int evaluateTwoStepComboSwing(int rootColor, AiProfile profile) {
    if (!profile.enableComboProbe || isSearchTimeUp()) {
      return 0;
    }
    int own = hasTwoStepComboThreat(rootColor, profile) ? 1 : 0;
    int opp = hasTwoStepComboThreat(oppositeColor(rootColor), profile) ? 1 : 0;
    return own * 280_000 - opp * 300_000;
  }

  private boolean hasTwoStepComboThreat(int attackerColor, AiProfile profile) {
    List<int[]> attackMoves = selectComboMoves(attackerColor, profile, 6);
    if (attackMoves.isEmpty()) {
      return false;
    }
    int defenderColor = oppositeColor(attackerColor);

    for (int[] a : attackMoves) {
      if (isSearchTimeUp()) {
        return false;
      }
      int prevA = board[a[0]][a[1]];
      board[a[0]][a[1]] = attackerColor;

      MoveTactics first = analyzeMoveTactics(a[0], a[1], attackerColor);
      if (tacticalSeverity(first) >= 5) {
        board[a[0]][a[1]] = prevA;
        return true;
      }

      List<int[]> defenseMoves = selectComboMoves(defenderColor, profile, 5);
      int bestAttackerFollowup = Integer.MAX_VALUE;
      if (defenseMoves.isEmpty()) {
        bestAttackerFollowup = maxThreatSeverityForColor(attackerColor, profile, 6);
      } else {
        for (int[] d : defenseMoves) {
          if (isSearchTimeUp()) {
            break;
          }
          int prevD = board[d[0]][d[1]];
          board[d[0]][d[1]] = defenderColor;
          int follow = maxThreatSeverityForColor(attackerColor, profile, 6);
          board[d[0]][d[1]] = prevD;
          if (follow < bestAttackerFollowup) {
            bestAttackerFollowup = follow;
          }
          if (bestAttackerFollowup <= 2) {
            break;
          }
        }
      }

      board[a[0]][a[1]] = prevA;
      if (bestAttackerFollowup >= 5) {
        return true;
      }
    }
    return false;
  }

  private int maxThreatSeverityForColor(int color, AiProfile profile, int limit) {
    int max = 0;
    List<int[]> moves = selectComboMoves(color, profile, limit);
    for (int[] mv : moves) {
      MoveTactics t = analyzeMoveTactics(mv[0], mv[1], color);
      int sev = tacticalSeverity(t);
      if (sev > max) {
        max = sev;
      }
      if (max >= 6) {
        return max;
      }
    }
    return max;
  }

  private List<int[]> selectComboMoves(int color, AiProfile profile, int limit) {
    List<int[]> legal = getLocalizedAIMovesForColor(color, profile.localRadius + 1);
    if (legal.isEmpty()) {
      legal = getLegalMovesForColor(color);
    }
    List<ScoredMove> scored = new ArrayList<>();
    for (int[] mv : legal) {
      MoveTactics t = analyzeMoveTactics(mv[0], mv[1], color);
      int fork = forkSeverityIfPlaced(mv[0], mv[1], color);
      int arms = strongThreatArms(mv[0], mv[1], color);
      int score = fork * 140_000 + tacticalSeverity(t) * 95_000 + arms * 12_000 + evaluateMove(mv[0], mv[1], color, profile);
      scored.add(new ScoredMove(mv[0], mv[1], score));
    }
    scored.sort((a, b) -> Integer.compare(b.score, a.score));
    int n = Math.min(limit, scored.size());
    List<int[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(new int[]{scored.get(i).row, scored.get(i).col});
    }
    return out;
  }

  private PatternStats evaluatePatternsForColor(int color) {
    PatternStats out = new PatternStats();
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] != color) {
          continue;
        }
        for (int[] d : DIRS) {
          int pr = r - d[0];
          int pc = c - d[1];
          if (inRange(pr, pc) && board[pr][pc] == color) {
            continue;
          }
          int len = 1 + countOneDirection(r, c, d[0], d[1], color);
          int endR = r + d[0] * len;
          int endC = c + d[1] * len;
          boolean openStart = inRange(pr, pc) && board[pr][pc] == EMPTY;
          boolean openEnd = inRange(endR, endC) && board[endR][endC] == EMPTY;
          int open = (openStart ? 1 : 0) + (openEnd ? 1 : 0);

          if (len >= 5) {
            if (!(color == BLACK && isRenjuStyle() && len > 5 && isRenjuPlayPhase())) {
              out.five++;
            }
          } else if (len == 4) {
            if (open == 2) {
              out.open4++;
            } else if (open == 1) {
              out.closed4++;
            }
          } else if (len == 3) {
            if (open == 2) {
              out.open3++;
            } else if (open == 1) {
              out.closed3++;
            }
          } else if (len == 2 && open == 2) {
            out.open2++;
          }
        }
      }
    }
    if ((out.open4 + out.closed4) >= 1 && out.open3 >= 1) {
      out.doubleThreat43++;
    }
    if ((out.open4 + out.closed4) >= 2 || out.open3 >= 2) {
      out.doubleThreat43++;
    }
    return out;
  }

  private boolean isRenjuPlayPhase() {
    if (mode == RuleMode.RENJU_ADVANCED) {
      return advancedPhase == AdvancedPhase.NORMAL;
    }
    return mode == RuleMode.RENJU;
  }

  private TacticalSnapshot collectTacticalSnapshot(List<int[]> candidates, int color) {
    int maxSeverity = 0;
    List<int[]> maxMoves = new ArrayList<>();
    for (int[] mv : candidates) {
      MoveTactics t = analyzeMoveTactics(mv[0], mv[1], color);
      int sev = tacticalSeverity(t);
      if (sev > maxSeverity) {
        maxSeverity = sev;
        maxMoves.clear();
        maxMoves.add(mv);
      } else if (sev == maxSeverity && sev > 0) {
        maxMoves.add(mv);
      }
    }
    return new TacticalSnapshot(maxSeverity, maxMoves);
  }

  private ForkThreatSnapshot collectForkThreatSnapshot(List<int[]> candidates, int color) {
    int maxForkSeverity = 0;
    List<int[]> maxForkMoves = new ArrayList<>();
    for (int[] mv : candidates) {
      int sev = forkSeverityIfPlaced(mv[0], mv[1], color);
      if (sev > maxForkSeverity) {
        maxForkSeverity = sev;
        maxForkMoves.clear();
        maxForkMoves.add(mv);
      } else if (sev == maxForkSeverity && sev > 0) {
        maxForkMoves.add(mv);
      }
    }
    return new ForkThreatSnapshot(maxForkSeverity, maxForkMoves);
  }

  private MoveTactics analyzeMoveTactics(int row, int col, int color) {
    MoveTactics t = new MoveTactics();
    if (!inRange(row, col) || board[row][col] != EMPTY) {
      return t;
    }
    if (color == BLACK && isRenjuStyle() && isRenjuPlayPhase() && isForbiddenCellForBlack(row, col)) {
      return t;
    }
    int prev = board[row][col];
    board[row][col] = color;
    t.winNow = isWinningIfPlaced(row, col, color);

    for (int[] d : DIRS) {
      int f = countOneDirection(row, col, d[0], d[1], color);
      int b = countOneDirection(row, col, -d[0], -d[1], color);
      int len = f + b + 1;
      boolean openF = isOpenEnd(row, col, d[0], d[1], f, color);
      boolean openB = isOpenEnd(row, col, -d[0], -d[1], b, color);
      int open = (openF ? 1 : 0) + (openB ? 1 : 0);

      if (len >= 4 && open == 2) {
        t.open4++;
      } else if (len >= 4 && open == 1) {
        t.closed4++;
      } else if (len == 3 && open == 2) {
        t.open3++;
      } else if (len == 3 && open == 1) {
        t.closed3++;
      }
    }
    t.doubleThreat43 = ((t.open4 + t.closed4) >= 1 && t.open3 >= 1) || ((t.open4 + t.closed4) >= 2) || (t.open3 >= 2);
    board[row][col] = prev;
    return t;
  }

  private int tacticalSeverity(MoveTactics t) {
    if (t.winNow) {
      return 6;
    }
    if (t.doubleThreat43) {
      return 5;
    }
    if (t.open4 > 0) {
      return 4;
    }
    if (t.closed4 > 0) {
      return 3;
    }
    if (t.open3 > 0) {
      return 2;
    }
    if (t.closed3 > 0) {
      return 1;
    }
    return 0;
  }

  private List<int[]> findImmediateWinningMoves(List<int[]> legal, int color) {
    List<int[]> wins = new ArrayList<>();
    for (int[] mv : legal) {
      if (isWinningIfPlaced(mv[0], mv[1], color)) {
        wins.add(mv);
      }
    }
    return wins;
  }

  private boolean hasImmediateWinningMove(List<int[]> legal, int color) {
    return !findImmediateWinningMoves(legal, color).isEmpty();
  }

  private List<int[]> selectSearchCandidates(List<int[]> legal, int color, int limit) {
    List<int[]> near = new ArrayList<>();
    for (int[] mv : legal) {
      if (hasNeighborWithin(mv[0], mv[1], 3) || moveCount < 2) {
        near.add(mv);
      }
    }
    if (near.size() < Math.min(16, Math.max(10, limit / 2))) {
      near = legal;
    }
    if (near.isEmpty()) {
      near = legal;
    }

    AiProfile profile = getAiProfile(computerDifficulty);
    List<ScoredMove> scored = new ArrayList<>();
    for (int[] mv : near) {
      int score = evaluateMove(mv[0], mv[1], color, profile);
      scored.add(new ScoredMove(mv[0], mv[1], score));
    }
    scored.sort((a, b) -> Integer.compare(b.score, a.score));

    List<int[]> out = new ArrayList<>();
    int n = Math.min(limit, scored.size());
    for (int i = 0; i < n; i++) {
      out.add(new int[]{scored.get(i).row, scored.get(i).col});
    }
    if (out.isEmpty()) {
      out.addAll(near);
    }
    return out;
  }

  private int[] chooseTopMoveWithNoise(List<int[]> moves, int color, AiProfile profile, int topN) {
    if (moves.isEmpty()) {
      return null;
    }
    List<ScoredMove> scored = new ArrayList<>();
    for (int[] mv : moves) {
      int s = evaluateMove(mv[0], mv[1], color, profile);
      scored.add(new ScoredMove(mv[0], mv[1], s));
    }
    scored.sort((a, b) -> Integer.compare(b.score, a.score));
    int pool = Math.min(Math.max(1, topN + profile.mistakeAllowance), scored.size());
    ScoredMove chosen = scored.get(random.nextInt(pool));
    return new int[]{chosen.row, chosen.col};
  }

  private List<int[]> intersectMoves(List<int[]> a, List<int[]> b) {
    List<int[]> out = new ArrayList<>();
    for (int[] x : a) {
      for (int[] y : b) {
        if (x[0] == y[0] && x[1] == y[1]) {
          out.add(x);
          break;
        }
      }
    }
    return out;
  }

  private int evaluateMove(int row, int col, int color, AiProfile profile) {
    int enemy = oppositeColor(color);
    MoveTactics mine = analyzeMoveTactics(row, col, color);
    MoveTactics opp = analyzeMoveTactics(row, col, enemy);
    int mySev = tacticalSeverity(mine);
    int oppSev = tacticalSeverity(opp);
    int enemyMaxNow = currentMaxContinuous(enemy);
    boolean lowLevelDefensive = computerDifficulty <= 5;
    boolean earlyLooseDefense = moveCount <= 10 && enemyMaxNow <= 2 && !lowLevelDefensive;
    int oppPenaltyFactor;
    if (oppSev >= 4) {
      oppPenaltyFactor = 110;
    } else if (oppSev >= 2) {
      oppPenaltyFactor = lowLevelDefensive ? 95 : (earlyLooseDefense ? 24 : 70);
    } else {
      oppPenaltyFactor = lowLevelDefensive ? 36 : (earlyLooseDefense ? 8 : 24);
    }
    int tactical =
        mySev * profile.tacticalWeight * 165
            - oppSev * profile.defenseWeight * oppPenaltyFactor;
    int ownLine = localShapeScore(row, col, color);
    int ownOpen = localOpenEndedScore(row, col, color);
    int blockLine = localShapeScore(row, col, enemy);
    int blockWeight = lowLevelDefensive ? 74 : (earlyLooseDefense ? 18 : 52);
    int nearby = adjacentStoneCount(row, col, color) + adjacentStoneCount(row, col, enemy);
    int centerBias = 14 - (Math.abs(row - CENTER) + Math.abs(col - CENTER));
    int brickPenalty = brickStackPenalty(row, col, color);
    int openingVariety = (moveCount <= 12) ? openingVarietyScore(row, col, color) : 0;
    return tactical + ownLine * 135 + ownOpen * 70 + blockLine * blockWeight + nearby * 7 + centerBias * 4
        - brickPenalty + openingVariety;
  }

  private int evaluateMove(int row, int col, int color) {
    return evaluateMove(row, col, color, getAiProfile(computerDifficulty));
  }

  private int brickStackPenalty(int row, int col, int color) {
    int jumpLinks = 0;
    int adjacent = adjacentStoneCount(row, col, color);
    int[][] ortho = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    for (int[] d : ortho) {
      int r1 = row + d[0];
      int c1 = col + d[1];
      int r2 = row + d[0] * 2;
      int c2 = col + d[1] * 2;
      if (inRange(r2, c2) && board[r2][c2] == color && inRange(r1, c1) && board[r1][c1] == EMPTY) {
        jumpLinks++;
      }
    }
    int penalty = jumpLinks * 180;
    if (jumpLinks >= 1 && adjacent == 0) {
      penalty += 140;
    }
    return penalty;
  }

  private int openingVarietyScore(int row, int col, int color) {
    int score = 0;
    int centerDist = Math.abs(row - CENTER) + Math.abs(col - CENTER);
    score += (16 - centerDist) * 6;
    if (moveCount <= 2) {
      if (centerDist > 6) {
        score -= 2200;
      }
      if (moveCount >= 1 && !hasNeighborWithin(row, col, 2)) {
        score -= 1400;
      }
    }
    int myNearby = adjacentStoneCount(row, col, color);
    if (myNearby >= 3) {
      score -= 120;
    } else if (myNearby == 0) {
      score += 40;
    }
    int hash = row * 17 + col * 31 + openingStyleBiasA;
    int hash2 = row * 23 - col * 19 + openingStyleBiasB;
    score += (hash % 11) * 2;
    score += (hash2 % 7);
    return score;
  }

  private int currentMaxContinuous(int color) {
    int best = 0;
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] != color) {
          continue;
        }
        for (int[] d : DIRS) {
          int pr = r - d[0];
          int pc = c - d[1];
          if (inRange(pr, pc) && board[pr][pc] == color) {
            continue;
          }
          int len = 1 + countOneDirection(r, c, d[0], d[1], color);
          if (len > best) {
            best = len;
          }
        }
      }
    }
    return best;
  }

  private AiProfile getAiProfile(int level) {
    int lv = Math.max(1, Math.min(10, level));
    int depth = switch (lv) {
      case 10 -> 8; // Ai급 (완성)
      case 9 -> 7;  // 준Ai급
      case 8 -> 5;  // 최상급
      case 7 -> 4;  // 상급
      case 6 -> 3;  // 상급 하위
      case 5 -> 3;  // 중상급
      case 4 -> 2;  // 중급
      case 3 -> 2;  // 초중급
      case 2 -> 1;  // 초급
      default -> 0; // 최하급
    };
    int candidate = switch (lv) {
      case 10 -> 24;
      case 9 -> 21;
      case 8 -> 19;
      case 7 -> 17;
      case 6 -> 16;
      case 5 -> 15;
      case 4 -> 14;
      case 3 -> 13;
      case 2 -> 12;
      default -> 10;
    };
    int localRadius = switch (lv) {
      case 10, 9 -> 3;
      case 8, 7 -> 3;
      case 6, 5 -> 2;
      default -> 2;
    };
    int defenseAwareness = switch (lv) {
      case 10, 9, 8, 7, 6 -> 2;
      case 5, 4, 3 -> 3;
      default -> 4;
    };
    int attackWeight = switch (lv) {
      case 10 -> 24;
      case 9 -> 21;
      case 8 -> 19;
      case 7 -> 17;
      case 6 -> 16;
      case 5 -> 15;
      case 4 -> 14;
      case 3 -> 13;
      case 2 -> 12;
      default -> 10;
    };
    int defenseWeight = switch (lv) {
      case 10 -> 8;
      case 9 -> 8;
      case 8 -> 9;
      case 7 -> 9;
      case 6 -> 11;
      case 5 -> 12;
      case 4 -> 13;
      case 3 -> 14;
      case 2 -> 15;
      default -> 16;
    };
    int tacticalWeight = switch (lv) {
      case 10 -> 20;
      case 9 -> 18;
      case 8 -> 16;
      case 7 -> 15;
      case 6 -> 14;
      case 5 -> 13;
      case 4 -> 12;
      case 3 -> 11;
      case 2 -> 9;
      default -> 6;
    };
    int mistake = switch (lv) {
      case 10 -> 0;
      case 9 -> 1;
      case 8 -> 2;
      case 7 -> 3;
      case 6 -> 4;
      case 5 -> 5;
      case 4 -> 6;
      case 3 -> 8;
      case 2 -> 10;
      default -> 14;
    };
    boolean comboProbe = lv >= 9;
    return new AiProfile(depth, candidate, localRadius, defenseAwareness, attackWeight, defenseWeight, tacticalWeight, mistake, comboProbe);
  }

  private int oppositeColor(int color) {
    return color == BLACK ? WHITE : BLACK;
  }

  private int centerMassScore(int color) {
    int score = 0;
    for (int r = 0; r < SIZE; r++) {
      for (int c = 0; c < SIZE; c++) {
        if (board[r][c] == color) {
          score += 14 - (Math.abs(r - CENTER) + Math.abs(c - CENTER));
        }
      }
    }
    return score;
  }

  private boolean hasNeighborWithin(int row, int col, int dist) {
    for (int dr = -dist; dr <= dist; dr++) {
      for (int dc = -dist; dc <= dist; dc++) {
        if (dr == 0 && dc == 0) {
          continue;
        }
        int nr = row + dr;
        int nc = col + dc;
        if (inRange(nr, nc) && board[nr][nc] != EMPTY) {
          return true;
        }
      }
    }
    return false;
  }

  private static class TacticalSnapshot {
    final int maxSeverity;
    final List<int[]> maxSeverityMoves;

    TacticalSnapshot(int maxSeverity, List<int[]> maxSeverityMoves) {
      this.maxSeverity = maxSeverity;
      this.maxSeverityMoves = maxSeverityMoves;
    }
  }

  private static class ForkThreatSnapshot {
    final int maxForkSeverity;
    final List<int[]> maxForkMoves;

    ForkThreatSnapshot(int maxForkSeverity, List<int[]> maxForkMoves) {
      this.maxForkSeverity = maxForkSeverity;
      this.maxForkMoves = maxForkMoves;
    }
  }

  private static class BuildUpThreatSnapshot {
    final int maxBuildSeverity;
    final List<int[]> maxBuildMoves;

    BuildUpThreatSnapshot(int maxBuildSeverity, List<int[]> maxBuildMoves) {
      this.maxBuildSeverity = maxBuildSeverity;
      this.maxBuildMoves = maxBuildMoves;
    }
  }

  private static class MoveTactics {
    boolean winNow;
    int open4;
    int closed4;
    int open3;
    int closed3;
    boolean doubleThreat43;
  }

  private static class PatternStats {
    int five;
    int open4;
    int closed4;
    int open3;
    int closed3;
    int open2;
    int doubleThreat43;
  }

  private static class ScoredMove {
    final int row;
    final int col;
    final int score;

    ScoredMove(int row, int col, int score) {
      this.row = row;
      this.col = col;
      this.score = score;
    }
  }

  private static class RiskMove {
    final int row;
    final int col;
    final int score;

    RiskMove(int row, int col, int score) {
      this.row = row;
      this.col = col;
      this.score = score;
    }
  }

  private static class AiProfile {
    final int searchDepth;
    final int candidateCount;
    final int localRadius;
    final int defenseAwarenessSeverity;
    final int attackWeight;
    final int defenseWeight;
    final int tacticalWeight;
    final int mistakeAllowance;
    final boolean enableComboProbe;

    AiProfile(
        int searchDepth,
        int candidateCount,
        int localRadius,
        int defenseAwarenessSeverity,
        int attackWeight,
        int defenseWeight,
        int tacticalWeight,
        int mistakeAllowance,
        boolean enableComboProbe
    ) {
      this.searchDepth = searchDepth;
      this.candidateCount = candidateCount;
      this.localRadius = localRadius;
      this.defenseAwarenessSeverity = defenseAwarenessSeverity;
      this.attackWeight = attackWeight;
      this.defenseWeight = defenseWeight;
      this.tacticalWeight = tacticalWeight;
      this.mistakeAllowance = mistakeAllowance;
      this.enableComboProbe = enableComboProbe;
    }
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

  private List<int[]> getLegalMovesForColor(int color) {
    List<int[]> moves = new ArrayList<>();
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

  private List<int[]> getLocalizedAIMovesForColor(int color, int radius) {
    List<int[]> all = getLegalMovesForColor(color);
    if (moveCount < 2) {
      return all;
    }
    List<int[]> local = new ArrayList<>();
    for (int[] mv : all) {
      if (hasNeighborWithin(mv[0], mv[1], radius)) {
        local.add(mv);
      }
    }
    return local.isEmpty() ? all : local;
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
    repaintBoardNow();
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
    repaintBoardNow();
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
    repaintBoardNow();
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
    repaintBoardNow();
  }

  private void updateTurnStatus() {
    statusLabel.setText(blackTurn ? "흑 차례" : "백 차례");
  }

  private void setThinkingStatus(boolean thinking) {
    String now = stripThinkingDecorations(statusLabel.getText());
    if (thinking) {
      statusLabel.setText(now + THINKING_SUFFIX);
    } else {
      statusLabel.setText(now);
      thinkingPreviewMarks.clear();
    }
    repaintBoardNow();
  }

  private void updateThinkingCandidate(int row, int col) {
    if (!computerThinking) {
      return;
    }
    thinkingPreviewMarks.add(row * SIZE + col);
    repaintBoardNow();
    if (boardPanel != null) {
      boardPanel.paintImmediately(boardPanel.getVisibleRect());
    }
    statusLabel.paintImmediately(statusLabel.getVisibleRect());
  }

  List<int[]> getThinkingPreviewMoves() {
    List<int[]> out = new ArrayList<>();
    if (!computerThinking || thinkingPreviewMarks.isEmpty()) {
      return out;
    }
    for (Integer v : thinkingPreviewMarks) {
      int row = v / SIZE;
      int col = v % SIZE;
      out.add(new int[]{row, col});
    }
    return out;
  }

  private String stripThinkingDecorations(String text) {
    String base = text;
    if (base.endsWith(THINKING_SUFFIX)) {
      base = base.substring(0, base.length() - THINKING_SUFFIX.length());
    }
    return base;
  }

  private boolean isSearchTimeUp() {
    return System.nanoTime() >= searchDeadlineNanos;
  }

  private int getSearchTimeLimitMillis(AiProfile profile) {
    return switch (computerDifficulty) {
      case 10 -> (moveCount < 18 ? 3500 : 7000);
      case 9 -> (moveCount < 18 ? 2500 : 5000);
      case 8 -> 2600;
      case 7 -> 2100;
      case 6 -> 1700;
      case 5 -> 1300;
      case 4 -> 1000;
      case 3 -> 800;
      case 2 -> 550;
      default -> 350;
    };
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
      int winningSpots = countWinningSpotsInDirection(row, col, d[0], d[1], BLACK).size();
      if (winningSpots >= 1) {
        fourCount++;
      }
      if (createsOpenThreeInDirection(row, col, d[0], d[1])) {
        openThreeCount++;
      }
    }
    return openThreeCount >= 2 || fourCount >= 2;
  }

  private boolean createsOpenThreeInDirection(int row, int col, int dr, int dc) {
    for (int k = -4; k <= 4; k++) {
      int r = row + dr * k;
      int c = col + dc * k;
      if (!inRange(r, c) || board[r][c] != EMPTY) {
        continue;
      }
      board[r][c] = BLACK;
      boolean legal = !isOverline(r, c, BLACK);
      boolean openFour = legal && isOpenFourLinkedToAnchor(row, col, r, c, dr, dc);
      board[r][c] = EMPTY;
      if (openFour) {
        return true;
      }
    }
    return false;
  }

  private boolean isOpenFourLinkedToAnchor(int anchorRow, int anchorCol, int secondRow, int secondCol, int dr, int dc) {
    int withAnchor = countWinningSpotsInDirection(secondRow, secondCol, dr, dc, BLACK).size();
    if (withAnchor < 2) {
      return false;
    }
    int prevAnchor = board[anchorRow][anchorCol];
    board[anchorRow][anchorCol] = EMPTY;
    int withoutAnchor = countWinningSpotsInDirection(secondRow, secondCol, dr, dc, BLACK).size();
    board[anchorRow][anchorCol] = prevAnchor;
    return withoutAnchor < 2;
  }

  private Set<Integer> countWinningSpotsInDirection(int row, int col, int dr, int dc, int color) {
    Set<Integer> wins = new HashSet<>();
    for (int start = -4; start <= 0; start++) {
      int end = start + 4;
      if (!(start <= 0 && 0 <= end)) {
        continue;
      }
      int blacks = 0;
      int empties = 0;
      int emptyOffset = 0;
      boolean blocked = false;
      for (int t = start; t <= end; t++) {
        int r = row + dr * t;
        int c = col + dc * t;
        if (!inRange(r, c) || board[r][c] == WHITE) {
          blocked = true;
          break;
        }
        if (board[r][c] == color) {
          blacks++;
        } else if (board[r][c] == EMPTY) {
          empties++;
          emptyOffset = t;
        }
      }
      if (!blocked && blacks == 4 && empties == 1) {
        int er = row + dr * emptyOffset;
        int ec = col + dc * emptyOffset;
        if (inRange(er, ec) && board[er][ec] == EMPTY) {
          board[er][ec] = color;
          boolean legalWin = (color == BLACK && isRenjuPlayPhase()) ? isExactFive(er, ec, color) : isFiveOrMore(er, ec, color);
          if (color == BLACK && isRenjuPlayPhase() && isOverline(er, ec, color)) {
            legalWin = false;
          }
          board[er][ec] = EMPTY;
          if (legalWin) {
            wins.add(emptyOffset);
          }
        }
      }
    }
    return wins;
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
