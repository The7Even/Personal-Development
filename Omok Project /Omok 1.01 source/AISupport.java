import java.util.List;

class TacticalSnapshot {
  final int maxSeverity;
  final List<int[]> maxSeverityMoves;

  TacticalSnapshot(int maxSeverity, List<int[]> maxSeverityMoves) {
    this.maxSeverity = maxSeverity;
    this.maxSeverityMoves = maxSeverityMoves;
  }
}

class ForkThreatSnapshot {
  final int maxForkSeverity;
  final List<int[]> maxForkMoves;

  ForkThreatSnapshot(int maxForkSeverity, List<int[]> maxForkMoves) {
    this.maxForkSeverity = maxForkSeverity;
    this.maxForkMoves = maxForkMoves;
  }
}

class BuildUpThreatSnapshot {
  final int maxBuildSeverity;
  final List<int[]> maxBuildMoves;

  BuildUpThreatSnapshot(int maxBuildSeverity, List<int[]> maxBuildMoves) {
    this.maxBuildSeverity = maxBuildSeverity;
    this.maxBuildMoves = maxBuildMoves;
  }
}

class MoveTactics {
  boolean winNow;
  int open4;
  int closed4;
  int open3;
  int closed3;
  boolean doubleThreat43;
}

class PatternStats {
  int five;
  int open4;
  int closed4;
  int open3;
  int closed3;
  int open2;
  int doubleThreat43;
}

class ScoredMove {
  final int row;
  final int col;
  final int score;

  ScoredMove(int row, int col, int score) {
    this.row = row;
    this.col = col;
    this.score = score;
  }
}

class RiskMove {
  final int row;
  final int col;
  final int score;

  RiskMove(int row, int col, int score) {
    this.row = row;
    this.col = col;
    this.score = score;
  }
}

class TranspositionEntry {
  final int depth;
  final int score;
  final int flag;
  final int bestMove;

  TranspositionEntry(int depth, int score, int flag, int bestMove) {
    this.depth = depth;
    this.score = score;
    this.flag = flag;
    this.bestMove = bestMove;
  }
}

class AiProfile {
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
