package aslframework.game;

/**
 * Enumerates the two supported game modes in the ASL learning platform.
 */
public enum GameMode {

  /**
   * Single-player practice mode.
   * The player works through letters A-Z in order, advancing only
   * when the current letter is recognized successfully.
   */
  PRACTICE,

  /**
   * Multi-player elimination battle mode.
   * All active players attempt the same letter each round.
   * Players who fail are eliminated. The letter difficulty increases
   * each round following the {@link LetterDifficulty} ordering.
   * Last player standing wins.
   */
  BATTLE
}
