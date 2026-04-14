package aslframework.game.result;

import aslframework.game.GameMode;
import aslframework.game.round.BattleRound;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result for a completed battle session.
 *
 * <p>Carries battle-specific data: winner(s), player rankings, completed
 * rounds, and per-player round counts.
 */
public class BattleResult implements GameResult {

  private final List<String>           winners;
  private final List<String>           rankedPlayers;
  private final List<BattleRound>      rounds;
  private final Map<String, Integer>   roundsClearedPerPlayer;
  private final boolean                completed;

  /**
   * Constructs a {@code BattleResult}.
   *
   * @param winners                player ID(s) who won (joint winners if multiple)
   * @param rankedPlayers          all players sorted best → worst by rounds cleared
   * @param rounds                 completed rounds in order
   * @param roundsClearedPerPlayer map of player ID → rounds cleared
   * @param completed              {@code true} if the game ended naturally
   */
  public BattleResult(List<String>         winners,
                      List<String>         rankedPlayers,
                      List<BattleRound>    rounds,
                      Map<String, Integer> roundsClearedPerPlayer,
                      boolean              completed) {
    this.winners                = Collections.unmodifiableList(List.copyOf(winners));
    this.rankedPlayers          = Collections.unmodifiableList(List.copyOf(rankedPlayers));
    this.rounds                 = Collections.unmodifiableList(List.copyOf(rounds));
    this.roundsClearedPerPlayer = Collections.unmodifiableMap(Map.copyOf(roundsClearedPerPlayer));
    this.completed              = completed;
  }

  @Override public GameMode getMode()        { return GameMode.BATTLE; }
  @Override public boolean  isCompleted()    { return completed; }
  @Override public int      getTotalRounds() { return rounds.size(); }

  /**
   * Returns the winner(s). Multiple entries indicate a joint win.
   *
   * @return unmodifiable list of winner IDs; never null, may be empty
   *         if the game was abandoned before anyone was eliminated
   */
  public List<String> getWinners() { return winners; }

  /**
   * Returns the primary winner (first in the list), or {@code null} if
   * the winners list is empty.
   *
   * @return primary winner ID, or {@code null}
   */
  public String getWinnerId() {
    return winners.isEmpty() ? null : winners.get(0);
  }

  /**
   * Returns all players ranked from best (most rounds cleared) to worst.
   *
   * @return ordered list of player IDs
   */
  public List<String> getRankedPlayers() { return rankedPlayers; }

  /**
   * Returns the completed battle rounds in order.
   *
   * @return list of {@link BattleRound}s
   */
  public List<BattleRound> getRounds() { return rounds; }

  /**
   * Returns a map of player ID → number of rounds cleared.
   *
   * @return unmodifiable map
   */
  public Map<String, Integer> getRoundsClearedPerPlayer() {
    return roundsClearedPerPlayer;
  }

  @Override
  public String toString() {
    return String.format("BattleResult[winners=%s rounds=%d players=%d completed=%b]",
        winners, rounds.size(), rankedPlayers.size(), completed);
  }
}
