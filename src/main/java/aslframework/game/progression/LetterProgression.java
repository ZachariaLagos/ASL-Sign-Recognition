package aslframework.game.progression;

import java.util.List;

/**
 * Defines the sequence of ASL letters presented during a game session.
 *
 * <p>Each session injects its own progression strategy:
 * <ul>
 *   <li>{@link SequentialProgression} — A → Z order for practice mode</li>
 *   <li>{@link DifficultyProgression} — easiest → hardest tier for battle mode</li>
 * </ul>
 *
 * <p>The progression acts as a stateful cursor. Call {@link #advance()} after
 * a letter is successfully cleared to move to the next one.
 */
public interface LetterProgression {

  /**
   * Returns the current target letter.
   *
   * @return current letter, or {@code null} if the progression is exhausted
   */
  String current();

  /**
   * Advances to the next letter in the sequence.
   * Has no effect if the progression is already exhausted.
   */
  void advance();

  /**
   * Returns whether there are more letters after the current one.
   *
   * @return {@code true} if {@link #advance()} would move to a valid letter
   */
  boolean hasNext();

  /**
   * Returns whether the progression has been fully exhausted
   * (i.e. {@link #current()} would return {@code null}).
   *
   * @return {@code true} if all letters have been played
   */
  boolean isExhausted();

  /**
   * Returns the total number of letters in this progression (including
   * already-played ones).
   *
   * @return total letter count
   */
  int totalLetters();

  /**
   * Returns the 0-based index of the current letter.
   *
   * @return current index
   */
  int currentIndex();

  /**
   * Returns the remaining letters (current + future), in order.
   *
   * @return unmodifiable list of remaining letters
   */
  List<String> remaining();
}
