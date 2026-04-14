package aslframework.game.session;

import aslframework.game.round.BattleRound;
import aslframework.game.result.BattleResult;
import aslframework.game.result.GameResult;
import aslframework.game.result.PracticeResult;
import aslframework.persistence.AttemptRecord;

import java.util.List;

/**
 * Console-printing implementation of {@link GameEventListener}.
 *
 * <p>Produces the same log lines that were previously scattered inside
 * {@link PracticeSession} and {@link BattleSession}, now consolidated here.
 * Inject this listener for development/CLI use; inject a silent or UI-aware
 * listener in production.
 */
public class ConsoleGameEventListener extends NoOpGameEventListener {

  @Override
  public void onAttempt(String letter, AttemptRecord record, int streak) {
    if (record.isPassed()) {
      System.out.printf("  %-2s  HIT   streak=%d  conf=%.1f%%%n",
          letter, streak, record.getAccuracy() * 100);
    } else {
      System.out.printf("  %-2s  MISS  conf=%.1f%%%n",
          letter, record.getAccuracy() * 100);
    }
  }

  @Override
  public void onLetterCleared(String letter, int letterScore, int totalScore) {
    System.out.printf("  %-2s  CLEARED  letter_score=%d  total=%d%n",
        letter, letterScore, totalScore);
  }

  @Override
  public void onRoundOpened(BattleRound round, List<String> activePlayers) {
    System.out.printf("%n[Battle] ══ Round %d ══  Letter: %s  (tier %d)  active=%d%n",
        round.getRoundNumber(), round.getTargetLetter(),
        round.getDifficultyTier(), activePlayers.size());
  }

  @Override
  public void onRoundClosed(BattleRound round, List<String> eliminated) {
    System.out.printf("[Battle] Round %d closed — passed: %s  eliminated: %s%n",
        round.getRoundNumber(), round.getPassedPlayers(), eliminated);
  }

  @Override
  public void onPlayerEliminated(String playerId, int roundCleared) {
    System.out.printf("[Battle] %-12s  eliminated after %d round(s)%n",
        playerId, roundCleared);
  }

  @Override
  public void onWinnersDeclared(List<String> winners) {
    if (winners.size() == 1) {
      System.out.printf("%n[Battle] Winner: %s%n", winners.get(0));
    } else {
      System.out.printf("%n[Battle] Joint winners: %s%n", winners);
    }
  }

  @Override
  public void onSessionFinished(GameResult result) {
    if (result instanceof PracticeResult) {
      PracticeResult pr = (PracticeResult) result;
      System.out.printf("[Practice] Done — cleared %d/%d  score=%d%n",
          pr.getLettersCleared(), pr.getTotalLetters(), pr.getTotalScore());
    } else if (result instanceof BattleResult) {
      BattleResult br = (BattleResult) result;
      System.out.printf("[Battle] Done — %d rounds  winner(s)=%s%n",
          br.getTotalRounds(), br.getWinners());
    }
  }
}
