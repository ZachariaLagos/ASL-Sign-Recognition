package aslframework.game;

public class ScoringEngine {

  private static final double PASS_THRESHOLD = 0.8;

  public int calculateScore(double accuracy, int difficulty) {
    return (int)(accuracy * 100) * difficulty;
  }

  public boolean isPassed(double accuracy) {
    return accuracy >= PASS_THRESHOLD;
  }
}