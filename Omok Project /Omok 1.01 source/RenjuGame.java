/*
 * Decompiled with CFR 0.152.
 */
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

class RenjuGame {
    static final int SIZE = 15;
    static final int EMPTY = 0;
    static final int BLACK = 1;
    static final int WHITE = 2;
    private static final int[][] DIRS = new int[][]{{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int CENTER = 7;
    private static final int MAX_SEARCH_PLY = 32;
    private static final int TT_FLAG_EXACT = 0;
    private static final int TT_FLAG_LOWER = 1;
    private static final int TT_FLAG_UPPER = 2;
    private static final long[][][] SEARCH_ZOBRIST = initSearchZobrist();
    private static final long[] TURN_ZOBRIST = initSearchTurnHash();
    private static final long[] MODE_ZOBRIST = initModeHash();
    private static final long[] PHASE_ZOBRIST = initPhaseHash();
    final int[][] board = new int[15][15];
    final int[][] moveOrder = new int[15][15];
    private final JLabel statusLabel;
    private BoardPanel boardPanel;
    private boolean blackTurn = true;
    private boolean gameOver = false;
    private int moveCount = 0;
    private int[] lastMove = null;
    private RuleMode mode = RuleMode.RENJU;
    private AdvancedPhase advancedPhase = AdvancedPhase.NONE;
    private final List<int[]> fifthCandidates = new ArrayList<int[]>();
    private final Random random = new Random();
    private boolean computerEnabled = false;
    private int computerDifficulty = 5;
    private int playerColor = 1;
    private int computerColor = 2;
    private boolean computerThinking = false;
    private static final String THINKING_SUFFIX = " (\uacc4\uc0b0\uc911...)";
    private long searchDeadlineNanos = Long.MAX_VALUE;
    private boolean searchTimedOut = false;
    private final Set<Integer> thinkingPreviewMarks = new LinkedHashSet<Integer>();
    private final int[][] historyBlack = new int[15][15];
    private final int[][] historyWhite = new int[15][15];
    private final Map<Long, TranspositionEntry> transpositionTable = new HashMap<Long, TranspositionEntry>(65536);
    private final int[][] killerMoves = new int[32][2];
    private int candidateSelectionDepth = 0;
    private int openingStyleBiasA = 0;
    private int openingStyleBiasB = 0;
    private int openingStyleBiasC = 0;
    private int openingStyleFlavor = 0;
    private boolean hasStartedGame = false;
    private boolean hiddenStage11 = false;

    private static long[][][] initSearchZobrist() {
        Random random = new Random(20260407L);
        long[][][] table = new long[15][15][3];
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                for (int k = 0; k < 3; ++k) {
                    table[i][j][k] = random.nextLong();
                }
            }
        }
        return table;
    }

    private static long[] initSearchTurnHash() {
        Random random = new Random(20260408L);
        return new long[]{0L, random.nextLong(), random.nextLong()};
    }

    private static long[] initModeHash() {
        Random random = new Random(20260409L);
        long[] table = new long[RuleMode.values().length];
        for (int i = 0; i < table.length; ++i) {
            table[i] = random.nextLong();
        }
        return table;
    }

    private static long[] initPhaseHash() {
        Random random = new Random(20260410L);
        long[] table = new long[AdvancedPhase.values().length];
        for (int i = 0; i < table.length; ++i) {
            table[i] = random.nextLong();
        }
        return table;
    }

    RenjuGame(JLabel jLabel) {
        this.statusLabel = jLabel;
        this.clearSearchArtifacts();
    }

    void setComputerEnabled(boolean bl) {
        this.computerEnabled = bl;
        this.computerColor = !bl ? 2 : (this.playerColor == 1 ? 2 : 1);
        this.updateWindowTitle();
    }

    void setComputerDifficulty(int n) {
        this.computerDifficulty = Math.max(1, Math.min(10, n));
        this.updateWindowTitle();
    }

    void setPlayerColor(int n) {
        if (n != 1 && n != 2) {
            return;
        }
        this.playerColor = n;
        this.computerColor = this.playerColor == 1 ? 2 : 1;
        this.updateWindowTitle();
    }

    void tryComputerMove() {
        if (this.computerThinking || !this.shouldComputerMoveNow()) {
            return;
        }
        this.computerThinking = true;
        this.setThinkingStatus(true);
        SwingUtilities.invokeLater(this::runComputerMoveLoop);
    }

    private void runComputerMoveLoop() {
        try {
            while (this.shouldComputerMoveNow()) {
                int[] nArray = this.pickInstantForcedMove();
                if (nArray != null) {
                    this.thinkingPreviewMarks.clear();
                    this.repaintBoardNow();
                    this.play(nArray[0], nArray[1], true);
                    continue;
                }
                int[] nArray2 = this.pickComputerMove();
                if (nArray2 == null) {
                    break;
                }
                this.thinkingPreviewMarks.clear();
                this.repaintBoardNow();
                this.play(nArray2[0], nArray2[1], true);
            }
        }
        finally {
            this.computerThinking = false;
            this.setThinkingStatus(false);
        }
    }

