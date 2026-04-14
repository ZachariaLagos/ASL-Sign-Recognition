package aslframework.game.session;

import aslframework.game.BattlePlayerState;
import aslframework.game.LetterDifficulty;
import aslframework.game.progression.DifficultyProgression;
import aslframework.game.progression.LetterProgression;
import aslframework.game.result.BattleResult;
import aslframework.game.result.GameResult;
import aslframework.game.round.BattleRound;
import aslframework.game.scoring.StandardScoringStrategy;
import aslframework.model.HandLandmark;
import aslframework.persistence.AttemptRecord;
import aslframework.recognition.GestureLibrary;
import aslframework.recognition.GestureRecognizer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-player elimination battle session — the "high-jump" game mode.
 *
 * <p>Contains only battle-specific logic. All recognition, attempt recording,
 * and guards live in {@link AbstractGameSession}. All output goes through the
 * injected {@link GameEventListener} — no {@code System.out} calls here.
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li>All registered players start active.</li>
 *   <li>Each round every active player signs the same letter.</li>
 *   <li>Failing eliminates the player immediately.</li>
 *   <li>Round closes automatically when all active players have submitted.</li>
 *   <li>Letters advance by difficulty (easiest → hardest) each round.</li>
 *   <li>Last player standing wins. Mass-elimination = joint win for top survivors.</li>
 * </ol>
 */
public class BattleSession extends AbstractGameSession {

  private final Map<String, BattlePlayerState> players;
  private final LetterProgression              progression;
  private final List<BattleRound>              completedRounds;
  private BattleRound                          currentRound;
  private List<String>                         winners;

  // ── Construction ────────────────────────────────────────────────────────────

  /**
   * Default constructor — difficulty-ordered progression, standard scoring,
   * no-op event listener (replace via {@link #setEventListener}).
   */
  public BattleSession(GestureRecognizer recognizer,
                       GestureLibrary library,
                       List<String> playerIds) {
    this(recognizer, library, playerIds, new DifficultyProgression(library));
  }

  /** Full constructor for custom progression (e.g. tests). */
  public BattleSession(GestureRecognizer recognizer,
                       GestureLibrary library,
                       List<String> playerIds,
                       LetterProgression progression) {
    super(recognizer, library, new StandardScoringStrategy());

    if (playerIds == null || playerIds.size() < 2) {
      throw new IllegalArgumentException(
          "Battle requires at least 2 players, got: "
              + (playerIds == null ? 0 : playerIds.size()));
    }
    if (progression.isExhausted()) {
      throw new IllegalStateException(
          "No reference gestures loaded. Run collect_reference_data.py first.");
    }

    this.progression     = progression;
    this.completedRounds = new ArrayList<>();
    this.winners         = new ArrayList<>();

    this.players = new LinkedHashMap<>();
    for (String id : playerIds) {
      players.put(id, new BattlePlayerState(id));
    }

    this.currentRound = openRound();
  }

  // ── GameSession ──────────────────────────────────────────────────────────────

  @Override
  public String getCurrentLetter() {
    return progression.current();
  }

  /**
   * Convenience single-player attempt (satisfies {@link GameSession} interface).
   * Delegates to {@link #submitAttempt} using the first active player.
   *
   * @throws IllegalStateException if no active players remain
   */
  @Override
  public AttemptRecord attempt(List<HandLandmark> userLandmarks) {
    String activeId = getActivePlayers().stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No active players remaining."));
    return submitAttempt(activeId, userLandmarks);
  }

  @Override
  public GameResult finish() {
    if (!finished) {
      List<String> stillActive = getActivePlayers();
      declareWinners(stillActive.isEmpty() ? List.of() : stillActive);
    }
    BattleResult result = buildResult();
    eventListener.onSessionFinished(result);
    return result;
  }

  // ── Battle-specific API ──────────────────────────────────────────────────────

