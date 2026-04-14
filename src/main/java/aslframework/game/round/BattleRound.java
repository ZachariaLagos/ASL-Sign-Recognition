package aslframework.game.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single round in a battle session.
 *
 * <p>Each round has one target letter. All active players attempt the letter;
 * those who fail are eliminated. The round records who passed and who failed.
 *
 * <p>Mutation ({@link #recordPass} / {@link #recordFail}) is package-private:
 * only {@link aslframework.game.session.BattleSession} (same package structure)
 * drives round state. External callers are read-only.
 */
public class BattleRound {

  private final int    roundNumber;
  private final String targetLetter;
  private final int    difficultyTier;

  private final List<String> passedPlayers = new ArrayList<>();
  private final List<String> failedPlayers = new ArrayList<>();

  /**
   * Constructs a new {@code BattleRound}.
   *
   * @param roundNumber    1-based round index
   * @param targetLetter   the ASL letter all players must sign this round
   * @param difficultyTier the difficulty tier of the letter (1–5)
   */
  public BattleRound(int roundNumber, String targetLetter, int difficultyTier) {
    this.roundNumber    = roundNumber;
    this.targetLetter   = targetLetter;
    this.difficultyTier = difficultyTier;
  }

  // ── Mutation — called by BattleSession to record outcomes ────────────

  /**
   * Records that a player passed this round.
   * Called by {@code BattleSession} to record outcome.
   */
  public void recordPass(String playerId) {
    passedPlayers.add(playerId);
  }

  /**
   * Records that a player failed this round.
   * Called by {@code BattleSession} to record outcome.
   */
  public void recordFail(String playerId) {
    failedPlayers.add(playerId);
  }

  // ── Public read-only accessors ───────────────────────────────────────────────

  /** Returns the 1-based round number. */
  public int getRoundNumber() { return roundNumber; }

  /** Returns the target letter for this round. */
  public String getTargetLetter() { return targetLetter; }

  /** Returns the difficulty tier of this round's letter (1–5). */
  public int getDifficultyTier() { return difficultyTier; }

  /** Returns an unmodifiable view of players who passed. */
  public List<String> getPassedPlayers() {
    return Collections.unmodifiableList(passedPlayers);
  }

  /** Returns an unmodifiable view of players who failed. */
  public List<String> getFailedPlayers() {
    return Collections.unmodifiableList(failedPlayers);
  }

  /**
   * Returns whether every player in {@code activePlayers} has submitted.
   *
   * @param activePlayers player IDs who were active when this round opened
   * @return {@code true} if all have a pass or fail result
   */
  public boolean isComplete(List<String> activePlayers) {
    return activePlayers.stream()
        .allMatch(id -> passedPlayers.contains(id) || failedPlayers.contains(id));
  }

  /** Returns whether a given player has already submitted this round. */
  public boolean hasSubmitted(String playerId) {
    return passedPlayers.contains(playerId) || failedPlayers.contains(playerId);
  }

  @Override
  public String toString() {
    return String.format("BattleRound(round=%d, letter=%s, tier=%d, passed=%d, failed=%d)",
        roundNumber, targetLetter, difficultyTier,
        passedPlayers.size(), failedPlayers.size());
  }
}