    private int[] pickInstantForcedMove() {
        if (!this.shouldComputerMoveNow()) {
            return null;
        }
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
            return null;
        }
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase != AdvancedPhase.NONE && this.advancedPhase != AdvancedPhase.NORMAL) {
            return null;
        }
        int n = this.getCurrentTurnColor();
        int n2 = this.oppositeColor(n);
        AiProfile aiProfile = this.getAiProfile(this.computerDifficulty);
        if (n == 1 && this.moveCount == 0 && this.board[7][7] == 0) {
            return new int[]{7, 7};
        }
        if (n == 2 && this.moveCount == 1) {
            int[] openingResponse = this.pickOpeningResponseMove(n, aiProfile);
            if (openingResponse != null) {
                return openingResponse;
            }
        }
        List<int[]> list = this.getLocalizedAIMovesForColor(n, aiProfile.localRadius);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n);
        }
        if (list.isEmpty()) {
            return null;
        }
        List<int[]> list2 = this.findImmediateWinningMoves(list, n);
        if (list2.size() == 1) {
            return new int[]{list2.get(0)[0], list2.get(0)[1]};
        }
        if (list2.size() > 1) {
            return this.chooseTopMoveWithNoise(list2, n, aiProfile, 1);
        }
        List<int[]> list3 = this.findImmediateWinningMoves(this.getLegalMovesForColor(n2), n2);
        if (list3.isEmpty()) {
            return null;
        }
        List<int[]> list4 = this.intersectMoves(list, list3);
        if (list4.size() == 1) {
            return new int[]{list4.get(0)[0], list4.get(0)[1]};
        }
        if (list4.size() > 1) {
            return this.chooseTopMoveWithNoise(list4, n, aiProfile, 1);
        }
        return null;
    }

    void startNewGameWithModeSelection(JFrame jFrame) {
        boolean bl;
        JComboBox<String> jComboBox = new JComboBox<String>(new String[]{"\ubb34\uaddc\uce59", "\ub80c\uc8fc", "\ub80c\uc8fc \ubcf4\uac15\ub8f0"});
        JCheckBox jCheckBox = new JCheckBox("\ucef4\ud4e8\ud130\uc640 \ub450\uae30", this.computerEnabled);
        JComboBox<String> jComboBox2 = new JComboBox<String>(new String[]{"\ud50c\ub808\uc774\uc5b4: \ud751", "\ud50c\ub808\uc774\uc5b4: \ubc31"});
        jComboBox2.setSelectedIndex(this.playerColor == 1 ? 0 : 1);
        JSlider jSlider = new JSlider(0, 1, 10, this.computerDifficulty);
        jSlider.setMajorTickSpacing(1);
        jSlider.setPaintTicks(true);
        jSlider.setPaintLabels(true);
        jComboBox.setSelectedIndex(this.mode == RuleMode.FREESTYLE ? 0 : (this.mode == RuleMode.RENJU ? 1 : 2));
        jComboBox2.setEnabled(this.computerEnabled);
        jSlider.setEnabled(this.computerEnabled);
        jCheckBox.addActionListener(actionEvent -> {
            boolean selected = jCheckBox.isSelected();
            jComboBox2.setEnabled(selected);
            jSlider.setEnabled(selected);
        });
        JPanel jPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        jPanel.add(new JLabel("\uaddc\uce59 \uc120\ud0dd"));
        jPanel.add(jComboBox);
        jPanel.add(jCheckBox);
        jPanel.add(jComboBox2);
        jPanel.add(new JLabel("\ucef4\ud4e8\ud130 \ub09c\uc774\ub3c4 (1~10)"));
        jPanel.add(jSlider);
        JOptionPane jOptionPane = new JOptionPane(jPanel, -1, 2);
        JDialog jDialog = jOptionPane.createDialog(jFrame, "\uac8c\uc784 \uc2dc\uc791 \uc124\uc815");
        boolean[] blArray = new boolean[]{false};
        SwingUtilities.invokeLater(() -> this.attachHiddenStageTriggerToCancel(jDialog, () -> {
            blArray[0] = true;
            jOptionPane.setValue(0);
            jDialog.dispose();
        }));
        jDialog.setVisible(true);
        Object object = jOptionPane.getValue();
        boolean bl2 = bl = blArray[0] || object instanceof Integer && (Integer)object == 0;
        if (!bl) {
            if (!this.hasStartedGame) {
                jFrame.dispose();
                System.exit(0);
            }
            return;
        }
        int n = jComboBox.getSelectedIndex();
        this.mode = n == 0 ? RuleMode.FREESTYLE : (n == 2 ? RuleMode.RENJU_ADVANCED : RuleMode.RENJU);
        this.hiddenStage11 = blArray[0];
        boolean bl3 = jCheckBox.isSelected() || this.hiddenStage11;
        this.setComputerEnabled(bl3);
        this.setPlayerColor(jComboBox2.getSelectedIndex() == 0 ? 1 : 2);
        this.setComputerDifficulty(this.hiddenStage11 ? 10 : jSlider.getValue());
        this.reset();
    }

    void setBoardPanel(BoardPanel boardPanel) {
        this.boardPanel = boardPanel;
    }

    void reset() {
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                this.board[i][j] = 0;
                this.moveOrder[i][j] = 0;
                this.historyBlack[i][j] = 0;
                this.historyWhite[i][j] = 0;
            }
        }
        this.blackTurn = true;
        this.gameOver = false;
        this.moveCount = 0;
        this.lastMove = null;
        this.computerColor = this.playerColor == 1 ? 2 : 1;
        this.fifthCandidates.clear();
        if (this.mode == RuleMode.RENJU_ADVANCED) {
            this.advancedPhase = AdvancedPhase.OPENING_1_BLACK_CENTER;
            this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 1\uc218: \ucc9c\uc6d0(\uc911\uc559)\uc5d0 \ub450\uc138\uc694.");
        } else {
            this.advancedPhase = AdvancedPhase.NONE;
            this.statusLabel.setText(this.blackTurn ? "\ud751 \ucc28\ub840" : "\ubc31 \ucc28\ub840");
        }
        this.openingStyleBiasA = this.random.nextInt(9) - 4;
        this.openingStyleBiasB = this.random.nextInt(9) - 4;
        this.openingStyleBiasC = this.random.nextInt(13) - 6;
        this.openingStyleFlavor = this.random.nextInt(4);
        this.hasStartedGame = true;
        this.updateWindowTitle();
        this.repaintBoardNow();
        this.tryComputerMove();
    }

    private String getRuleNameForTitle() {
        return switch (this.mode.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> "\ubb34\uaddc\uce59";
            case 1 -> "\ub80c\uc8fc\ub8f0";
            case 2 -> "\ub80c\uc8fc \ubcf4\uac15\ub8f0";
        };
    }

    private void updateWindowTitle() {
        Window window = SwingUtilities.getWindowAncestor(this.statusLabel);
        if (window == null && this.boardPanel != null) {
            window = SwingUtilities.getWindowAncestor(this.boardPanel);
        }
        if (!(window instanceof JFrame)) {
            return;
        }
        JFrame jFrame = (JFrame)window;
        String string = this.getRuleNameForTitle();
        int n = this.hiddenStage11 ? 11 : this.computerDifficulty;
        String string2 = this.computerEnabled ? String.format("\uc624\ubaa9 - \ucef4\ud4e8\ud130\uc640 \ud50c\ub808\uc774 (%d\ub2e8\uacc4 - %s)", n, string) : String.format("\uc624\ubaa9 (%s)", string);
        jFrame.setTitle(string2);
    }

    private void attachHiddenStageTriggerToCancel(Window window, Runnable runnable) {
        if (window == null || runnable == null) {
            return;
        }
        String string = UIManager.getString("OptionPane.cancelButtonText");
        this.attachHiddenStageTriggerRecursive(window, string, runnable);
    }

    private void attachHiddenStageTriggerRecursive(Component component, String string, final Runnable runnable) {
        if (component instanceof JButton) {
            JButton button = (JButton)component;
            String text = button.getText();
            boolean isCancelButton = string != null && string.equals(text) || "Cancel".equalsIgnoreCase(text) || "\ucde8\uc18c".equals(text);
            if (isCancelButton) {
                button.addMouseListener(new MouseAdapter(){
                    @Override
                    public void mousePressed(MouseEvent mouseEvent) {
                        if (SwingUtilities.isRightMouseButton(mouseEvent)) {
                            runnable.run();
                        }
                    }
                });
            }
        }
        if (component instanceof Container) {
            Container container = (Container)component;
            for (Component component2 : container.getComponents()) {
                this.attachHiddenStageTriggerRecursive(component2, string, runnable);
            }
        }
    }

    int[] getLastMove() {
        return this.lastMove;
    }

    int getMoveNumber(int n, int n2) {
        return this.moveOrder[n][n2];
    }

    boolean shouldShowForbiddenHints() {
        if (this.gameOver || !this.blackTurn || !this.isRenjuStyle()) {
            return false;
        }
        if (this.mode == RuleMode.RENJU_ADVANCED) {
            return this.advancedPhase == AdvancedPhase.NORMAL;
        }
        return true;
    }

    boolean shouldShowOpeningRangeHints() {
        return this.mode == RuleMode.RENJU_ADVANCED && !this.gameOver && (this.advancedPhase == AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER || this.advancedPhase == AdvancedPhase.OPENING_3_BLACK_WITHIN_TWO);
    }

    boolean isOpeningRangeCell(int n, int n2) {
        if (!this.shouldShowOpeningRangeHints() || !this.inRange(n, n2) || this.board[n][n2] != 0) {
            return false;
        }
        if (this.advancedPhase == AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER) {
            return this.isAroundCenter8(n, n2);
        }
        return this.isWithinCenterFiveByFive(n, n2);
    }

    boolean shouldShowSymmetryForbiddenHints() {
        return this.mode == RuleMode.RENJU_ADVANCED && !this.gameOver && this.advancedPhase == AdvancedPhase.OPENING_5_BLACK_SECOND && this.fifthCandidates.size() == 1;
    }

    boolean isForbiddenCellForBlack(int n, int n2) {
        if (!this.isRenjuStyle()) {
            return false;
        }
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return false;
        }
        this.board[n][n2] = 1;
        boolean bl = this.isOverline(n, n2, 1) ? true : (this.isExactFive(n, n2, 1) ? false : this.isForbiddenByRenju(n, n2));
        this.board[n][n2] = 0;
        return bl;
    }

    boolean isSymmetryForbiddenCellForSecondFifth(int n, int n2) {
        if (!this.shouldShowSymmetryForbiddenHints()) {
            return false;
        }
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return false;
        }
        return this.isSecondFifthSymmetryEquivalent(n, n2);
    }

    void play(int n, int n2) {
        this.play(n, n2, false);
    }

    private void play(int n, int n2, boolean bl) {
        if (this.gameOver || !this.inRange(n, n2)) {
            return;
        }
        if (!bl && this.shouldComputerMoveNow()) {
            return;
        }
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
            this.handleAdvancedRemoveChoice(n, n2);
            return;
        }
        if (this.board[n][n2] != 0) {
            return;
        }
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase != AdvancedPhase.NONE && this.advancedPhase != AdvancedPhase.NORMAL) {
            this.handleAdvancedOpeningPlacement(n, n2);
            return;
        }
        this.placeStandardMove(n, n2);
    }

    private boolean shouldComputerMoveNow() {
        if (!this.computerEnabled || this.gameOver) {
            return false;
        }
        return this.getCurrentTurnColor() == this.computerColor;
    }

    private int getCurrentTurnColor() {
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
            return 2;
        }
        return this.blackTurn ? 1 : 2;
    }

    private int[] pickComputerMove() {
        List<int[]> list;
        List<int[]> list2;
        List<int[]> list3;
        int n;
        int n2;
        List<int[]> enemyCandidates;
        int n3;
        List<int[]> blockingMoves;
        TacticalSnapshot enemyThreat;
        List<int[]> list5 = this.getLegalMovesForCurrentTurn();
        if (list5.isEmpty()) {
            return null;
        }
        int n4 = this.getCurrentTurnColor();
        AiProfile aiProfile = this.getAiProfile(this.computerDifficulty);
        this.clearSearchArtifacts();
        this.searchTimedOut = false;
        this.searchDeadlineNanos = System.nanoTime() + (long)this.getSearchTimeLimitMillis(aiProfile) * 1000000L;
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase == AdvancedPhase.OPENING_5_WHITE_REMOVE) {
            return this.pickRemoveCandidateMove();
        }
        if (this.mode == RuleMode.RENJU_ADVANCED && this.advancedPhase != AdvancedPhase.NONE && this.advancedPhase != AdvancedPhase.NORMAL) {
            return this.chooseTopMoveWithNoise(list5, n4, aiProfile, 1);
        }
        list5 = this.getLocalizedAIMovesForColor(n4, aiProfile.localRadius);
        if (list5.isEmpty()) {
            list5 = this.getLegalMovesForColor(n4);
        }
        int n5 = this.oppositeColor(n4);
        List<int[]> list6 = null;
        boolean bl = false;
        int n6 = this.getMinSearchCandidates(aiProfile);
        List<int[]> list7 = this.findImmediateWinningMoves(list5, n4);
        if (!list7.isEmpty()) {
            list6 = list7;
            bl = true;
        } else {
            List<int[]> enemyWins = this.findImmediateWinningMoves(this.getLegalMovesForColor(n5), n5);
            if (!enemyWins.isEmpty() && !(blockingMoves = this.intersectMoves(list5, enemyWins)).isEmpty()) {
                list6 = blockingMoves;
                bl = true;
            }
        }
        enemyThreat = new TacticalSnapshot(0, new ArrayList<int[]>());
        TacticalSnapshot myThreat = new TacticalSnapshot(0, new ArrayList<int[]>());
        ForkThreatSnapshot forkThreatSnapshot = new ForkThreatSnapshot(0, new ArrayList<int[]>());
        ForkThreatSnapshot forkThreatSnapshot2 = new ForkThreatSnapshot(0, new ArrayList<int[]>());
        BuildUpThreatSnapshot buildUpThreatSnapshot = new BuildUpThreatSnapshot(0, new ArrayList<int[]>());
        BuildUpThreatSnapshot buildUpThreatSnapshot2 = new BuildUpThreatSnapshot(0, new ArrayList<int[]>());
        if (list6 == null) {
            List<int[]> list8;
            List<int[]> list9;
            List<int[]> list10;
            List<int[]> list11;
            List<int[]> list12;
            List<int[]> list13;
            List<int[]> list14;
            List<int[]> list15;
            List<int[]> list16;
            List<int[]> list17;
            int[] nArray;
            boolean bl2 = this.computerDifficulty >= 2 && this.moveCount <= 20;
            n3 = this.computerDifficulty >= 2 && this.computerDifficulty <= 9 && this.moveCount <= 15 ? 1 : 0;
            boolean bl3 = this.computerDifficulty >= 6;
            enemyCandidates = aiProfile.searchDepth >= 5 || bl2 || bl3 ? this.getLegalMovesForColor(n5) : this.getLocalizedAIMovesForColor(n5, aiProfile.localRadius);
            enemyThreat = this.collectTacticalSnapshot(enemyCandidates, n5);
            myThreat = this.collectTacticalSnapshot(list5, n4);
            forkThreatSnapshot = this.collectForkThreatSnapshot(enemyCandidates, n5);
            forkThreatSnapshot2 = this.collectForkThreatSnapshot(list5, n4);
            if (this.shouldProbeBuildUp(aiProfile, enemyCandidates.size())) {
                buildUpThreatSnapshot = this.collectBuildUpThreatSnapshot(enemyCandidates, n5, aiProfile, 12);
            }
            if (this.shouldProbeBuildUp(aiProfile, list5.size())) {
                buildUpThreatSnapshot2 = this.collectBuildUpThreatSnapshot(list5, n4, aiProfile, 12);
            }
            PatternStats patternStats = this.evaluatePatternsForColor(n5);
            n2 = patternStats.open4 * 6 + patternStats.closed4 * 3 + patternStats.open3 * 2;
            boolean strongLinePressure = patternStats.open4 > 0 || patternStats.open3 > 0 || n2 >= 3;
            boolean moderateLinePressure = strongLinePressure || n2 >= 2;
            if (list6 == null && strongLinePressure) {
                List<int[]> tacticalBlocks = this.intersectMoves(list5, enemyThreat.maxSeverityMoves);
                List<int[]> directLineDefense = this.chooseStrictLineDefenseMoves(list5, n4, aiProfile);
                List<int[]> reinforcedDefense = this.chooseThreatReductionMoves(list5, n4, aiProfile, Math.max(6, Math.min(14, aiProfile.candidateCount / 2 + 3)));
                List<int[]> mergedDefense = this.mergeDistinctMoves(tacticalBlocks, directLineDefense, Math.max(6, Math.min(12, aiProfile.candidateCount)));
                mergedDefense = this.mergeDistinctMoves(mergedDefense, reinforcedDefense, Math.max(8, Math.min(16, aiProfile.candidateCount + 2)));
                if (!mergedDefense.isEmpty()) {
                    list6 = mergedDefense;
                    bl = true;
                }
            }
            n = aiProfile.defenseAwarenessSeverity;
            if (this.moveCount <= 20 && this.computerDifficulty >= 2) {
                n = this.computerDifficulty <= 5 ? Math.max(n, 1) : Math.max(n, 2);
            }
            if (this.moveCount <= 24 && moderateLinePressure) {
                n = Math.max(1, n - (strongLinePressure ? 2 : 1));
            }
            int n7 = enemyThreat.maxSeverity;
            if (bl2 && n7 >= 2) {
                int n8;
                if (n3 != 0) {
                    n8 = 18;
                } else {
                    int n9 = switch (this.computerDifficulty) {
                        case 7, 8, 9 -> 14;
                        case 4, 5, 6 -> 13;
                        case 2, 3 -> 12;
                        case 10 -> 15;
                        default -> 11;
                    };
                    int n10 = Math.max(0, Math.min(5, this.moveCount - 15));
                    n8 = (18 * (5 - n10) + n9 * n10 + 2) / 5;
                }
                n7 = (n7 * n8 + 9) / 10;
            }
            if (list6 == null && n3 != 0 && enemyThreat.maxSeverity >= 2 && !enemyThreat.maxSeverityMoves.isEmpty()) {
                int[] nArray2;
                if (this.computerDifficulty <= 5 && (nArray2 = this.chooseBestThreatReductionMove(list5, n4, aiProfile)) != null) {
                    return nArray2;
                }
                List<int[]> list18 = this.intersectMoves(list5, enemyThreat.maxSeverityMoves);
                if (!list18.isEmpty()) {
                    list6 = list18;
                    bl = true;
                }
            }
            if (this.computerDifficulty >= 2 && this.computerDifficulty <= 7 && this.moveCount <= 25 && n2 >= 2 && (nArray = this.chooseBestThreatReductionMove(list5, n4, aiProfile)) != null) {
                return nArray;
            }
            if ((n7 >= n || moderateLinePressure && enemyThreat.maxSeverity >= Math.max(1, n - 1)) && !(list17 = this.intersectMoves(list5, enemyThreat.maxSeverityMoves)).isEmpty()) {
                if (n7 >= 4) {
                    list6 = list17;
                    bl = true;
                } else if (bl2 || moderateLinePressure || this.computerDifficulty <= 6 || myThreat.maxSeverity < enemyThreat.maxSeverity) {
                    list6 = list17;
                }
            }
            if (this.computerDifficulty >= 6 && this.moveCount <= 24 && (enemyThreat.maxSeverity >= 2 || moderateLinePressure) && !(list16 = this.mergeDistinctMoves(list15 = this.intersectMoves(list5, enemyThreat.maxSeverityMoves), list14 = this.chooseThreatReductionMoves(list5, n4, aiProfile, Math.max(6, Math.min(12, aiProfile.candidateCount / 2 + 2))), Math.max(8, Math.min(14, aiProfile.candidateCount)))).isEmpty()) {
                list6 = list16;
                bl = true;
            }
            if (!(list6 != null || n7 < 2 || !bl2 && myThreat.maxSeverity >= 4 || enemyThreat.maxSeverityMoves.isEmpty() || (list13 = this.intersectMoves(list5, enemyThreat.maxSeverityMoves)).isEmpty())) {
                list6 = list13;
            }
            if (list6 == null && this.computerDifficulty >= 5 && this.moveCount <= 24 && moderateLinePressure && !(list12 = this.chooseStrictLineDefenseMoves(list5, n4, aiProfile)).isEmpty()) {
                list6 = list12;
                bl = true;
            }
            if (!(list6 != null || this.computerDifficulty < 5 || !strongLinePressure || (list11 = this.chooseThreatReductionMoves(list5, n4, aiProfile, Math.max(5, Math.min(12, aiProfile.candidateCount / 2 + 1)))).isEmpty())) {
                list6 = list11;
                bl = true;
            }
            if (list6 == null && this.computerDifficulty >= 6 && (n7 >= 2 || moderateLinePressure) && !(list10 = this.chooseThreatReductionMoves(list5, n4, aiProfile, Math.max(4, Math.min(9, aiProfile.candidateCount / 2 + 1)))).isEmpty()) {
                list6 = list10;
                bl = true;
            }
            if (list6 == null && forkThreatSnapshot.maxForkSeverity >= 4 && !forkThreatSnapshot.maxForkMoves.isEmpty() && !(list9 = this.intersectMoves(list5, forkThreatSnapshot.maxForkMoves)).isEmpty()) {
                list6 = list9;
                bl = true;
            }
            if (list6 == null && buildUpThreatSnapshot.maxBuildSeverity >= 4 && !buildUpThreatSnapshot.maxBuildMoves.isEmpty() && !(list8 = this.intersectMoves(list5, buildUpThreatSnapshot.maxBuildMoves)).isEmpty()) {
                list6 = list8;
                bl = true;
            }
            if (list6 == null && myThreat.maxSeverity >= 4 && !myThreat.maxSeverityMoves.isEmpty()) {
                list6 = myThreat.maxSeverityMoves;
            } else if (list6 == null && forkThreatSnapshot2.maxForkSeverity >= 4 && !forkThreatSnapshot2.maxForkMoves.isEmpty()) {
                list6 = forkThreatSnapshot2.maxForkMoves;
            } else if (list6 == null && buildUpThreatSnapshot2.maxBuildSeverity >= 4 && !buildUpThreatSnapshot2.maxBuildMoves.isEmpty()) {
                list6 = buildUpThreatSnapshot2.maxBuildMoves;
            }
        }
        if (list6 != null) {
            if (bl) {
                n3 = Math.max(2, Math.min(aiProfile.candidateCount, list6.size()));
                list3 = this.selectSearchCandidates(list6, n4, n3);
            } else {
                n3 = Math.max(4, Math.min(aiProfile.candidateCount, Math.max(6, list6.size())));
                List<int[]> list19 = this.selectSearchCandidates(list6, n4, n3);
                List<int[]> baseCandidates = this.selectSearchCandidates(list5, n4, Math.max(aiProfile.candidateCount, n6));
                list3 = this.mergeDistinctMoves(list19, baseCandidates, Math.max(aiProfile.candidateCount, n6));
            }
        } else {
            list3 = this.selectSearchCandidates(list5, n4, Math.max(aiProfile.candidateCount, n6));
        }
        list3 = this.expandCandidateBreadth(list3, list5, n4, aiProfile, n6);
        list3 = this.injectCriticalTacticalCandidates(list3, list5, n4, aiProfile, Math.max(aiProfile.candidateCount + 6, n6 + 4), true);
            if (aiProfile.searchDepth >= 7 && !(list2 = this.intersectMoves(list5, list = this.selectThreatSeedMoves(n5, aiProfile, 10))).isEmpty()) {
               list3 = this.mergeDistinctMoves(list3, list2, Math.max(aiProfile.candidateCount + 8, n6 + 8));
            }
            if (aiProfile.searchDepth >= 4) {
               int n11 = Math.max(4, Math.min(8, aiProfile.candidateCount / 3 + 2));
               List<int[]> list20 = this.selectThreatSeedMoves(n4, aiProfile, n11);
               if (!list20.isEmpty()) {
                  list3 = this.mergeDistinctMoves(list3, list20, Math.max(aiProfile.candidateCount + 6, n6 + 6));
               }
            }
            if (aiProfile.searchDepth >= 5) {
               int n11 = bl ? 1 : n6;
               list3 = this.filterUnsafeCandidates(list3, n4, aiProfile, n11);
               list3 = this.expandCandidateBreadth(list3, list5, n4, aiProfile, n6);
               list3 = this.injectCriticalTacticalCandidates(list3, list5, n4, aiProfile, Math.max(aiProfile.candidateCount + 6, n6 + 4), true);
            }
        if (aiProfile.searchDepth >= 7) {
            list3 = this.enforceForkSafetyOnCandidates(list3, list5, n4, aiProfile, bl ? 1 : n6);
        }
        int n12 = this.computerDifficulty >= 10 ? 1 : 2;
        int[] bestMove = this.chooseTopMoveWithNoise(list3, n4, aiProfile, n12);
        int n13 = aiProfile.searchDepth;
        if (this.hiddenStage11) {
            n2 = bl || enemyThreat.maxSeverity >= 3 || forkThreatSnapshot.maxForkSeverity >= 4 || buildUpThreatSnapshot.maxBuildSeverity >= 4 ? 1 : 0;
            n13 = n2 != 0 ? 22 : 12;
            n = n2 != 0 ? 20000 : 18000;
            this.searchDeadlineNanos = System.nanoTime() + (long)n * 1000000L;
        }
        for (n2 = 1; n2 <= n13 && !this.isSearchTimeUp(); ++n2) {
            int[] nArray3 = this.searchBestMoveAtDepth(list3, n4, aiProfile, n2);
            if (this.searchTimedOut || nArray3 == null) break;
            bestMove = nArray3;
        }
        if (aiProfile.searchDepth >= 5) {
            bestMove = this.refineByBlunderCheck(bestMove, list3, n4, aiProfile);
        }
        return this.enforceCriticalMovePriority(bestMove, n4, list5, list3, aiProfile);
    }

    private int[] enforceCriticalMovePriority(int[] nArray, int n, List<int[]> list, List<int[]> list2, AiProfile aiProfile) {
        List<int[]> list3;
        List<int[]> list4 = list == null || list.isEmpty() ? this.getLegalMovesForColor(n) : list;
        List<int[]> list5 = this.findImmediateWinningMoves(list4, n);
        if (!list5.isEmpty()) {
            return this.chooseTopMoveWithNoise(list5, n, aiProfile, 1);
        }
        int n2 = this.oppositeColor(n);
        List<int[]> list6 = this.findImmediateWinningMoves(this.getLegalMovesForColor(n2), n2);
        if (!(list6.isEmpty() || (list3 = this.intersectMoves(list4, list6)).isEmpty() || nArray != null && this.containsMove(list3, nArray[0], nArray[1]))) {
            return this.chooseTopMoveWithNoise(list3, n, aiProfile, 1);
        }
        PatternStats patternStats = this.evaluatePatternsForColor(n2);
        if (patternStats.open4 > 0 || patternStats.open3 > 0 || patternStats.closed4 > 0) {
            List<int[]> list7 = this.chooseStrictLineDefenseMoves(list4, n, aiProfile);
            if (!list7.isEmpty() && (nArray == null || !this.containsMove(list7, nArray[0], nArray[1]))) {
                return this.chooseTopMoveWithNoise(list7, n, aiProfile, 1);
            }
        }
        if (nArray != null) {
            return nArray;
        }
        if (list2 != null && !list2.isEmpty()) {
            return this.chooseTopMoveWithNoise(list2, n, aiProfile, 1);
        }
        return null;
    }

    private boolean containsMove(List<int[]> list, int n, int n2) {
        for (int[] nArray : list) {
            if (nArray[0] != n || nArray[1] != n2) continue;
            return true;
        }
        return false;
    }

    private int[] refineByBlunderCheck(int[] nArray, List<int[]> list, int n, AiProfile aiProfile) {
        int n2;
        int n3;
        int n4;
        int n5;
        if (nArray == null || list.isEmpty()) {
            return nArray;
        }
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        int n6 = this.enemyImmediateWinningCountAfterMove(nArray[0], nArray[1], n, aiProfile);
        if (n6 > 0) {
            int[] nArray2 = null;
            n5 = Integer.MIN_VALUE;
            for (int[] nArray3 : list) {
                n4 = this.enemyImmediateWinningCountAfterMove(nArray3[0], nArray3[1], n, aiProfile);
                if (n4 != 0 || (n3 = this.evaluateMove(nArray3[0], nArray3[1], n, aiProfile)) <= n5) continue;
                n5 = n3;
                nArray2 = nArray3;
            }
            if (nArray2 != null) {
                return nArray2;
            }
        }
        int n7 = this.enemyMaxTacticalSeverityAfterMove(nArray[0], nArray[1], n, aiProfile);
        n5 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
        int n8 = n2 = bl ? this.enemyBuildUpSeverityAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
        if (n7 <= 3 && n5 <= 2 && n2 <= 3) {
            return nArray;
        }
        int n9 = n7;
        n4 = n2;
        n3 = Integer.MIN_VALUE;
        int[] nArray4 = nArray;
        for (int[] nArray5 : list) {
            int n10 = this.enemyMaxTacticalSeverityAfterMove(nArray5[0], nArray5[1], n, aiProfile);
            int n11 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(nArray5[0], nArray5[1], n, aiProfile) : 0;
            int n12 = bl ? this.enemyBuildUpSeverityAfterMove(nArray5[0], nArray5[1], n, aiProfile) : 0;
            if (n12 > n4 || n10 > n9) continue;
            int n13 = this.evaluateMove(nArray5[0], nArray5[1], n, aiProfile);
            int n14 = n13 - n11 * 1200 - n12 * 6000;
            if (n12 >= n4 && n10 >= n9 && n14 <= n3) continue;
            n4 = n12;
            n9 = n10;
            n3 = n14;
            nArray4 = nArray5;
        }
        return this.stabilizeOpaqueMoveChoice(nArray4, list, n, aiProfile, bl);
    }

    private int[] stabilizeOpaqueMoveChoice(int[] nArray, List<int[]> list, int n, AiProfile aiProfile, boolean bl) {
        int n2;
        if (nArray == null || list == null || list.isEmpty()) {
            return nArray;
        }
        int n3 = this.enemyImmediateWinningCountAfterMove(nArray[0], nArray[1], n, aiProfile);
        int n4 = this.enemyMaxTacticalSeverityAfterMove(nArray[0], nArray[1], n, aiProfile);
        int n5 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
        int n6 = bl ? this.enemyBuildUpSeverityAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
        int n7 = this.enemyLineDangerAfterMove(nArray[0], nArray[1], n);
        boolean bl2 = n3 > 0 || n4 >= 4 || n5 >= 4 || n6 >= 4 || n7 >= 6;
        int n8 = n2 = this.resistantMoveScore(nArray[0], nArray[1], n, aiProfile, bl);
        int[] nArray2 = nArray;
        for (int[] nArray3 : list) {
            int n9 = this.resistantMoveScore(nArray3[0], nArray3[1], n, aiProfile, bl);
            if (n9 <= n8) continue;
            n8 = n9;
            nArray2 = nArray3;
        }
        int n10 = bl2 ? 1 : 3500;
        return n8 >= n2 + n10 ? nArray2 : nArray;
    }

    private int resistantMoveScore(int n, int n2, int n3, AiProfile aiProfile, boolean bl) {
        int n4 = this.enemyImmediateWinningCountAfterMove(n, n2, n3, aiProfile);
        int n5 = this.enemyMaxTacticalSeverityAfterMove(n, n2, n3, aiProfile);
        int n6 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(n, n2, n3, aiProfile) : 0;
        int n7 = bl ? this.enemyBuildUpSeverityAfterMove(n, n2, n3, aiProfile) : 0;
        int n8 = this.enemyLineDangerAfterMove(n, n2, n3);
        MoveTactics moveTactics = this.analyzeMoveTactics(n, n2, n3);
        int n9 = this.tacticalSeverity(moveTactics);
        int n10 = this.forkSeverityIfPlaced(n, n2, n3);
        int n11 = this.strongThreatArms(n, n2, n3);
        int n12 = this.adjacentStoneCount(n, n2, n3) + this.adjacentStoneCount(n, n2, this.oppositeColor(n3));
        int n13 = this.evaluateMove(n, n2, n3, aiProfile);
        int n14 = this.hasNeighborWithin(n, n2, 2) ? 0 : 1600;
        return -n4 * 220000 - n5 * 24000 - n8 * 11000 - n6 * 18000 - n7 * 16000 + n9 * 8500 + n10 * 7500 + n11 * 1800 + n12 * 260 + n13 / 8 - n14;
    }

    private List<int[]> filterUnsafeCandidates(List<int[]> list, int n, AiProfile aiProfile, int n2) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        int n3 = Math.max(1, n2);
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        ArrayList<RiskMove> arrayList2 = new ArrayList<RiskMove>();
        int n4 = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); ++i) {
            int[] move = list.get(i);
            int n5 = this.enemyImmediateWinningCountAfterMove(move[0], move[1], n, aiProfile);
            int n6 = this.enemyMaxTacticalSeverityAfterMove(move[0], move[1], n, aiProfile);
            int n7 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(move[0], move[1], n, aiProfile) : 0;
            int n8 = bl && i < 10 ? this.enemyBuildUpSeverityAfterMove(move[0], move[1], n, aiProfile) : 0;
            int n9 = this.brickStackPenalty(move[0], move[1], n);
            boolean bl2 = n9 >= 320 && n6 >= 2 || n7 >= 4 || n8 >= 4;
            int n10 = n6 + n5 * 3 + n7 * 2 + n8 * 3 + (bl2 ? 2 : 0);
            if (n5 == 0 && n6 <= 4 && n8 <= 3 && !bl2) {
                arrayList.add(move);
            }
            arrayList2.add(new RiskMove(move[0], move[1], n10));
            n4 = Math.min(n4, n10);
        }
        if (arrayList.size() >= n3) {
            return arrayList;
        }
        if (!arrayList.isEmpty()) {
            HashSet<String> hashSet = new HashSet<String>();
            for (int[] nArray : arrayList) {
                hashSet.add(nArray[0] + "," + nArray[1]);
            }
            arrayList2.sort((riskMove, riskMove2) -> Integer.compare(riskMove.score, riskMove2.score));
            for (RiskMove riskMove3 : arrayList2) {
                if (arrayList.size() >= n3) break;
                String string = riskMove3.row + "," + riskMove3.col;
                if (hashSet.contains(string)) continue;
                arrayList.add(new int[]{riskMove3.row, riskMove3.col});
                hashSet.add(string);
            }
            return arrayList;
        }
        ArrayList<int[]> arrayList3 = new ArrayList<int[]>();
        for (RiskMove riskMove4 : arrayList2) {
            if (riskMove4.score != n4) continue;
            arrayList3.add(new int[]{riskMove4.row, riskMove4.col});
        }
        return arrayList3.isEmpty() ? list : arrayList3;
    }

    private List<int[]> expandCandidateBreadth(List<int[]> list, List<int[]> list2, int n, AiProfile aiProfile, int n2) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        int n3 = Math.min(list2.size(), Math.max(1, n2));
        if (list.size() >= n3) {
            return list;
        }
        ArrayList<int[]> arrayList = new ArrayList<int[]>(list);
        HashSet<String> hashSet = new HashSet<String>();
        for (int[] object2 : arrayList) {
            hashSet.add(object2[0] + "," + object2[1]);
        }
        List<int[]> list3 = this.selectSearchCandidates(list2, n, list2.size());
        Iterator iterator = list3.iterator();
        while (iterator.hasNext()) {
            int[] nArray = (int[])iterator.next();
            if (arrayList.size() >= n3) break;
            String string = nArray[0] + "," + nArray[1];
            if (hashSet.contains(string)) continue;
            arrayList.add(nArray);
            hashSet.add(string);
        }
        return arrayList;
    }

    private List<int[]> injectCriticalTacticalCandidates(List<int[]> list, List<int[]> list2, int n, AiProfile aiProfile, int n2, boolean bl) {
        List<int[]> list3;
        if (list == null || list.isEmpty()) {
            return list;
        }
        int n3 = this.oppositeColor(n);
        List<int[]> list4 = this.getLocalizedAIMovesForColor(n3, aiProfile.localRadius + 1);
        if (list4.isEmpty()) {
            list4 = list2;
        }
        TacticalSnapshot tacticalSnapshot = this.collectTacticalSnapshot(list2, n);
        TacticalSnapshot tacticalSnapshot2 = this.collectTacticalSnapshot(list4, n3);
        List<int[]> list5 = new ArrayList<int[]>(list);
        if (tacticalSnapshot.maxSeverity >= 4 && !tacticalSnapshot.maxSeverityMoves.isEmpty()) {
            list3 = this.intersectMoves(list2, tacticalSnapshot.maxSeverityMoves);
            list5 = this.mergeDistinctMoves(list5, list3, n2);
        }
        if (aiProfile.searchDepth >= 7) {
            list3 = this.selectThreatSeedMoves(n, aiProfile, 8);
            list5 = this.mergeDistinctMoves(list5, list3, n2);
        }
        if (tacticalSnapshot2.maxSeverity >= 3 && !tacticalSnapshot2.maxSeverityMoves.isEmpty()) {
            list3 = this.intersectMoves(list2, tacticalSnapshot2.maxSeverityMoves);
            list5 = this.mergeDistinctMoves(list5, list3, n2);
        }
        if (bl && aiProfile.searchDepth >= 7 && tacticalSnapshot2.maxSeverity >= 2) {
            list3 = this.selectThreatSeedMoves(n3, aiProfile, 8);
            List<int[]> list6 = this.intersectMoves(list2, list3);
            list5 = this.mergeDistinctMoves(list5, list6, n2);
        }
        return list5;
    }

    private List<int[]> selectThreatSeedMoves(int n, AiProfile aiProfile, int n2) {
        List<int[]> list = this.getLocalizedAIMovesForColor(n, aiProfile.localRadius + 1);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n);
        }
        if (list.isEmpty()) {
            return new ArrayList<int[]>();
        }
        List<int[]> list2 = this.selectLightweightCandidates(list, n, Math.min(24, list.size()), aiProfile);
        boolean bl = this.shouldProbeBuildUp(aiProfile, list2.size());
        ArrayList<ScoredMove> arrayList = new ArrayList<ScoredMove>();
        for (int i = 0; i < list2.size() && !this.isSearchTimeUp(); ++i) {
            int[] move = list2.get(i);
            MoveTactics moveTactics = this.analyzeMoveTactics(move[0], move[1], n);
            int n4 = this.tacticalSeverity(moveTactics);
            int n5 = this.forkSeverityIfPlaced(move[0], move[1], n);
            int n6 = this.strongThreatArms(move[0], move[1], n);
            int n7 = bl && i < 8 ? this.projectedBuildUpSeverityIfPlaced(move[0], move[1], n, aiProfile) : 0;
            int n8 = n7 * 220000 + n5 * 180000 + n4 * 70000 + n6 * 9000;
            arrayList.add(new ScoredMove(move[0], move[1], n8));
        }
        arrayList.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        int limit = Math.min(n2, arrayList.size());
        ArrayList<int[]> result = new ArrayList<int[]>();
        for (int i = 0; i < limit; ++i) {
            ScoredMove scoredMove = arrayList.get(i);
            result.add(new int[]{scoredMove.row, scoredMove.col});
        }
        return result;
    }

    private BuildUpThreatSnapshot collectBuildUpThreatSnapshot(List<int[]> list, int n, AiProfile aiProfile, int n2) {
        if (list == null || list.isEmpty()) {
            return new BuildUpThreatSnapshot(0, new ArrayList<int[]>());
        }
        if (!this.shouldProbeBuildUp(aiProfile, list.size())) {
            return new BuildUpThreatSnapshot(0, new ArrayList<int[]>());
        }
        List<int[]> list2 = this.selectLightweightCandidates(list, n, Math.min(n2, list.size()), aiProfile);
        int n3 = 0;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list2) {
            if (this.isSearchTimeUp()) break;
            int n4 = this.projectedBuildUpSeverityIfPlaced(nArray[0], nArray[1], n, aiProfile);
            if (n4 > n3) {
                n3 = n4;
                arrayList.clear();
                arrayList.add(nArray);
                continue;
            }
            if (n4 != n3 || n4 <= 0) continue;
            arrayList.add(nArray);
        }
        return new BuildUpThreatSnapshot(n3, arrayList);
    }

    private int projectedBuildUpSeverityIfPlaced(int n, int n2, int n3, AiProfile aiProfile) {
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return 0;
        }
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        MoveTactics moveTactics = this.analyzeMoveTactics(n, n2, n3);
        if (this.tacticalSeverity(moveTactics) >= 5) {
            this.board[n][n2] = n4;
            return 5;
        }
        List<int[]> list = this.getLocalizedAIMovesForColor(n3, aiProfile.localRadius + 1);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n3);
        }
        if (!this.shouldProbeBuildUp(aiProfile, list.size())) {
            this.board[n][n2] = n4;
            return 0;
        }
        list = this.selectLightweightCandidates(list, n3, Math.min(8, list.size()), aiProfile);
        int n5 = 0;
        for (int[] nArray : list) {
            if (this.isSearchTimeUp()) break;
            int n6 = this.forkSeverityIfPlaced(nArray[0], nArray[1], n3);
            if (n6 > n5) {
                n5 = n6;
            }
            if (n5 < 5) continue;
            break;
        }
        this.board[n][n2] = n4;
        return n5;
    }

    private List<int[]> mergeDistinctMoves(List<int[]> list, List<int[]> list2, int n) {
        String string;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        HashSet<String> hashSet = new HashSet<String>();
        for (int[] nArray : list) {
            if (arrayList.size() >= n) {
                return arrayList;
            }
            string = nArray[0] + "," + nArray[1];
            if (!hashSet.add(string)) continue;
            arrayList.add(nArray);
        }
        for (int[] nArray : list2) {
            if (arrayList.size() >= n) break;
            string = nArray[0] + "," + nArray[1];
            if (!hashSet.add(string)) continue;
            arrayList.add(nArray);
        }
        return arrayList;
    }

    private List<int[]> enforceForkSafetyOnCandidates(List<int[]> list, List<int[]> list2, int n, AiProfile aiProfile, int n2) {
        int n3;
        int n4;
        if (list == null || list.isEmpty()) {
            return list;
        }
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        List<int[]> list3 = new ArrayList<int[]>();
        ArrayList<RiskMove> arrayList = new ArrayList<RiskMove>();
        int n5 = Integer.MAX_VALUE;
        int n6 = Integer.MAX_VALUE;
        for (int[] nArray : list) {
            int n7;
            int n8 = this.enemyForkThreatScoreAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n9 = n7 = bl ? this.enemyBuildUpSeverityAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
            if (n8 < n5) {
                n5 = n8;
            }
            if (n7 < n6) {
                n6 = n7;
            }
            arrayList.add(new RiskMove(nArray[0], nArray[1], n8 * 10 + n7));
        }
        int n10 = Math.max(3, n5);
        int n11 = Math.max(3, n6);
        for (RiskMove riskMove : arrayList) {
            n4 = riskMove.score / 10;
            n3 = riskMove.score % 10;
            if ((n4 > n10 || n3 > n11) && !this.isWinningIfPlaced(riskMove.row, riskMove.col, n)) continue;
            list3.add(new int[]{riskMove.row, riskMove.col});
        }
        if (list3.isEmpty()) {
            for (RiskMove riskMove : arrayList) {
                n4 = riskMove.score / 10;
                n3 = riskMove.score % 10;
                if (n4 != n5 || n3 != n6) continue;
                list3.add(new int[]{riskMove.row, riskMove.col});
            }
        }
        return (list3 = this.expandCandidateBreadth(list3, list2, n, aiProfile, n2)).isEmpty() ? list : list3;
    }

    private List<int[]> enforceBuildUpDefenseCandidates(List<int[]> list, List<int[]> list2, int n, AiProfile aiProfile, int n2) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        List<int[]> list3 = this.selectSearchCandidates(list, n, Math.min(14, list.size()));
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        int n3 = Integer.MAX_VALUE;
        ArrayList<int[]> arrayList2 = new ArrayList<int[]>();
        Iterator<int[]> iterator = list3.iterator();
        while (iterator.hasNext()) {
            int[] nArray = iterator.next();
            if (this.isSearchTimeUp()) break;
            int n4 = this.worstEnemyBuildUpLineAfterMyMove(nArray[0], nArray[1], n, aiProfile);
            if (n4 < n3) {
                n3 = n4;
                arrayList2.clear();
                arrayList2.add(nArray);
            } else if (n4 == n3) {
                arrayList2.add(nArray);
            }
            if (n4 > 4) continue;
            arrayList.add(nArray);
        }
        List<int[]> defenseCandidates = !arrayList.isEmpty() ? arrayList : arrayList2;
        if (defenseCandidates.isEmpty()) {
            return list;
        }
        defenseCandidates = this.expandCandidateBreadth(defenseCandidates, list2, n, aiProfile, n2);
        return defenseCandidates.isEmpty() ? list : defenseCandidates;
    }

    private int worstEnemyBuildUpLineAfterMyMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLegalMovesForColor(n4);
        if (list.isEmpty()) {
            this.board[n][n2] = n5;
            return 0;
        }
        List<int[]> list2 = this.selectThreatSeedMoves(n4, aiProfile, 10);
        list2 = this.mergeDistinctMoves(list2, this.selectSearchCandidates(list, n4, 10), 14);
        int n6 = 0;
        for (int[] nArray : list2) {
            int n7;
            if (this.isSearchTimeUp()) break;
            int n8 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n4;
            int n9 = this.forkSeverityIfPlaced(nArray[0], nArray[1], n4);
            int n10 = Math.max(n9, n7 = this.projectedBuildUpSeverityIfPlaced(nArray[0], nArray[1], n4, aiProfile));
            if (n10 >= 5) {
                this.board[nArray[0]][nArray[1]] = n8;
                this.board[n][n2] = n5;
                return 5;
            }
            List<int[]> list3 = this.getLegalMovesForColor(n3);
            int n11 = 6;
            if (!list3.isEmpty()) {
                List<int[]> list4 = this.selectSearchCandidates(list3, n3, 10);
                for (int[] nArray2 : list4) {
                    int n12 = this.board[nArray2[0]][nArray2[1]];
                    this.board[nArray2[0]][nArray2[1]] = n3;
                    List<int[]> list5 = this.selectThreatSeedMoves(n4, aiProfile, 8);
                    int n13 = 0;
                    for (int[] nArray3 : list5) {
                        int n14 = Math.max(this.forkSeverityIfPlaced(nArray3[0], nArray3[1], n4), this.projectedBuildUpSeverityIfPlaced(nArray3[0], nArray3[1], n4, aiProfile));
                        if (n14 > n13) {
                            n13 = n14;
                        }
                        if (n13 < 5) continue;
                        break;
                    }
                    this.board[nArray2[0]][nArray2[1]] = n12;
                    if (n13 < n11) {
                        n11 = n13;
                    }
                    if (n11 > 2) continue;
                    break;
                }
            }
            if (n11 > n6) {
                n6 = n11;
            }
            this.board[nArray[0]][nArray[1]] = n8;
            if (n6 < 5) continue;
            break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int getMinSearchCandidates(AiProfile aiProfile) {
        if (this.hiddenStage11) {
            if (this.moveCount <= 18) {
                return Math.min(Math.max(16, aiProfile.candidateCount), 24);
            }
            return Math.min(Math.max(14, aiProfile.candidateCount / 2 + 4), 20);
        }
        if (this.moveCount <= 14) {
            return Math.min(Math.max(12, aiProfile.candidateCount), 18);
        }
        return Math.min(Math.max(10, aiProfile.candidateCount / 2), 16);
    }

    private int enemyMaxTacticalSeverityAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        TacticalSnapshot tacticalSnapshot = this.collectTacticalSnapshot(this.getLocalizedAIMovesForColor(n4, aiProfile.localRadius), n4);
        this.board[n][n2] = n5;
        return tacticalSnapshot.maxSeverity;
    }

    private int enemyImmediateWinningCountAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLocalizedAIMovesForColor(n4, aiProfile.localRadius);
        int n6 = 0;
        for (int[] nArray : list) {
            if (this.isWinningIfPlaced(nArray[0], nArray[1], n4) && ++n6 >= 2) break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int enemyForkThreatScoreAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLocalizedAIMovesForColor(n4, aiProfile.localRadius + 2);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n4);
        }
        list = this.mergeDistinctMoves(list, this.selectSearchCandidates(this.getLegalMovesForColor(n4), n4, Math.min(12, Math.max(6, aiProfile.candidateCount / 2))), 18);
        int n6 = 0;
        for (int[] nArray : list) {
            int n7 = this.forkSeverityIfPlaced(nArray[0], nArray[1], n4);
            if (n7 > n6) {
                n6 = n7;
            }
            if (n6 < 5) continue;
            break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int enemyBuildUpSeverityAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLocalizedAIMovesForColor(n4, aiProfile.localRadius + 2);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n4);
        }
        if (!this.shouldProbeBuildUp(aiProfile, list.size())) {
            this.board[n][n2] = n5;
            return 0;
        }
        BuildUpThreatSnapshot buildUpThreatSnapshot = this.collectBuildUpThreatSnapshot(list, n4, aiProfile, 14);
        this.board[n][n2] = n5;
        return buildUpThreatSnapshot.maxBuildSeverity;
    }

    private int[] chooseBestThreatReductionMove(List<int[]> list, int n, AiProfile aiProfile) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        int n2 = Integer.MAX_VALUE;
        int n3 = Integer.MIN_VALUE;
        int[] nArray = null;
        for (int[] nArray2 : list) {
            int n4 = this.enemyImmediateWinningCountAfterMove(nArray2[0], nArray2[1], n, aiProfile);
            int n5 = this.enemyMaxTacticalSeverityAfterMove(nArray2[0], nArray2[1], n, aiProfile);
            int n6 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(nArray2[0], nArray2[1], n, aiProfile) : 0;
            int n7 = bl ? this.enemyBuildUpSeverityAfterMove(nArray2[0], nArray2[1], n, aiProfile) : 0;
            int n8 = this.enemyLineDangerAfterMove(nArray2[0], nArray2[1], n);
            int n9 = this.opponentContactScore(nArray2[0], nArray2[1], n) + n4 * 320 + n8 * 110 + n5 * 60 + n6 * 14 + n7 * 10;
            int n10 = this.evaluateMove(nArray2[0], nArray2[1], n, aiProfile);
            if (n9 >= n2 && (n9 != n2 || n10 <= n3)) continue;
            n2 = n9;
            n3 = n10;
            nArray = nArray2;
        }
        return nArray;
    }

    private List<int[]> chooseThreatReductionMoves(List<int[]> list, int n, AiProfile aiProfile, int n2) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        if (list == null || list.isEmpty() || n2 <= 0) {
            return arrayList;
        }
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        ArrayList<ScoredMove> arrayList2 = new ArrayList<ScoredMove>();
        for (int[] nArray : list) {
            int n3 = this.enemyImmediateWinningCountAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n4 = this.enemyMaxTacticalSeverityAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n5 = aiProfile.searchDepth >= 5 ? this.enemyForkThreatScoreAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
            int n6 = bl ? this.enemyBuildUpSeverityAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
            int n7 = this.enemyLineDangerAfterMove(nArray[0], nArray[1], n);
            int n8 = this.opponentContactScore(nArray[0], nArray[1], n) * 25 + n3 * 320 + n7 * 120 + n4 * 50 + n5 * 14 + n6 * 10;
            int n9 = this.evaluateMove(nArray[0], nArray[1], n, aiProfile);
            arrayList2.add(new ScoredMove(nArray[0], nArray[1], -n8 * 1000 + n9));
        }
        arrayList2.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        int n10 = Math.min(n2, arrayList2.size());
        for (int i = 0; i < n10; ++i) {
            arrayList.add(new int[]{((ScoredMove)arrayList2.get((int)i)).row, ((ScoredMove)arrayList2.get((int)i)).col});
        }
        return arrayList;
    }

    private List<int[]> chooseStrictLineDefenseMoves(List<int[]> list, int n, AiProfile aiProfile) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        if (list == null || list.isEmpty()) {
            return arrayList;
        }
        int n3 = this.enemyLineDangerAfterMove(-1, -1, n);
        int n4 = Integer.MAX_VALUE;
        int n5 = Integer.MAX_VALUE;
        int n6 = Integer.MAX_VALUE;
        for (int[] nArray : list) {
            int n2 = this.enemyImmediateWinningCountAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n7 = this.enemyMaxTacticalSeverityAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n8 = this.enemyLineDangerAfterMove(nArray[0], nArray[1], n);
            if (n2 > n4 || n2 == n4 && (n7 > n5 || n7 == n5 && n8 >= n6)) continue;
            n4 = n2;
            n5 = n7;
            n6 = n8;
        }
        if (n6 >= n3 && n4 > 0) {
            return arrayList;
        }
        ArrayList<ScoredMove> arrayList2 = new ArrayList<ScoredMove>();
        for (int[] nArray : list) {
            int n2 = this.enemyImmediateWinningCountAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n7 = this.enemyMaxTacticalSeverityAfterMove(nArray[0], nArray[1], n, aiProfile);
            int n8 = this.enemyLineDangerAfterMove(nArray[0], nArray[1], n);
            if (n2 != n4 || n7 != n5 || n8 != n6) continue;
            int n9 = this.evaluateMove(nArray[0], nArray[1], n, aiProfile);
            int n10 = this.opponentContactScore(nArray[0], nArray[1], n) * 120 - n2 * 400000 - n7 * 50000 - n8 * 5000 + n9;
            arrayList2.add(new ScoredMove(nArray[0], nArray[1], n10));
        }
        arrayList2.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        for (ScoredMove scoredMove : arrayList2) {
            arrayList.add(new int[]{scoredMove.row, scoredMove.col});
        }
        return arrayList;
    }

    private int enemyLineDangerAfterMove(int n, int n2, int n3) {
        int n4 = this.oppositeColor(n3);
        int n5 = 0;
        if (n >= 0 && n2 >= 0) {
            n5 = this.board[n][n2];
            this.board[n][n2] = n3;
        }
        PatternStats patternStats = this.evaluatePatternsForColor(n4);
        if (n >= 0 && n2 >= 0) {
            this.board[n][n2] = n5;
        }
        return patternStats.open4 * 6 + patternStats.closed4 * 3 + patternStats.open3 * 2;
    }

    private int getHistoryScore(int n, int n2, int n3) {
        return n3 == 1 ? this.historyBlack[n][n2] : this.historyWhite[n][n2];
    }

    private void updateHistoryScore(int n, int n2, int n3, int n4) {
        int[][] nArray = n3 == 1 ? this.historyBlack : this.historyWhite;
        int n5 = Math.max(1, n4 * n4 * 10);
        nArray[n][n2] = Math.min(1000000, nArray[n][n2] + n5);
    }

    private int enemyTwoStepComboRiskAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLegalMovesForColor(n4);
        if (list.isEmpty()) {
            this.board[n][n2] = n5;
            return 0;
        }
        List<int[]> list2 = this.selectSearchCandidates(list, n4, 12);
        list2 = this.mergeDistinctMoves(list2, this.selectComboMoves(n4, aiProfile, 8), 14);
        int n6 = 0;
        for (int[] nArray : list2) {
            int n7 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n4;
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n4);
            int n8 = this.tacticalSeverity(moveTactics);
            if (n8 >= 5) {
                this.board[nArray[0]][nArray[1]] = n7;
                this.board[n][n2] = n5;
                return 5;
            }
            List<int[]> list3 = this.getLegalMovesForColor(n3);
            int n9 = 6;
            if (!list3.isEmpty()) {
                List<int[]> list4 = this.selectSearchCandidates(list3, n3, 8);
                for (int[] nArray2 : list4) {
                    int n10 = this.board[nArray2[0]][nArray2[1]];
                    this.board[nArray2[0]][nArray2[1]] = n3;
                    int n11 = this.maxThreatSeverityForColor(n4, aiProfile, 8);
                    this.board[nArray2[0]][nArray2[1]] = n10;
                    if (n11 < n9) {
                        n9 = n11;
                    }
                    if (n9 > 2) continue;
                    break;
                }
            }
            if (n9 >= 5) {
                n6 = Math.max(n6, 5);
            } else if (n9 >= 4 && n8 >= 2) {
                n6 = Math.max(n6, 4);
            } else if (n9 >= 3 && n8 >= 2) {
                n6 = Math.max(n6, 3);
            }
            this.board[nArray[0]][nArray[1]] = n7;
            if (n6 < 5) continue;
            break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int ownTwoStepComboGainAfterMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLegalMovesForColor(n3);
        if (list.isEmpty()) {
            this.board[n][n2] = n5;
            return 0;
        }
        List<int[]> list2 = this.selectSearchCandidates(list, n3, 12);
        list2 = this.mergeDistinctMoves(list2, this.selectComboMoves(n3, aiProfile, 10), 14);
        int n6 = 0;
        for (int[] nArray : list2) {
            int n7 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n3;
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n3);
            int n8 = this.tacticalSeverity(moveTactics);
            if (n8 >= 5) {
                this.board[nArray[0]][nArray[1]] = n7;
                this.board[n][n2] = n5;
                return 5;
            }
            List<int[]> list3 = this.getLegalMovesForColor(n4);
            int n9 = 6;
            if (!list3.isEmpty()) {
                List<int[]> list4 = this.selectSearchCandidates(list3, n4, 8);
                for (int[] nArray2 : list4) {
                    int n10 = this.board[nArray2[0]][nArray2[1]];
                    this.board[nArray2[0]][nArray2[1]] = n4;
                    int n11 = this.maxThreatSeverityForColor(n3, aiProfile, 8);
                    this.board[nArray2[0]][nArray2[1]] = n10;
                    if (n11 < n9) {
                        n9 = n11;
                    }
                    if (n9 > 2) continue;
                    break;
                }
            }
            if (n9 >= 5) {
                n6 = Math.max(n6, 5);
            } else if (n9 >= 4 && n8 >= 2) {
                n6 = Math.max(n6, 4);
            } else if (n9 >= 3 && n8 >= 2) {
                n6 = Math.max(n6, 3);
            }
            this.board[nArray[0]][nArray[1]] = n7;
            if (n6 < 5) continue;
            break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int forkSeverityIfPlaced(int n, int n2, int n3) {
        MoveTactics moveTactics = this.analyzeMoveTactics(n, n2, n3);
        if (moveTactics.winNow) {
            return 6;
        }
        int n4 = this.countImmediateWinningContinuationsAfterMove(n, n2, n3, 2);
        if (n4 >= 2) {
            return 5;
        }
        int n5 = this.strongThreatArms(n, n2, n3);
        if (moveTactics.doubleThreat43) {
            return 5;
        }
        if (moveTactics.open4 > 0 && n5 >= 2) {
            return 5;
        }
        if (moveTactics.open3 >= 2) {
            return 4;
        }
        if (n5 >= 2 && (moveTactics.open3 > 0 || moveTactics.closed4 > 0)) {
            return 3;
        }
        return 0;
    }

    private int countImmediateWinningContinuationsAfterMove(int n, int n2, int n3, int n4) {
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return 0;
        }
        int n5 = this.board[n][n2];
        this.board[n][n2] = n3;
        List<int[]> list = this.getLocalizedAIMovesForColor(n3, 3);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n3);
        }
        int n6 = 0;
        for (int[] nArray : list) {
            if (this.isWinningIfPlaced(nArray[0], nArray[1], n3) && ++n6 >= n4) break;
        }
        this.board[n][n2] = n5;
        return n6;
    }

    private int strongThreatArms(int n, int n2, int n3) {
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return 0;
        }
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        int n5 = 0;
        for (int[] nArray : DIRS) {
            int n6 = this.countOneDirection(n, n2, nArray[0], nArray[1], n3);
            int n7 = this.countOneDirection(n, n2, -nArray[0], -nArray[1], n3);
            int n8 = n6 + n7 + 1;
            boolean bl = this.isOpenEnd(n, n2, nArray[0], nArray[1], n6, n3);
            boolean bl2 = this.isOpenEnd(n, n2, -nArray[0], -nArray[1], n7, n3);
            int n9 = (bl ? 1 : 0) + (bl2 ? 1 : 0);
            if ((n8 < 4 || n9 < 1) && (n8 != 3 || n9 != 2)) continue;
            ++n5;
        }
        this.board[n][n2] = n4;
        return n5;
    }

    private int[] searchBestMoveAtDepth(List<int[]> list, int n, AiProfile aiProfile, int n2) {
        int n3 = Integer.MIN_VALUE;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        boolean bl = this.shouldProbeBuildUp(aiProfile, list.size());
        List<int[]> list2 = list;
        long rootHash = 0L;
        if (this.useAdvancedSearchOrdering(aiProfile)) {
            rootHash = this.computeSearchHash(n);
            TranspositionEntry transpositionEntry = this.transpositionTable.get(rootHash);
            int n4 = transpositionEntry != null ? transpositionEntry.bestMove : -1;
            list2 = this.orderCandidatesForSearch(list, n, aiProfile, n2, 0, n4);
        }
        int n5 = -1;
        for (int[] nArray : list2) {
            if (this.isSearchTimeUp()) {
                this.searchTimedOut = true;
                break;
            }
            this.updateThinkingCandidate(nArray[0], nArray[1]);
            int n6 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n;
            long childHash = rootHash != 0L ? this.applySearchHashMove(rootHash, nArray[0], nArray[1], n) : 0L;
            int n7 = -this.negamax(n2 - 1, this.oppositeColor(n), n, -536870912, 0x1FFFFFFF, aiProfile, true, childHash);
            this.board[nArray[0]][nArray[1]] = n6;
            if (this.searchTimedOut) break;
            if (aiProfile.searchDepth >= 7) {
                int n8;
                TacticalSnapshot tacticalSnapshot = this.collectTacticalSnapshot(this.getLocalizedAIMovesForColor(this.oppositeColor(n), aiProfile.localRadius), this.oppositeColor(n));
                int n9 = this.enemyForkThreatScoreAfterMove(nArray[0], nArray[1], n, aiProfile);
                int n10 = n8 = bl ? this.enemyBuildUpSeverityAfterMove(nArray[0], nArray[1], n, aiProfile) : 0;
                if (tacticalSnapshot.maxSeverity >= 5) {
                    n7 -= 3000000;
                } else if (tacticalSnapshot.maxSeverity >= 4) {
                    n7 -= 500000;
                }
                if (n9 >= 5) {
                    n7 -= 1800000;
                } else if (n9 >= 4) {
                    n7 -= 450000;
                }
                if (bl && n8 >= 5) {
                    n7 -= 1400000;
                } else if (bl && n8 >= 4) {
                    n7 -= 380000;
                }
            }
            if (n7 > n3) {
                n3 = n7;
                arrayList.clear();
                arrayList.add(nArray);
                n5 = this.encodeMove(nArray[0], nArray[1]);
                continue;
            }
            if (n7 != n3) continue;
            arrayList.add(nArray);
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        if (this.useAdvancedSearchOrdering(aiProfile) && n5 >= 0 && !this.searchTimedOut) {
            this.storeTransposition(rootHash != 0L ? rootHash : this.computeSearchHash(n), n2, n3, 0, n5);
        }
        return this.chooseTopMoveWithNoise(arrayList, n, aiProfile, 1);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private int negamax(int n, int n2, int n3, int n4, int n5, AiProfile aiProfile, boolean bl, long hash) {
        if (this.isSearchTimeUp()) {
            this.searchTimedOut = true;
            return this.evaluateBoard(n3, aiProfile);
        }
        int n6 = n4;
        int n7 = n5;
        long l = hash;
        int n8 = -1;
        TranspositionEntry entry = null;
        if (this.useAdvancedSearchOrdering(aiProfile)) {
            if (l == 0L) {
                l = this.computeSearchHash(n2);
            }
            entry = this.transpositionTable.get(l);
        }
        if (entry != null) {
            n8 = entry.bestMove;
            if (entry.depth >= n) {
                if (entry.flag == 0) {
                    return entry.score;
                }
                if (entry.flag == 1) {
                    n4 = Math.max(n4, entry.score);
                } else if (entry.flag == 2) {
                    n5 = Math.min(n5, entry.score);
                }
                if (n4 >= n5) {
                    return entry.score;
                }
            }
        }
        List<int[]> list2;
        if ((list2 = this.getLocalizedAIMovesForColor(n2, aiProfile.localRadius)).isEmpty()) {
            list2 = this.getLegalMovesForColor(n2);
        }
        if (list2.isEmpty()) {
            return this.evaluateBoard(n3, aiProfile);
        }
        if (this.hasImmediateWinningMove(list2, n2)) {
            int n9 = 5000000 - (aiProfile.searchDepth - n) * 100;
            return n2 == n3 ? n9 : -n9;
        }
        if (n <= 0) {
            if (!bl || aiProfile.searchDepth < 5) return this.evaluateBoard(n3, aiProfile);
            TacticalSnapshot tacticalSnapshotSelf = this.collectTacticalSnapshot(this.getLocalizedAIMovesForColor(n2, aiProfile.localRadius), n2);
            TacticalSnapshot tacticalSnapshot = this.collectTacticalSnapshot(this.getLocalizedAIMovesForColor(this.oppositeColor(n2), aiProfile.localRadius), this.oppositeColor(n2));
            if (tacticalSnapshotSelf.maxSeverity < 4 && tacticalSnapshot.maxSeverity < 4) return this.evaluateBoard(n3, aiProfile);
            n = 1;
            bl = false;
        }
        List<int[]> list = this.selectSearchCandidates(list2, n2, Math.max(6, aiProfile.candidateCount - (aiProfile.searchDepth - n) * 3));
        list = this.injectCriticalTacticalCandidates(list, list2, n2, aiProfile, Math.max(8, aiProfile.candidateCount - (aiProfile.searchDepth - n) * 2), n >= Math.max(2, aiProfile.searchDepth - 3));
        if (this.useAdvancedSearchOrdering(aiProfile)) {
            int n10 = Math.max(0, Math.min(31, aiProfile.searchDepth - n));
            list = this.orderCandidatesForSearch(list, n2, aiProfile, n, n10, n8);
        }
        int n11 = -536870912;
        int n12 = -1;
        int n13 = Math.max(0, Math.min(31, aiProfile.searchDepth - n));
        for (int[] nArray : list) {
            if (this.isSearchTimeUp()) {
                this.searchTimedOut = true;
                break;
            }
            int n14 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n2;
            long childHash = l != 0L ? this.applySearchHashMove(l, nArray[0], nArray[1], n2) : 0L;
            int n15 = -this.negamax(n - 1, this.oppositeColor(n2), n3, -n5, -n4, aiProfile, bl, childHash);
            this.board[nArray[0]][nArray[1]] = n14;
            if (n15 > n11) {
                n11 = n15;
                n12 = this.encodeMove(nArray[0], nArray[1]);
            }
            if (n15 > n4) {
                n4 = n15;
            }
            if (n4 < n5) continue;
            this.updateHistoryScore(nArray[0], nArray[1], n2, Math.max(1, n));
            this.recordKillerMove(n13, nArray[0], nArray[1]);
            break;
        }
        if (!this.useAdvancedSearchOrdering(aiProfile) || this.searchTimedOut || n11 <= -268435456) return n11;
        int n16 = 0;
        if (n11 <= n6) {
            n16 = 2;
        } else if (n11 >= n7) {
            n16 = 1;
        }
        this.storeTransposition(l, n, n11, n16, n12);
        return n11;
    }

    private int evaluateBoard(int n, AiProfile aiProfile) {
        int n2 = this.oppositeColor(n);
        PatternStats patternStats = this.evaluatePatternsForColor(n);
        PatternStats patternStats2 = this.evaluatePatternsForColor(n2);
        int n3 = patternStats.five * 1000000 + patternStats.doubleThreat43 * 80000 + patternStats.open4 * 50000 + patternStats.closed4 * 14000 + patternStats.open3 * 4000 + patternStats.closed3 * 900 + patternStats.open2 * 180;
        int n4 = patternStats2.five * 1000000 + patternStats2.doubleThreat43 * 85000 + patternStats2.open4 * 55000 + patternStats2.closed4 * 16000 + patternStats2.open3 * 4500 + patternStats2.closed3 * 950 + patternStats2.open2 * 200;
        int n5 = this.centerMassScore(n) - this.centerMassScore(n2);
        return n3 * aiProfile.attackWeight - n4 * aiProfile.defenseWeight + n5 * 4;
    }

    private int evaluateTwoStepComboSwing(int n, AiProfile aiProfile) {
        if (!aiProfile.enableComboProbe || this.isSearchTimeUp()) {
            return 0;
        }
        int n2 = this.hasTwoStepComboThreat(n, aiProfile) ? 1 : 0;
        int n3 = this.hasTwoStepComboThreat(this.oppositeColor(n), aiProfile) ? 1 : 0;
        return n2 * 280000 - n3 * 300000;
    }

    private boolean hasTwoStepComboThreat(int n, AiProfile aiProfile) {
        List<int[]> list = this.selectComboMoves(n, aiProfile, 6);
        if (list.isEmpty()) {
            return false;
        }
        int n2 = this.oppositeColor(n);
        for (int[] nArray : list) {
            if (this.isSearchTimeUp()) {
                return false;
            }
            int n3 = this.board[nArray[0]][nArray[1]];
            this.board[nArray[0]][nArray[1]] = n;
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n);
            if (this.tacticalSeverity(moveTactics) >= 5) {
                this.board[nArray[0]][nArray[1]] = n3;
                return true;
            }
            List<int[]> list2 = this.selectComboMoves(n2, aiProfile, 5);
            int n4 = Integer.MAX_VALUE;
            if (list2.isEmpty()) {
                n4 = this.maxThreatSeverityForColor(n, aiProfile, 6);
            } else {
                for (int[] nArray2 : list2) {
                    if (this.isSearchTimeUp()) break;
                    int n5 = this.board[nArray2[0]][nArray2[1]];
                    this.board[nArray2[0]][nArray2[1]] = n2;
                    int n6 = this.maxThreatSeverityForColor(n, aiProfile, 6);
                    this.board[nArray2[0]][nArray2[1]] = n5;
                    if (n6 < n4) {
                        n4 = n6;
                    }
                    if (n4 > 2) continue;
                    break;
                }
            }
            this.board[nArray[0]][nArray[1]] = n3;
            if (n4 < 5) continue;
            return true;
        }
        return false;
    }

    private int maxThreatSeverityForColor(int n, AiProfile aiProfile, int n2) {
        int n3 = 0;
        List<int[]> list = this.selectComboMoves(n, aiProfile, n2);
        for (int[] nArray : list) {
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n);
            int n4 = this.tacticalSeverity(moveTactics);
            if (n4 > n3) {
                n3 = n4;
            }
            if (n3 < 6) continue;
            return n3;
        }
        return n3;
    }

    private List<int[]> selectComboMoves(int n, AiProfile aiProfile, int n2) {
        List<int[]> list = this.getLocalizedAIMovesForColor(n, aiProfile.localRadius + 1);
        if (list.isEmpty()) {
            list = this.getLegalMovesForColor(n);
        }
        ArrayList<ScoredMove> arrayList = new ArrayList<ScoredMove>();
        for (int[] object2 : list) {
            MoveTactics i = this.analyzeMoveTactics(object2[0], object2[1], n);
            int n3 = this.forkSeverityIfPlaced(object2[0], object2[1], n);
            int n4 = this.strongThreatArms(object2[0], object2[1], n);
            int n5 = n3 * 140000 + this.tacticalSeverity(i) * 95000 + n4 * 12000 + this.evaluateMove(object2[0], object2[1], n, aiProfile);
            arrayList.add(new ScoredMove(object2[0], object2[1], n5));
        }
        arrayList.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        int n6 = Math.min(n2, arrayList.size());
        ArrayList<int[]> arrayList2 = new ArrayList<int[]>();
        for (int i = 0; i < n6; ++i) {
            arrayList2.add(new int[]{((ScoredMove)arrayList.get((int)i)).row, ((ScoredMove)arrayList.get((int)i)).col});
        }
        return arrayList2;
    }

    private PatternStats evaluatePatternsForColor(int n) {
        PatternStats patternStats = new PatternStats();
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != n) continue;
                for (int[] nArray : DIRS) {
                    int n2 = i - nArray[0];
                    int n3 = j - nArray[1];
                    if (this.inRange(n2, n3) && this.board[n2][n3] == n) continue;
                    int n4 = 1 + this.countOneDirection(i, j, nArray[0], nArray[1], n);
                    int n5 = i + nArray[0] * n4;
                    int n6 = j + nArray[1] * n4;
                    boolean bl = this.inRange(n2, n3) && this.board[n2][n3] == 0;
                    boolean bl2 = this.inRange(n5, n6) && this.board[n5][n6] == 0;
                    int n7 = (bl ? 1 : 0) + (bl2 ? 1 : 0);
                    if (n4 >= 5) {
                        if (n == 1 && this.isRenjuStyle() && n4 > 5 && this.isRenjuPlayPhase()) continue;
                        ++patternStats.five;
                        continue;
                    }
                    if (n4 == 4) {
                        if (n7 == 2) {
                            ++patternStats.open4;
                            continue;
                        }
                        if (n7 != 1) continue;
                        ++patternStats.closed4;
                        continue;
                    }
                    if (n4 == 3) {
                        if (n7 == 2) {
                            ++patternStats.open3;
                            continue;
                        }
                        if (n7 != 1) continue;
                        ++patternStats.closed3;
                        continue;
                    }
                    if (n4 != 2 || n7 != 2) continue;
                    ++patternStats.open2;
                }
            }
        }
        if (patternStats.open4 + patternStats.closed4 >= 1 && patternStats.open3 >= 1) {
            ++patternStats.doubleThreat43;
        }
        if (patternStats.open4 + patternStats.closed4 >= 2 || patternStats.open3 >= 2) {
            ++patternStats.doubleThreat43;
        }
        return patternStats;
    }

    private boolean isRenjuPlayPhase() {
        if (this.mode == RuleMode.RENJU_ADVANCED) {
            return this.advancedPhase == AdvancedPhase.NORMAL;
        }
        return this.mode == RuleMode.RENJU;
    }

    private TacticalSnapshot collectTacticalSnapshot(List<int[]> list, int n) {
        int n2 = 0;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list) {
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n);
            int n3 = this.tacticalSeverity(moveTactics);
            if (n3 > n2) {
                n2 = n3;
                arrayList.clear();
                arrayList.add(nArray);
                continue;
            }
            if (n3 != n2 || n3 <= 0) continue;
            arrayList.add(nArray);
        }
        return new TacticalSnapshot(n2, arrayList);
    }

    private ForkThreatSnapshot collectForkThreatSnapshot(List<int[]> list, int n) {
        int n2 = 0;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list) {
            int n3 = this.forkSeverityIfPlaced(nArray[0], nArray[1], n);
            if (n3 > n2) {
                n2 = n3;
                arrayList.clear();
                arrayList.add(nArray);
                continue;
            }
            if (n3 != n2 || n3 <= 0) continue;
            arrayList.add(nArray);
        }
        return new ForkThreatSnapshot(n2, arrayList);
    }

    private MoveTactics analyzeMoveTactics(int n, int n2, int n3) {
        MoveTactics moveTactics = new MoveTactics();
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return moveTactics;
        }
        if (n3 == 1 && this.isRenjuStyle() && this.isRenjuPlayPhase() && this.isForbiddenCellForBlack(n, n2)) {
            return moveTactics;
        }
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        moveTactics.winNow = this.isWinningIfPlaced(n, n2, n3);
        for (int[] nArray : DIRS) {
            int n5 = this.countOneDirection(n, n2, nArray[0], nArray[1], n3);
            int n6 = this.countOneDirection(n, n2, -nArray[0], -nArray[1], n3);
            int n7 = n5 + n6 + 1;
            boolean bl = this.isOpenEnd(n, n2, nArray[0], nArray[1], n5, n3);
            boolean bl2 = this.isOpenEnd(n, n2, -nArray[0], -nArray[1], n6, n3);
            int n8 = (bl ? 1 : 0) + (bl2 ? 1 : 0);
            if (n7 >= 4 && n8 == 2) {
                ++moveTactics.open4;
                continue;
            }
            if (n7 >= 4 && n8 == 1) {
                ++moveTactics.closed4;
                continue;
            }
            if (n7 == 3 && n8 == 2) {
                ++moveTactics.open3;
                continue;
            }
            if (n7 != 3 || n8 != 1) continue;
            ++moveTactics.closed3;
        }
        moveTactics.doubleThreat43 = moveTactics.open4 + moveTactics.closed4 >= 1 && moveTactics.open3 >= 1 || moveTactics.open4 + moveTactics.closed4 >= 2 || moveTactics.open3 >= 2;
        this.board[n][n2] = n4;
        return moveTactics;
    }

    private int tacticalSeverity(MoveTactics moveTactics) {
        if (moveTactics.winNow) {
            return 6;
        }
        if (moveTactics.doubleThreat43) {
            return 5;
        }
        if (moveTactics.open4 > 0) {
            return 4;
        }
        if (moveTactics.closed4 > 0) {
            return 3;
        }
        if (moveTactics.open3 > 0) {
            return 2;
        }
        if (moveTactics.closed3 > 0) {
            return 1;
        }
        return 0;
    }

    private List<int[]> findImmediateWinningMoves(List<int[]> list, int n) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list) {
            if (!this.isWinningIfPlaced(nArray[0], nArray[1], n)) continue;
            arrayList.add(nArray);
        }
        return arrayList;
    }

    private boolean hasImmediateWinningMove(List<int[]> list, int n) {
        return !this.findImmediateWinningMoves(list, n).isEmpty();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private List<int[]> selectSearchCandidates(List<int[]> list, int n, int n2) {
        int n3;
        int n4;
        int arrayList5;
        List<int[]> list2 = new ArrayList<int[]>();
        for (int[] nArray : list) {
            if (!this.hasNeighborWithin(nArray[0], nArray[1], 3) && this.moveCount >= 2) continue;
            list2.add(nArray);
        }
        int n13 = Math.min(16, Math.max(10, n2 / 2));
        if (list2.size() < n13) {
            ArrayList<int[]> arrayList2 = new ArrayList<int[]>();
            for (int[] nArray : list) {
                if (!this.hasNeighborWithin(nArray[0], nArray[1], 4) && this.moveCount >= 2) continue;
                arrayList2.add(nArray);
            }
            list2 = this.mergeDistinctMoves(list2, arrayList2, Math.min(Math.max(n2 * 2, 24), list.size()));
        }
        if (list2.size() < n13) {
            ArrayList<int[]> arrayList3 = new ArrayList<int[]>();
            for (int[] nArray : list) {
                if (!this.hasNeighborWithin(nArray[0], nArray[1], 5) && this.moveCount >= 2) continue;
                arrayList3.add(nArray);
            }
            list2 = this.mergeDistinctMoves(list2, arrayList3, Math.min(Math.max(n2 * 2, 28), list.size()));
        }
        if (list2.isEmpty()) {
            list2 = list;
        }
        AiProfile aiProfile = this.getAiProfile(this.computerDifficulty);
        int n6 = this.oppositeColor(n);
        boolean bl = this.moveCount <= 4 || list2.size() > 96;
        boolean bl2 = !bl && list2.size() <= 80;
        List<int[]> list3 = this.collectOpenTwoSupportMoves(n6, list2);
        HashSet<String> hashSet = new HashSet<String>();
        for (int[] object32 : list3) {
            hashSet.add(object32[0] + "," + object32[1]);
        }
        ArrayList<ScoredMove> arrayList = new ArrayList<ScoredMove>();
        for (int[] n11 : list2) {
            if (this.isSearchTimeUp()) break;
            MoveTactics ownTactics = this.analyzeMoveTactics(n11[0], n11[1], n);
            MoveTactics enemyTactics = this.analyzeMoveTactics(n11[0], n11[1], n6);
            int arrayList2 = this.tacticalSeverity(ownTactics);
            int arrayList3 = this.tacticalSeverity(enemyTactics);
            int arrayList4 = this.strongThreatArms(n11[0], n11[1], n);
            arrayList5 = bl2 ? this.enemyLineDangerAfterMove(n11[0], n11[1], n) : 0;
            int scoredMove3 = this.getHistoryScore(n11[0], n11[1], n);
            n4 = bl ? this.quickEvaluateMove(n11[0], n11[1], n, aiProfile) : this.evaluateMove(n11[0], n11[1], n, aiProfile);
            if (this.moveCount <= 16 && arrayList2 <= 2 && arrayList3 <= 2) {
                n4 += this.gameStyleScore(n11[0], n11[1], n);
            }
            n3 = n4 + scoredMove3 * 24 + arrayList2 * 1600 - arrayList3 * 1100 + arrayList4 * 220 - arrayList5 * 180;
            if (hashSet.contains(n11[0] + "," + n11[1])) {
                n3 += 450;
            }
            arrayList.add(new ScoredMove(n11[0], n11[1], n3));
        }
        arrayList.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        boolean bl3 = this.candidateSelectionDepth == 0 && !bl && this.shouldProbeBuildUp(aiProfile, list2.size());
        int n5 = bl3 ? Math.min(Math.max(n2 * 2, 12), arrayList.size()) : 0;
        ArrayList<ScoredMove> defenseMoves = new ArrayList<ScoredMove>();
        ArrayList<ScoredMove> attackMoves = new ArrayList<ScoredMove>();
        ArrayList<ScoredMove> arrayList2 = new ArrayList<ScoredMove>();
        ArrayList<ScoredMove> arrayList3 = new ArrayList<ScoredMove>();
        ArrayList<ScoredMove> arrayList4 = new ArrayList<ScoredMove>();
        ++this.candidateSelectionDepth;
        try {
            for (arrayList5 = 0; arrayList5 < arrayList.size(); ++arrayList5) {
                if (this.isSearchTimeUp()) {
                    break;
                }
                ScoredMove hashSet2 = (ScoredMove)arrayList.get(arrayList5);
                n4 = hashSet2.row;
                n3 = hashSet2.col;
                MoveTactics n18 = this.analyzeMoveTactics(n4, n3, n);
                MoveTactics n19 = this.analyzeMoveTactics(n4, n3, n6);
                int n7 = this.tacticalSeverity(n18);
                int nArray = this.tacticalSeverity(n19);
                int string = this.strongThreatArms(n4, n3, n);
                int n8 = bl2 ? this.enemyLineDangerAfterMove(n4, n3, n) : 0;
                int n9 = arrayList5 < n5 ? this.projectedBuildUpSeverityIfPlaced(n4, n3, n, aiProfile) : 0;
            int n10 = hashSet2.score + n9 * 700 + (this.moveCount <= 18 ? this.gameStyleScore(n4, n3, n) : 0);
                ScoredMove scoredMove3 = new ScoredMove(n4, n3, n10);
                arrayList4.add(scoredMove3);
                if (nArray >= 2 || n8 >= 2) {
                    defenseMoves.add(scoredMove3);
                    continue;
                }
                if (n7 >= 2) {
                    attackMoves.add(scoredMove3);
                    continue;
                }
                if (n9 >= 3 || string >= 2 || hashSet.contains(n4 + "," + n3)) {
                    arrayList2.add(scoredMove3);
                    continue;
                }
                arrayList3.add(scoredMove3);
            }
        }
        finally {
            --this.candidateSelectionDepth;
        }
        defenseMoves.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        attackMoves.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        arrayList2.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        arrayList3.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        arrayList4.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        ArrayList<int[]> arrayList6 = new ArrayList<int[]>();
        HashSet<String> hashSet2 = new HashSet<String>();
        n4 = Math.min(n2, arrayList4.size());
        n3 = this.moveCount <= 20 ? Math.max(2, n4 / 3) : Math.max(1, n4 / 4);
        int n11 = Math.max(1, n4 / 4);
        int n12 = Math.max(1, n4 / 5);
        if (this.computerDifficulty <= 5) {
            n3 = Math.max(n3, n4 / 2);
            n11 = Math.max(1, n4 / 6);
        } else if (this.computerDifficulty >= 8) {
            n11 = Math.max(n11, n4 / 3);
        }
        this.addTopDistinct(arrayList6, hashSet2, defenseMoves, n3, n4);
        this.addTopDistinct(arrayList6, hashSet2, attackMoves, n11, n4);
        this.addTopDistinct(arrayList6, hashSet2, arrayList2, n12, n4);
        this.addTopDistinct(arrayList6, hashSet2, arrayList3, Math.max(1, n4 / 5), n4);
        this.addTopDistinct(arrayList6, hashSet2, arrayList4, n4, n4);
        for (int[] nArray : list3) {
            if (arrayList6.size() >= n4) break;
            String string = nArray[0] + "," + nArray[1];
            if (!hashSet2.add(string)) continue;
            arrayList6.add(new int[]{nArray[0], nArray[1]});
        }
        if (arrayList6.isEmpty()) {
            arrayList6.addAll(list2);
        }
        return arrayList6;
    }

    private int[] chooseTopMoveWithNoise(List<int[]> list, int n, AiProfile aiProfile, int n2) {
        if (list.isEmpty()) {
            return null;
        }
        ArrayList<ScoredMove> arrayList = new ArrayList<ScoredMove>();
        for (int[] move : list) {
            int n3 = this.evaluateMove(move[0], move[1], n, aiProfile);
            arrayList.add(new ScoredMove(move[0], move[1], n3));
        }
        arrayList.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        int n4 = Math.min(Math.max(1, n2 + aiProfile.mistakeAllowance), arrayList.size());
        ScoredMove selected = arrayList.get(this.random.nextInt(n4));
        return new int[]{selected.row, selected.col};
    }

    private void addTopDistinct(List<int[]> list, Set<String> set, List<ScoredMove> list2, int n, int n2) {
        if (n <= 0 || list2.isEmpty() || list.size() >= n2) {
            return;
        }
        int n3 = 0;
        for (ScoredMove scoredMove : list2) {
            if (list.size() >= n2 || n3 >= n) break;
            String string = scoredMove.row + "," + scoredMove.col;
            if (!set.add(string)) continue;
            list.add(new int[]{scoredMove.row, scoredMove.col});
            ++n3;
        }
    }

    private List<int[]> collectOpenTwoSupportMoves(int n, List<int[]> list) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        if (list == null || list.isEmpty()) {
            return arrayList;
        }
        HashSet<String> hashSet = new HashSet<String>();
        for (int[] nArray : list) {
            hashSet.add(nArray[0] + "," + nArray[1]);
        }
        HashSet<String> hashSet2 = new HashSet<String>();
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != n) continue;
                for (int[] nArray : DIRS) {
                    String string;
                    boolean bl;
                    int n2;
                    int n3 = i - nArray[0];
                    int n4 = j - nArray[1];
                    if (this.inRange(n3, n4) && this.board[n3][n4] == n || (n2 = 1 + this.countOneDirection(i, j, nArray[0], nArray[1], n)) != 2) continue;
                    int n5 = i + nArray[0] * n2;
                    int n6 = j + nArray[1] * n2;
                    boolean bl2 = this.inRange(n3, n4) && this.board[n3][n4] == 0;
                    boolean bl3 = bl = this.inRange(n5, n6) && this.board[n5][n6] == 0;
                    if (!bl2 || !bl) continue;
                    int[][] nArrayArray = new int[][]{{n3, n4}, {n5, n6}, {i + nArray[0], j + nArray[1]}};
                    int n7 = -nArray[1];
                    int n8 = nArray[0];
                    int[][] nArrayArray2 = new int[][]{{i + n7, j + n8}, {i - n7, j - n8}, {i + nArray[0] + n7, j + nArray[1] + n8}, {i + nArray[0] - n7, j + nArray[1] - n8}};
                    for (int[] nArray2 : nArrayArray) {
                        string = nArray2[0] + "," + nArray2[1];
                        if (!hashSet.contains(string) || !hashSet2.add(string)) continue;
                        arrayList.add(new int[]{nArray2[0], nArray2[1]});
                    }
                    for (int[] nArray2 : nArrayArray2) {
                        string = nArray2[0] + "," + nArray2[1];
                        if (!hashSet.contains(string) || !hashSet2.add(string)) continue;
                        arrayList.add(new int[]{nArray2[0], nArray2[1]});
                    }
                }
            }
        }
        return arrayList;
    }

    private List<int[]> intersectMoves(List<int[]> list, List<int[]> list2) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        block0: for (int[] nArray : list) {
            for (int[] nArray2 : list2) {
                if (nArray[0] != nArray2[0] || nArray[1] != nArray2[1]) continue;
                arrayList.add(nArray);
                continue block0;
            }
        }
        return arrayList;
    }

    private int evaluateMove(int n, int n2, int n3, AiProfile aiProfile) {
        boolean bl;
        int n4 = this.oppositeColor(n3);
        MoveTactics moveTactics = this.analyzeMoveTactics(n, n2, n3);
        MoveTactics moveTactics2 = this.analyzeMoveTactics(n, n2, n4);
        int n5 = this.tacticalSeverity(moveTactics);
        int n6 = this.tacticalSeverity(moveTactics2);
        int n7 = this.currentMaxContinuous(n4);
        boolean bl2 = this.computerDifficulty <= 5;
        boolean bl3 = bl = this.moveCount <= 10 && n7 <= 2 && !bl2;
        int n8 = n6 >= 4 ? 110 : (n6 >= 2 ? (bl2 ? 95 : (bl ? 24 : 70)) : (bl2 ? 36 : (bl ? 8 : 24)));
        int n9 = n5 * aiProfile.tacticalWeight * 165 - n6 * aiProfile.defenseWeight * n8;
        int n10 = this.localShapeScore(n, n2, n3);
        int n11 = this.localOpenEndedScore(n, n2, n3);
        int n12 = this.localShapeScore(n, n2, n4);
        int n13 = bl2 ? 74 : (bl ? 18 : 52);
        int n14 = this.adjacentStoneCount(n, n2, n3) + this.adjacentStoneCount(n, n2, n4);
        int n15 = 14 - (Math.abs(n - 7) + Math.abs(n2 - 7));
        int n16 = this.brickStackPenalty(n, n2, n3);
        int n17 = this.moveCount <= 12 ? this.openingVarietyScore(n, n2, n3) : 0;
        int n18 = this.fightProximityScore(n, n2, n3);
        return n9 + n10 * 135 + n11 * 70 + n12 * n13 + n14 * 7 + n15 * 4 - n16 + n17 + n18;
    }

    private int evaluateMove(int n, int n2, int n3) {
        return this.evaluateMove(n, n2, n3, this.getAiProfile(this.computerDifficulty));
    }

    private int quickEvaluateMove(int n, int n2, int n3, AiProfile aiProfile) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.tacticalSeverity(this.analyzeMoveTactics(n, n2, n3));
        int n6 = this.tacticalSeverity(this.analyzeMoveTactics(n, n2, n4));
        int n7 = this.localShapeScore(n, n2, n3);
        int n8 = this.localShapeScore(n, n2, n4);
        int n9 = this.adjacentStoneCount(n, n2, n3) + this.adjacentStoneCount(n, n2, n4);
        int n10 = 14 - (Math.abs(n - 7) + Math.abs(n2 - 7));
        int n11 = this.moveCount <= 12 ? this.openingVarietyScore(n, n2, n3) : 0;
        int n12 = this.fightProximityScore(n, n2, n3);
        return n5 * aiProfile.tacticalWeight * 140 - n6 * aiProfile.defenseWeight * 60 + n7 * 110 + n8 * 44 + n9 * 10 + n10 * 4 + n11 + n12;
    }

    private boolean shouldProbeBuildUp(AiProfile aiProfile, int n) {
        if (this.hiddenStage11) {
            return aiProfile.searchDepth >= 7 && this.moveCount >= 4 && n <= 96 && !this.isSearchTimeUp();
        }
        return aiProfile.searchDepth >= 7 && this.moveCount >= 6 && n <= 72 && !this.isSearchTimeUp();
    }

    private boolean useAdvancedSearchOrdering(AiProfile aiProfile) {
        return this.hiddenStage11 || aiProfile.searchDepth >= 8;
    }

    private List<int[]> orderCandidatesForSearch(List<int[]> list, int n, AiProfile aiProfile, int n2, int n3, int n4) {
        if (list == null || list.size() <= 1) {
            return list;
        }
        ArrayList<ScoredMove> arrayList = new ArrayList<ScoredMove>(list.size());
        int n5 = this.killerMoves[n3][0];
        int n6 = this.killerMoves[n3][1];
        for (int[] object2 : list) {
            int scoredMove3 = this.encodeMove(object2[0], object2[1]);
            int n7 = this.getHistoryScore(object2[0], object2[1], n) * 16;
            if (scoredMove3 == n4) {
                n7 += 3500000;
            } else if (scoredMove3 == n5) {
                n7 += 2000000;
            } else if (scoredMove3 == n6) {
                n7 += 1200000;
            }
            MoveTactics moveTactics = this.analyzeMoveTactics(object2[0], object2[1], n);
            n7 += this.tacticalSeverity(moveTactics) * 160000;
            n7 += this.forkSeverityIfPlaced(object2[0], object2[1], n) * 120000;
            n7 += this.strongThreatArms(object2[0], object2[1], n) * 8000;
            n7 += this.quickEvaluateMove(object2[0], object2[1], n, aiProfile);
            if (this.moveCount <= 18) {
                n7 += this.gameStyleScore(object2[0], object2[1], n);
            }
            if (n2 <= 2) {
                n7 += this.enemyLineDangerAfterMove(object2[0], object2[1], n) * -6000;
            }
            arrayList.add(new ScoredMove(object2[0], object2[1], n7));
        }
        arrayList.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        ArrayList<int[]> arrayList2 = new ArrayList<int[]>(arrayList.size());
        for (ScoredMove scoredMove3 : arrayList) {
            arrayList2.add(new int[]{scoredMove3.row, scoredMove3.col});
        }
        return arrayList2;
    }

    private int encodeMove(int n, int n2) {
        return n * 15 + n2;
    }

    private void recordKillerMove(int n, int n2, int n3) {
        if (n < 0 || n >= 32) {
            return;
        }
        int n4 = this.encodeMove(n2, n3);
        if (this.killerMoves[n][0] == n4) {
            return;
        }
        this.killerMoves[n][1] = this.killerMoves[n][0];
        this.killerMoves[n][0] = n4;
    }

    private void clearSearchArtifacts() {
        this.transpositionTable.clear();
        for (int i = 0; i < this.killerMoves.length; ++i) {
            Arrays.fill(this.killerMoves[i], -1);
        }
    }

    private long computeSearchHash(int n) {
        long l = 0L;
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                int stone = this.board[i][j];
                if (stone == 0) continue;
                l ^= SEARCH_ZOBRIST[i][j][stone];
            }
        }
        l ^= MODE_ZOBRIST[this.mode.ordinal()];
        l ^= PHASE_ZOBRIST[this.advancedPhase.ordinal()];
        return l ^ TURN_ZOBRIST[n];
    }

    private long applySearchHashMove(long l, int n, int n2, int n3) {
        long next = l ^ SEARCH_ZOBRIST[n][n2][n3];
        next ^= TURN_ZOBRIST[n3];
        next ^= TURN_ZOBRIST[this.oppositeColor(n3)];
        return next;
    }

    private void storeTransposition(long l, int n, int n2, int n3, int n4) {
        TranspositionEntry transpositionEntry;
        if (l == 0L || n < 0) {
            return;
        }
        if (this.transpositionTable.size() >= 200000) {
            this.transpositionTable.clear();
        }
        if ((transpositionEntry = this.transpositionTable.get(l)) != null && transpositionEntry.depth > n && transpositionEntry.flag == 0) {
            return;
        }
        this.transpositionTable.put(l, new TranspositionEntry(n, n2, n3, n4));
    }

    private List<int[]> selectLightweightCandidates(List<int[]> list, int n, int n2, AiProfile aiProfile) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        if (list == null || list.isEmpty() || n2 <= 0) {
            return arrayList;
        }
        List<int[]> list2 = new ArrayList<int[]>();
        for (int[] nArray : list) {
            if (this.moveCount >= 2 && !this.hasNeighborWithin(nArray[0], nArray[1], 2)) continue;
            list2.add(nArray);
        }
        int nLimit = Math.min(Math.max(n2 * 2, 10), list.size());
        if (list2.size() < nLimit) {
            ArrayList<int[]> arrayList3 = new ArrayList<int[]>();
            for (int[] nArray : list) {
                if (this.moveCount >= 2 && !this.hasNeighborWithin(nArray[0], nArray[1], 3)) continue;
                arrayList3.add(nArray);
            }
            list2 = this.mergeDistinctMoves(list2, arrayList3, Math.min(Math.max(n2 * 2, 16), list.size()));
        }
        if (list2.size() < nLimit) {
            ArrayList<int[]> arrayList4 = new ArrayList<int[]>();
            for (int[] nArray : list) {
                if (this.moveCount >= 2 && !this.hasNeighborWithin(nArray[0], nArray[1], 4)) continue;
                arrayList4.add(nArray);
            }
            list2 = this.mergeDistinctMoves(list2, arrayList4, Math.min(Math.max(n2 * 2, 20), list.size()));
        }
        int n3 = this.oppositeColor(n);
        ArrayList<ScoredMove> arrayList2 = new ArrayList<ScoredMove>();
        for (int[] nArray : list2) {
            if (this.isSearchTimeUp()) break;
            MoveTactics moveTactics = this.analyzeMoveTactics(nArray[0], nArray[1], n);
            MoveTactics moveTactics2 = this.analyzeMoveTactics(nArray[0], nArray[1], n3);
            int n4 = this.tacticalSeverity(moveTactics);
            int n5 = this.tacticalSeverity(moveTactics2);
            int n6 = this.forkSeverityIfPlaced(nArray[0], nArray[1], n);
            int n7 = this.quickEvaluateMove(nArray[0], nArray[1], n, aiProfile) + n6 * 140000 + n4 * 60000 - n5 * 8000 + this.getHistoryScore(nArray[0], nArray[1], n) * 12;
            if (this.moveCount <= 16) {
                n7 += this.gameStyleScore(nArray[0], nArray[1], n);
            }
            arrayList2.add(new ScoredMove(nArray[0], nArray[1], n7));
        }
        arrayList2.sort((scoredMove, scoredMove2) -> Integer.compare(scoredMove2.score, scoredMove.score));
        int n8 = Math.min(n2, arrayList2.size());
        for (int i = 0; i < n8; ++i) {
            ScoredMove scoredMove = (ScoredMove)arrayList2.get(i);
            arrayList.add(new int[]{scoredMove.row, scoredMove.col});
        }
        return arrayList.isEmpty() ? list2 : arrayList;
    }

    private int[] pickOpeningResponseMove(int n, AiProfile aiProfile) {
        List<int[]> list = this.getLegalMovesForColor(n);
        if (list.isEmpty()) {
            return null;
        }
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] move : list) {
            int n2 = Math.max(Math.abs(move[0] - 7), Math.abs(move[1] - 7));
            if (n2 > 1) continue;
            arrayList.add(move);
        }
        List<int[]> list2 = arrayList.isEmpty() ? this.getLocalizedAIMovesForColor(n, 2) : arrayList;
        if (list2.isEmpty()) {
            list2 = list;
        }
        List<int[]> candidates = this.selectLightweightCandidates(list2, n, Math.min(10, list2.size()), aiProfile);
        if (candidates.isEmpty()) {
            candidates = list2;
        }
        return this.chooseTopMoveWithNoise(candidates, n, aiProfile, 1);
    }

    private int brickStackPenalty(int n, int n2, int n3) {
        int[][] nArrayArray;
        int n4 = 0;
        int n5 = this.adjacentStoneCount(n, n2, n3);
        for (int[] nArray : nArrayArray = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            int n6 = n + nArray[0];
            int n7 = n2 + nArray[1];
            int n8 = n + nArray[0] * 2;
            int n9 = n2 + nArray[1] * 2;
            if (!this.inRange(n8, n9) || this.board[n8][n9] != n3 || !this.inRange(n6, n7) || this.board[n6][n7] != 0) continue;
            ++n4;
        }
        int n10 = n4 * 180;
        if (n4 >= 1 && n5 == 0) {
            n10 += 140;
        }
        return n10;
    }

    private int openingVarietyScore(int n, int n2, int n3) {
        int n4;
        int n5 = 0;
        int n6 = Math.abs(n - 7) + Math.abs(n2 - 7);
        n5 += (16 - n6) * 6;
        if (this.moveCount <= 2) {
            if (n6 > 6) {
                n5 -= 2200;
            }
            if (this.moveCount >= 1 && !this.hasNeighborWithin(n, n2, 2)) {
                n5 -= 1400;
            }
        }
        if (this.moveCount == 1) {
            n4 = Math.max(Math.abs(n - 7), Math.abs(n2 - 7));
            if (n4 == 1) {
                n5 += 1800;
            } else if (n4 == 2) {
                n5 -= 900;
            } else if (n4 >= 3) {
                n5 -= 1800;
            }
        }
        if ((n4 = this.adjacentStoneCount(n, n2, n3)) >= 3) {
            n5 -= 120;
        } else if (n4 == 0) {
            n5 += 40;
        }
        int n7 = this.gameStyleScore(n, n2, n3);
        return n5 + n7 * (this.moveCount <= 12 ? 2 : 1);
    }

    private int gameStyleScore(int n, int n2, int n3) {
        if (this.moveCount > 16) {
            return 0;
        }
        int n4 = Math.abs(n - 7);
        int n5 = Math.abs(n2 - 7);
        int n6 = n4 + n5;
        int n7 = Math.max(n4, n5);
        int n8 = Math.max(0, 10 - n6) * 2;
        int n9 = switch (this.openingStyleFlavor) {
            case 0 -> n4 == n5 ? 3 : 0;
            case 1 -> n7 <= 2 ? 2 : 0;
            case 2 -> n6 <= 3 ? 2 : 0;
            default -> n4 == 0 || n5 == 0 ? 2 : 0;
        };
        int n10 = this.moveCount <= 8 ? (this.hasNeighborWithin(n, n2, 1) ? 2 : 0) : 0;
        return n8 + n9 + n10;
    }

    private int currentMaxContinuous(int n) {
        int n2 = 0;
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != n) continue;
                for (int[] nArray : DIRS) {
                    int n3;
                    int n4 = i - nArray[0];
                    int n5 = j - nArray[1];
                    if (this.inRange(n4, n5) && this.board[n4][n5] == n || (n3 = 1 + this.countOneDirection(i, j, nArray[0], nArray[1], n)) <= n2) continue;
                    n2 = n3;
                }
            }
        }
        return n2;
    }

    private AiProfile getAiProfile(int n) {
        int n2 = Math.max(1, Math.min(10, n));
        int n3 = this.hiddenStage11 ? 11 : n2;
        int n4 = switch (n3) {
            case 11 -> 11;
            case 10 -> 8;
            case 9 -> 7;
            case 8 -> 5;
            case 7 -> 4;
            case 6 -> 3;
            case 5 -> 3;
            case 4 -> 2;
            case 3 -> 2;
            case 2 -> 1;
            default -> 0;
        };
        int n5 = switch (n3) {
            case 11 -> 30;
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
        int n6 = switch (n3) {
            case 11 -> 4;
            case 9, 10 -> 3;
            case 7, 8 -> 3;
            case 5, 6 -> 2;
            default -> 2;
        };
        int n7 = switch (n3) {
            case 6, 7, 8, 9, 10, 11 -> 2;
            case 3, 4, 5 -> 3;
            default -> 4;
        };
        int n8 = switch (n3) {
            case 11 -> 27;
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
        int n9 = switch (n3) {
            case 11 -> 8;
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
        int n10 = switch (n3) {
            case 11 -> 22;
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
        int n11 = switch (n3) {
            case 11 -> 0;
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
        boolean bl = n3 >= 9;
        return new AiProfile(n4, n5, n6, n7, n8, n9, n10, n11, bl);
    }

    private int oppositeColor(int n) {
        return n == 1 ? 2 : 1;
    }

    private int centerMassScore(int n) {
        int n2 = 0;
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != n) continue;
                n2 += 14 - (Math.abs(i - 7) + Math.abs(j - 7));
            }
        }
        return n2;
    }

    private boolean hasNeighborWithin(int n, int n2, int n3) {
        for (int i = -n3; i <= n3; ++i) {
            for (int j = -n3; j <= n3; ++j) {
                int n4;
                int n5;
                if (i == 0 && j == 0 || !this.inRange(n5 = n + i, n4 = n2 + j) || this.board[n5][n4] == 0) continue;
                return true;
            }
        }
        return false;
    }

    private List<int[]> getLegalMovesForCurrentTurn() {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        int n = this.getCurrentTurnColor();
        if (this.mode == RuleMode.RENJU_ADVANCED) {
            switch (this.advancedPhase.ordinal()) {
                case 1: {
                    if (this.board[7][7] == 0) {
                        arrayList.add(new int[]{7, 7});
                    }
                    return arrayList;
                }
                case 2: {
                    for (int i = 0; i < 15; ++i) {
                        for (int j = 0; j < 15; ++j) {
                            if (this.board[i][j] != 0 || !this.isAroundCenter8(i, j)) continue;
                            arrayList.add(new int[]{i, j});
                        }
                    }
                    return arrayList;
                }
                case 3: {
                    for (int i = 0; i < 15; ++i) {
                        for (int j = 0; j < 15; ++j) {
                            if (this.board[i][j] != 0 || !this.isWithinCenterFiveByFive(i, j)) continue;
                            arrayList.add(new int[]{i, j});
                        }
                    }
                    return arrayList;
                }
                case 4: 
                case 5: {
                    break;
                }
                case 6: {
                    for (int i = 0; i < 15; ++i) {
                        for (int j = 0; j < 15; ++j) {
                            if (this.board[i][j] != 0 || this.isSecondFifthSymmetryEquivalent(i, j)) continue;
                            arrayList.add(new int[]{i, j});
                        }
                    }
                    return arrayList;
                }
                case 7: {
                    for (int[] nArray : this.fifthCandidates) {
                        arrayList.add(new int[]{nArray[0], nArray[1]});
                    }
                    return arrayList;
                }
            }
        }
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != 0 || n == 1 && this.isRenjuStyle() && (this.mode != RuleMode.RENJU_ADVANCED || this.advancedPhase == AdvancedPhase.NORMAL) && this.isForbiddenCellForBlack(i, j)) continue;
                arrayList.add(new int[]{i, j});
            }
        }
        return arrayList;
    }

    private List<int[]> getLegalMovesForColor(int n) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != 0 || n == 1 && this.isRenjuStyle() && (this.mode != RuleMode.RENJU_ADVANCED || this.advancedPhase == AdvancedPhase.NORMAL) && this.isForbiddenCellForBlack(i, j)) continue;
                arrayList.add(new int[]{i, j});
            }
        }
        return arrayList;
    }

    private List<int[]> getLocalizedAIMovesForColor(int n, int n2) {
        List<int[]> list = this.getLegalMovesForColor(n);
        if (this.moveCount < 2) {
            return list;
        }
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list) {
            if (!this.hasNeighborWithin(nArray[0], nArray[1], n2)) continue;
            arrayList.add(nArray);
        }
        return arrayList.isEmpty() ? list : arrayList;
    }

    private int[] pickRemoveCandidateMove() {
        int n;
        int n2;
        if (this.fifthCandidates.size() != 2) {
            return null;
        }
        int[] nArray = this.fifthCandidates.get(0);
        int[] nArray2 = this.fifthCandidates.get(1);
        int n3 = this.localShapeScore(nArray[0], nArray[1], 1);
        int n4 = n2 = n3 >= (n = this.localShapeScore(nArray2[0], nArray2[1], 1)) ? 0 : 1;
        if (this.random.nextInt(10) >= this.computerDifficulty) {
            n2 = 1 - n2;
        }
        int[] nArray3 = this.fifthCandidates.get(n2);
        return new int[]{nArray3[0], nArray3[1]};
    }

    private boolean isWinningIfPlaced(int n, int n2, int n3) {
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        boolean bl = n3 == 1 && this.isRenjuStyle() && (this.mode != RuleMode.RENJU_ADVANCED || this.advancedPhase == AdvancedPhase.NORMAL) ? !this.isOverline(n, n2, 1) && this.isExactFive(n, n2, 1) && !this.isForbiddenByRenju(n, n2) : this.isFiveOrMore(n, n2, n3);
        this.board[n][n2] = n4;
        return bl;
    }

    private int localShapeScore(int n, int n2, int n3) {
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        int n5 = 0;
        for (int[] nArray : DIRS) {
            int n6 = this.countContinuous(n, n2, nArray[0], nArray[1], n3);
            n5 = Math.max(n5, n6);
        }
        this.board[n][n2] = n4;
        return n5;
    }

    private int localOpenEndedScore(int n, int n2, int n3) {
        int n4 = this.board[n][n2];
        this.board[n][n2] = n3;
        int n5 = 0;
        for (int[] nArray : DIRS) {
            int n6 = this.countOneDirection(n, n2, nArray[0], nArray[1], n3);
            int n7 = this.countOneDirection(n, n2, -nArray[0], -nArray[1], n3);
            int n8 = n6 + n7 + 1;
            boolean bl = this.isOpenEnd(n, n2, nArray[0], nArray[1], n6, n3);
            boolean bl2 = this.isOpenEnd(n, n2, -nArray[0], -nArray[1], n7, n3);
            int n9 = (bl ? 1 : 0) + (bl2 ? 1 : 0);
            if (n8 >= 4 && n9 >= 1) {
                n5 += 7;
                continue;
            }
            if (n8 == 3 && n9 == 2) {
                n5 += 5;
                continue;
            }
            if (n8 == 3 && n9 == 1) {
                n5 += 3;
                continue;
            }
            if (n8 != 2 || n9 != 2) continue;
            n5 += 2;
        }
        this.board[n][n2] = n4;
        return n5;
    }

    private boolean isOpenEnd(int n, int n2, int n3, int n4, int n5, int n6) {
        int n7 = n + n3 * (n5 + 1);
        int n8 = n2 + n4 * (n5 + 1);
        if (!this.inRange(n7, n8)) {
            return false;
        }
        return this.board[n7][n8] == 0;
    }

    private int adjacentStoneCount(int n, int n2, int n3) {
        int n4 = 0;
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                int n5;
                int n6;
                if (i == 0 && j == 0 || !this.inRange(n6 = n + i, n5 = n2 + j) || this.board[n6][n5] != n3) continue;
                ++n4;
            }
        }
        return n4;
    }

    private int opponentContactScore(int n, int n2, int n3) {
        int n4 = this.oppositeColor(n3);
        int n5 = 0;
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                if (i == 0 && j == 0) continue;
                int n6 = n + i;
                int n7 = n2 + j;
                if (!this.inRange(n6, n7) || this.board[n6][n7] != n4) continue;
                int n8 = Math.max(Math.abs(i), Math.abs(j));
                n5 += n8 == 1 ? 6 : 2;
            }
        }
        return n5;
    }

    private int fightProximityScore(int n, int n2, int n3) {
        int n4 = this.oppositeColor(n3);
        int n5 = this.opponentContactScore(n, n2, n3);
        int n6 = this.adjacentStoneCount(n, n2, n3);
        int n7 = this.adjacentStoneCount(n, n2, n4);
        int n8 = n5 * 90 + n7 * 24 + n6 * 10;
        if (this.moveCount <= 18) {
            if (n5 == 0 && n7 == 0 && n6 == 0) {
                n8 -= 1400;
            } else if (n5 == 0 && n7 == 0) {
                n8 -= 500;
            } else if (n5 == 0) {
                n8 -= 120;
            }
        } else if (n5 == 0 && n7 == 0 && n6 == 0) {
            n8 -= 300;
        }
        return n8;
    }

    private void placeStandardMove(int n, int n2) {
        int n3;
        int[] nArray;
        if (this.lastMove == null) {
            nArray = null;
        } else {
            int[] nArray2 = new int[2];
            nArray2[0] = this.lastMove[0];
            nArray = nArray2;
            nArray2[1] = this.lastMove[1];
        }
        int[] nArray3 = nArray;
        int n4 = this.moveCount;
        this.board[n][n2] = n3 = this.blackTurn ? 1 : 2;
        this.moveOrder[n][n2] = ++this.moveCount;
        this.lastMove = new int[]{n, n2};
        if (n3 == 1 && this.isRenjuStyle()) {
            if (this.isOverline(n, n2, 1)) {
                this.rollbackMove(n, n2, n4, nArray3);
                JOptionPane.showMessageDialog(this.boardPanel, "\uc7a5\ubaa9(6\ubaa9 \uc774\uc0c1)\uc740 \ud751 \uae08\uc218\uc785\ub2c8\ub2e4.");
                return;
            }
            if (this.isExactFive(n, n2, 1)) {
                this.gameOver = true;
                this.statusLabel.setText("\ud751 \uc2b9\ub9ac");
                JOptionPane.showMessageDialog(this.boardPanel, "\ud751 \uc2b9\ub9ac");
                return;
            }
            if (this.isForbiddenByRenju(n, n2)) {
                this.rollbackMove(n, n2, n4, nArray3);
                JOptionPane.showMessageDialog(this.boardPanel, "\ud751 \uae08\uc218\uc785\ub2c8\ub2e4. (3-3 \ub610\ub294 4-4)");
                return;
            }
        } else if (n3 == 1) {
            if (this.isFiveOrMore(n, n2, 1)) {
                this.gameOver = true;
                this.statusLabel.setText("\ud751 \uc2b9\ub9ac");
                JOptionPane.showMessageDialog(this.boardPanel, "\ud751 \uc2b9\ub9ac");
                return;
            }
        } else if (this.isFiveOrMore(n, n2, 2)) {
            this.gameOver = true;
            this.statusLabel.setText("\ubc31 \uc2b9\ub9ac");
            JOptionPane.showMessageDialog(this.boardPanel, "\ubc31 \uc2b9\ub9ac");
            return;
        }
        this.blackTurn = !this.blackTurn;
        this.updateTurnStatus();
        this.repaintBoardNow();
        this.tryComputerMove();
    }

    private void handleAdvancedOpeningPlacement(int n, int n2) {
        switch (this.advancedPhase.ordinal()) {
            case 1: {
                if (n != 7 || n2 != 7) {
                    this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 1\uc218\ub294 \ucc9c\uc6d0(\uc911\uc559)\uc5d0 \ub46c\uc57c \ud569\ub2c8\ub2e4.");
                    this.repaintBoard();
                    return;
                }
                this.placeForcedMove(n, n2, 1, 1);
                this.blackTurn = false;
                this.advancedPhase = AdvancedPhase.OPENING_2_WHITE_AROUND_CENTER;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ubc31 2\uc218: \uc911\uc559 \uc8fc\ubcc0 8\uacf3 \uc911 \ud558\ub098\uc5d0 \ub450\uc138\uc694.");
                break;
            }
            case 2: {
                if (!this.isAroundCenter8(n, n2)) {
                    this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ubc31 2\uc218\ub294 \uc911\uc559 \uc8fc\ubcc0 8\uacf3 \uc911 \ud558\ub098\uc5ec\uc57c \ud569\ub2c8\ub2e4.");
                    this.repaintBoard();
                    return;
                }
                this.placeForcedMove(n, n2, 2, 2);
                this.blackTurn = true;
                this.advancedPhase = AdvancedPhase.OPENING_3_BLACK_WITHIN_TWO;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 3\uc218: \uc911\uc559 \uae30\uc900 5x5 \ubc94\uc704(1\u00b72\uc218 \uc81c\uc678)\uc5d0 \ub450\uc138\uc694.");
                break;
            }
            case 3: {
                int n3;
                boolean bl;
                if (!this.isWithinCenterFiveByFive(n, n2)) {
                    this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 3\uc218\ub294 \uc911\uc559 \uae30\uc900 5x5 \ubc94\uc704(1\u00b72\uc218 \uc81c\uc678)\uc5ec\uc57c \ud569\ub2c8\ub2e4.");
                    this.repaintBoard();
                    return;
                }
                this.placeForcedMove(n, n2, 1, 3);
                this.repaintBoardNow();
                if (this.isComputerControllingColor(2)) {
                    bl = this.shouldComputerSwapAfterThirdMove();
                } else {
                    n3 = JOptionPane.showConfirmDialog(this.boardPanel, "\ubc31\uc774 \ud751/\ubc31 \uad50\uccb4(\uc2a4\uc651)\ud558\uc2dc\uaca0\uc2b5\ub2c8\uae4c?", "\ub80c\uc8fc \ubcf4\uac15\ub8f0 - \uc2a4\uc651", 0);
                    boolean bl2 = bl = n3 == 0;
                }
                if (bl) {
                    if (!this.isComputerControllingColor(2)) {
                        JOptionPane.showMessageDialog(this.boardPanel, "\ud50c\ub808\uc774\uc5b4\uc758 \ud751/\ubc31 \uc5ed\ud560\ub9cc \uad50\uccb4\ub429\ub2c8\ub2e4. \ub3cc \uc0c9\uc740 \uc720\uc9c0\ub429\ub2c8\ub2e4.");
                    }
                    this.playerColor = (n3 = this.playerColor) == 1 ? 2 : 1;
                    this.computerColor = this.playerColor == 1 ? 2 : 1;
                }
                this.blackTurn = false;
                this.advancedPhase = AdvancedPhase.OPENING_4_WHITE_FOURTH;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ubc31 4\uc218\ub97c \ub450\uc138\uc694.");
                break;
            }
            case 4: {
                this.placeForcedMove(n, n2, 2, 4);
                this.blackTurn = true;
                this.advancedPhase = AdvancedPhase.OPENING_5_BLACK_FIRST;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 5\uc218 \ud6c4\ubcf4 1\uac1c\ub97c \ub450\uc138\uc694.");
                break;
            }
            case 5: {
                this.placeForcedMove(n, n2, 1, 5);
                this.fifthCandidates.clear();
                this.fifthCandidates.add(new int[]{n, n2});
                this.blackTurn = true;
                this.advancedPhase = AdvancedPhase.OPENING_5_BLACK_SECOND;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 5\uc218 \ud6c4\ubcf4 2\ubc88\uc9f8\ub97c \ub450\uc138\uc694. (\ub300\uce6d \uae08\uc9c0)");
                break;
            }
            case 6: {
                if (this.isSecondFifthSymmetryEquivalent(n, n2)) {
                    this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ub450 \ud6c4\ubcf4\ub294 \ub300\uce6d\uc73c\ub85c \uac19\uc740 \uc790\ub9ac \ucde8\uae09\uc774\ub77c \ubd88\uac00\ud569\ub2c8\ub2e4.");
                    this.repaintBoard();
                    return;
                }
                this.placeForcedMove(n, n2, 1, 5);
                this.fifthCandidates.add(new int[]{n, n2});
                this.blackTurn = false;
                this.advancedPhase = AdvancedPhase.OPENING_5_WHITE_REMOVE;
                this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ubc31\uc774 \uc81c\uac70\ud560 \ud751 5\uc218 \ud6c4\ubcf4\ub97c \ud074\ub9ad\ud558\uc138\uc694.");
                break;
            }
        }
        this.repaintBoardNow();
        this.tryComputerMove();
    }

    private void handleAdvancedRemoveChoice(int n, int n2) {
        if (this.fifthCandidates.size() != 2) {
            return;
        }
        int n3 = this.indexOfFifthCandidate(n, n2);
        if (n3 < 0) {
            this.statusLabel.setText("[\ub80c\uc8fc \ubcf4\uac15\ub8f0] \ud751 5\uc218 \ud6c4\ubcf4 2\uac1c \uc911 \uc81c\uac70\ud560 \uc218\ub97c \ud074\ub9ad\ud558\uc138\uc694.");
            this.repaintBoard();
            return;
        }
        int[] nArray = this.fifthCandidates.get(n3);
        int[] nArray2 = this.fifthCandidates.get(1 - n3);
        this.board[nArray[0]][nArray[1]] = 0;
        this.moveOrder[nArray[0]][nArray[1]] = 0;
        this.moveCount = 5;
        this.lastMove = new int[]{nArray2[0], nArray2[1]};
        this.fifthCandidates.clear();
        this.advancedPhase = AdvancedPhase.NORMAL;
        this.blackTurn = false;
        this.statusLabel.setText("\ubcf4\uac15 \uc624\ud504\ub2dd \uc885\ub8cc: \ubc31 \ucc28\ub840");
        this.repaintBoardNow();
        this.tryComputerMove();
    }

    private int indexOfFifthCandidate(int n, int n2) {
        for (int i = 0; i < this.fifthCandidates.size(); ++i) {
            int[] nArray = this.fifthCandidates.get(i);
            if (nArray[0] != n || nArray[1] != n2) continue;
            return i;
        }
        return -1;
    }

    private void placeForcedMove(int n, int n2, int n3, int n4) {
        this.board[n][n2] = n3;
        this.moveOrder[n][n2] = n4;
        this.moveCount = Math.max(this.moveCount, n4);
        this.lastMove = new int[]{n, n2};
    }

    private void rollbackMove(int n, int n2, int n3, int[] nArray) {
        this.board[n][n2] = 0;
        this.moveOrder[n][n2] = 0;
        this.moveCount = n3;
        this.lastMove = nArray;
        this.repaintBoardNow();
    }

    private void updateTurnStatus() {
        this.statusLabel.setText(this.blackTurn ? "\ud751 \ucc28\ub840" : "\ubc31 \ucc28\ub840");
    }

    private void setThinkingStatus(boolean bl) {
        String string = this.stripThinkingDecorations(this.statusLabel.getText());
        if (bl) {
            this.statusLabel.setText(string + THINKING_SUFFIX);
        } else {
            this.statusLabel.setText(string);
            this.thinkingPreviewMarks.clear();
        }
        this.repaintBoardNow();
    }

    private void updateThinkingCandidate(int n, int n2) {
        if (!this.computerThinking) {
            return;
        }
        this.thinkingPreviewMarks.add(n * 15 + n2);
        this.repaintBoardNow();
        if (this.boardPanel != null) {
            this.boardPanel.paintImmediately(this.boardPanel.getVisibleRect());
        }
        this.statusLabel.paintImmediately(this.statusLabel.getVisibleRect());
    }

    List<int[]> getThinkingPreviewMoves() {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        if (!this.computerThinking || this.thinkingPreviewMarks.isEmpty()) {
            return arrayList;
        }
        for (Integer n : this.thinkingPreviewMarks) {
            int n2 = n / 15;
            int n3 = n % 15;
            arrayList.add(new int[]{n2, n3});
        }
        return arrayList;
    }

    private String stripThinkingDecorations(String string) {
        String string2 = string;
        if (string2.endsWith(THINKING_SUFFIX)) {
            string2 = string2.substring(0, string2.length() - THINKING_SUFFIX.length());
        }
        return string2;
    }

    private boolean isSearchTimeUp() {
        return System.nanoTime() >= this.searchDeadlineNanos;
    }

    private int getSearchTimeLimitMillis(AiProfile aiProfile) {
        if (this.hiddenStage11) {
            return 15000;
        }
        return switch (this.computerDifficulty) {
            case 10 -> {
                if (this.moveCount < 18) {
                    yield 3500;
                }
                yield 7000;
            }
            case 9 -> {
                if (this.moveCount < 18) {
                    yield 2500;
                }
                yield 5000;
            }
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
        if (this.boardPanel != null) {
            this.boardPanel.repaint();
        }
    }

    private void repaintBoardNow() {
        if (this.boardPanel != null) {
            this.boardPanel.repaint();
            this.boardPanel.paintImmediately(this.boardPanel.getVisibleRect());
        }
    }

    private boolean isRenjuStyle() {
        return this.mode == RuleMode.RENJU || this.mode == RuleMode.RENJU_ADVANCED;
    }

    private boolean isComputerControllingColor(int n) {
        return this.computerEnabled && this.computerColor == n;
    }

    private boolean shouldComputerSwapAfterThirdMove() {
        int n = this.openingColorScore(1);
        int n2 = this.openingColorScore(2);
        int n3 = n - n2;
        int n4 = Math.max(0, 11 - this.computerDifficulty);
        int n5 = n4 == 0 ? 0 : this.random.nextInt(n4 * 2 + 1) - n4;
        return n3 + n5 >= 2;
    }

    private int openingColorScore(int n) {
        int n2 = 0;
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] != n) continue;
                int n3 = 14 - (Math.abs(i - 7) + Math.abs(j - 7));
                int n4 = 0;
                for (int[] nArray : DIRS) {
                    n4 += this.countOneDirection(i, j, nArray[0], nArray[1], n);
                    n4 += this.countOneDirection(i, j, -nArray[0], -nArray[1], n);
                }
                n2 += n3 + n4 * 3;
            }
        }
        return n2;
    }

    private boolean isAroundCenter8(int n, int n2) {
        int n3 = Math.abs(n - 7);
        int n4 = Math.abs(n2 - 7);
        return n3 <= 1 && n4 <= 1 && (n3 != 0 || n4 != 0);
    }

    private boolean isWithinCenterFiveByFive(int n, int n2) {
        int n3 = n - 7;
        int n4 = n2 - 7;
        return Math.max(Math.abs(n3), Math.abs(n4)) <= 2;
    }

    private boolean isSecondFifthSymmetryEquivalent(int n, int n2) {
        int n3;
        if (this.fifthCandidates.size() != 1) {
            return false;
        }
        int[] nArray = this.fifthCandidates.get(0);
        int n4 = nArray[0];
        int n5 = nArray[1];
        if (!this.inRange(n, n2) || this.board[n][n2] != 0) {
            return false;
        }
        int[][] nArray2 = new int[15][15];
        for (n3 = 0; n3 < 15; ++n3) {
            for (int i = 0; i < 15; ++i) {
                nArray2[n3][i] = this.board[n3][i];
            }
        }
        nArray2[n4][n5] = 0;
        nArray2[n][n2] = 1;
        for (n3 = 0; n3 < 8; ++n3) {
            if (!this.matchesUnderTransform(nArray2, n3)) continue;
            return true;
        }
        return false;
    }

    private boolean matchesUnderTransform(int[][] nArray, int n) {
        for (int i = 0; i < 15; ++i) {
            for (int j = 0; j < 15; ++j) {
                if (this.board[i][j] == 0) continue;
                int[] nArray2 = this.applySymmetry(i, j, n);
                if (!this.inRange(nArray2[0], nArray2[1])) {
                    return false;
                }
                if (nArray[nArray2[0]][nArray2[1]] == this.board[i][j]) continue;
                return false;
            }
        }
        return true;
    }

    private int[] applySymmetry(int n, int n2, int n3) {
        int n5 = n - 7;
        int n6 = n2 - 7;
        int transformedRow;
        int transformedCol;
        switch (n3) {
            case 0:
                transformedRow = n5;
                transformedCol = n6;
                break;
            case 1:
                transformedRow = n5;
                transformedCol = -n6;
                break;
            case 2:
                transformedRow = -n5;
                transformedCol = n6;
                break;
            case 3:
                transformedRow = -n5;
                transformedCol = -n6;
                break;
            case 4:
                transformedRow = n6;
                transformedCol = n5;
                break;
            case 5:
                transformedRow = n6;
                transformedCol = -n5;
                break;
            case 6:
                transformedRow = -n6;
                transformedCol = n5;
                break;
            default:
                transformedRow = -n6;
                transformedCol = -n5;
        }
        return new int[]{7 + transformedRow, 7 + transformedCol};
    }

    private boolean isForbiddenByRenju(int n, int n2) {
        int n3 = 0;
        int n4 = 0;
        for (int[] nArray : DIRS) {
            int n5 = this.countWinningSpotsInDirection(n, n2, nArray[0], nArray[1], 1).size();
            if (n5 >= 1) {
                ++n4;
            }
            if (!this.createsOpenThreeInDirection(n, n2, nArray[0], nArray[1])) continue;
            ++n3;
        }
        return n3 >= 2 || n4 >= 2;
    }

    private boolean createsOpenThreeInDirection(int n, int n2, int n3, int n4) {
        for (int i = -4; i <= 4; ++i) {
            int n5 = n + n3 * i;
            int n6 = n2 + n4 * i;
            if (!this.inRange(n5, n6) || this.board[n5][n6] != 0) continue;
            this.board[n5][n6] = 1;
            boolean bl = !this.isOverline(n5, n6, 1);
            boolean bl2 = bl && this.isOpenFourLinkedToAnchor(n, n2, n5, n6, n3, n4);
            this.board[n5][n6] = 0;
            if (!bl2) continue;
            return true;
        }
        return false;
    }

    private boolean isOpenFourLinkedToAnchor(int n, int n2, int n3, int n4, int n5, int n6) {
        int n7 = this.countWinningSpotsInDirection(n3, n4, n5, n6, 1).size();
        if (n7 < 2) {
            return false;
        }
        int n8 = this.board[n][n2];
        this.board[n][n2] = 0;
        int n9 = this.countWinningSpotsInDirection(n3, n4, n5, n6, 1).size();
        this.board[n][n2] = n8;
        return n9 < 2;
    }

    private Set<Integer> countWinningSpotsInDirection(int n, int n2, int n3, int n4, int n5) {
        HashSet<Integer> hashSet = new HashSet<Integer>();
        for (int i = -4; i <= 0; ++i) {
            int n6;
            int n7;
            int n8;
            int n9 = i + 4;
            if (i > 0 || 0 > n9) continue;
            int n10 = 0;
            int n11 = 0;
            int n12 = 0;
            boolean bl = false;
            for (n8 = i; n8 <= n9; ++n8) {
                n7 = n + n3 * n8;
                n6 = n2 + n4 * n8;
                if (!this.inRange(n7, n6) || this.board[n7][n6] == 2) {
                    bl = true;
                    break;
                }
                if (this.board[n7][n6] == n5) {
                    ++n10;
                    continue;
                }
                if (this.board[n7][n6] != 0) continue;
                ++n11;
                n12 = n8;
            }
            if (bl || n10 != 4 || n11 != 1 || !this.inRange(n8 = n + n3 * n12, n7 = n2 + n4 * n12) || this.board[n8][n7] != 0) continue;
            this.board[n8][n7] = n5;
            n6 = (n5 == 1 && this.isRenjuPlayPhase() ? this.isExactFive(n8, n7, n5) : this.isFiveOrMore(n8, n7, n5)) ? 1 : 0;
            if (n5 == 1 && this.isRenjuPlayPhase() && this.isOverline(n8, n7, n5)) {
                n6 = 0;
            }
            this.board[n8][n7] = 0;
            if (n6 == 0) continue;
            hashSet.add(n12);
        }
        return hashSet;
    }

    private boolean isExactFive(int n, int n2, int n3) {
        for (int[] nArray : DIRS) {
            int n4 = this.countContinuous(n, n2, nArray[0], nArray[1], n3);
            if (n4 != 5) continue;
            return true;
        }
        return false;
    }

    private boolean isFiveOrMore(int n, int n2, int n3) {
        for (int[] nArray : DIRS) {
            if (this.countContinuous(n, n2, nArray[0], nArray[1], n3) < 5) continue;
            return true;
        }
        return false;
    }

    private boolean isOverline(int n, int n2, int n3) {
        for (int[] nArray : DIRS) {
            if (this.countContinuous(n, n2, nArray[0], nArray[1], n3) < 6) continue;
            return true;
        }
        return false;
    }

    private int countContinuous(int n, int n2, int n3, int n4, int n5) {
        int n6 = 1;
        n6 += this.countOneDirection(n, n2, n3, n4, n5);
        return n6 += this.countOneDirection(n, n2, -n3, -n4, n5);
    }

    private int countOneDirection(int n, int n2, int n3, int n4, int n5) {
        int n6 = 0;
        int n7 = n + n3;
        int n8 = n2 + n4;
        while (this.inRange(n7, n8) && this.board[n7][n8] == n5) {
            ++n6;
            n7 += n3;
            n8 += n4;
        }
        return n6;
    }

    private boolean inRange(int n, int n2) {
        return n >= 0 && n < 15 && n2 >= 0 && n2 < 15;
    }

}