  /**
   * Submits a recognition attempt for a specific player in the current round.
   *
   * <p>Once every active player has submitted, the round closes automatically.
   *
   * @param playerId      the submitting player's ID
   * @param userLandmarks 21 hand landmarks from the live camera
   * @return the {@link AttemptRecord} for this attempt
   * @throws IllegalStateException    if the game is already over
   * @throws IllegalArgumentException if the player is unknown, eliminated, or
   *                                  has already submitted this round
   */
  public AttemptRecord submitAttempt(String playerId,
                                     List<HandLandmark> userLandmarks) {
    requireNotFinished();
    BattlePlayerState player = requireActivePlayer(playerId);

    if (currentRound.hasSubmitted(playerId)) {
      throw new IllegalArgumentException(
          "Player already submitted this round: " + playerId);
    }

    String letter = currentRound.getTargetLetter();
    AttemptRecord record = evaluate(letter, userLandmarks);

    // BattlePlayerState.recordAttempt handles elimination on failure
    player.recordAttempt(record);

    if (record.isPassed()) {
      currentRound.recordPass(playerId);
    } else {
      currentRound.recordFail(playerId);
      eventListener.onPlayerEliminated(playerId, player.getRoundsCleared());
    }

    eventListener.onAttempt(letter, record, 0);

    // Close when no active player is still pending
    long pending = players.values().stream()
        .filter(p -> p.isActive() && !currentRound.hasSubmitted(p.getPlayerId()))
        .count();
    if (pending == 0) {
      closeRound();
    }

    return record;
  }

  /** Returns the currently active {@link BattleRound}, or {@code null} if over. */
  public BattleRound getCurrentRound() { return currentRound; }

  /** Returns completed rounds in order. */
  public List<BattleRound> getCompletedRounds() {
    return Collections.unmodifiableList(completedRounds);
  }

  /** Returns IDs of players still in the game. */
  public List<String> getActivePlayers() {
    return players.values().stream()
        .filter(BattlePlayerState::isActive)
        .map(BattlePlayerState::getPlayerId)
        .collect(Collectors.toList());
  }

  /** Returns IDs of eliminated players. */
  public List<String> getEliminatedPlayers() {
    return players.values().stream()
        .filter(BattlePlayerState::isEliminated)
        .map(BattlePlayerState::getPlayerId)
        .collect(Collectors.toList());
  }

  /** Returns the winner(s); empty until the game is over. */
  public List<String> getWinners() {
    return Collections.unmodifiableList(winners);
  }

  /** Returns the {@link BattlePlayerState} for the given player, or {@code null}. */
  public BattlePlayerState getPlayerState(String playerId) {
    return players.get(playerId);
  }

  /** Returns whether the game has ended. */
  public boolean isOver() { return finished; }

  // ── Round lifecycle ──────────────────────────────────────────────────────────

  private BattleRound openRound() {
    String letter = progression.current();
    int    tier   = LetterDifficulty.tierOf(letter);
    int    n      = completedRounds.size() + 1;
    BattleRound round = new BattleRound(n, letter, tier);
    eventListener.onRoundOpened(round, getActivePlayers());
    return round;
  }

  private void closeRound() {
    List<String> eliminatedThisRound = currentRound.getFailedPlayers();
    eventListener.onRoundClosed(currentRound, eliminatedThisRound);

    completedRounds.add(currentRound);
    progression.advance();

    List<String> active = getActivePlayers();

    if (active.size() == 1) {
      declareWinners(active);
      return;
    }
    if (active.isEmpty()) {
      int max = players.values().stream()
          .mapToInt(BattlePlayerState::getRoundsCleared).max().orElse(0);
      List<String> joint = players.values().stream()
          .filter(p -> p.getRoundsCleared() == max)
          .map(BattlePlayerState::getPlayerId)
          .collect(Collectors.toList());
      declareWinners(joint);
      return;
    }
    if (progression.isExhausted()) {
      declareWinners(active);
      return;
    }

    currentRound = openRound();
  }

  private void declareWinners(List<String> winnerIds) {
    this.winners      = new ArrayList<>(winnerIds);
    this.finished     = true;
    this.currentRound = null;
    eventListener.onWinnersDeclared(winners);
  }

  // ── Result ───────────────────────────────────────────────────────────────────

  private BattleResult buildResult() {
    List<String> ranked = players.values().stream()
        .sorted(Comparator.comparingInt(BattlePlayerState::getRoundsCleared).reversed())
        .map(BattlePlayerState::getPlayerId)
        .collect(Collectors.toList());

    Map<String, Integer> cleared = new LinkedHashMap<>();
    players.values().forEach(p -> cleared.put(p.getPlayerId(), p.getRoundsCleared()));

    return new BattleResult(winners, ranked, completedRounds, cleared, finished);
  }

  // ── Guard ────────────────────────────────────────────────────────────────────

  private BattlePlayerState requireActivePlayer(String playerId) {
    if (!players.containsKey(playerId)) {
      throw new IllegalArgumentException("Unknown player: " + playerId);
    }
    BattlePlayerState p = players.get(playerId);
    if (p.isEliminated()) {
      throw new IllegalArgumentException("Player already eliminated: " + playerId);
    }
    return p;
  }
}
