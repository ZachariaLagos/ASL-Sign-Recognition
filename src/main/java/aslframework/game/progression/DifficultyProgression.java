package aslframework.game.progression;

import aslframework.game.LetterDifficulty;
import aslframework.recognition.GestureLibrary;

import java.util.List;

/**
 * Letter progression ordered by difficulty tier (easiest → hardest).
 * Used by {@link aslframework.game.session.BattleSession}.
 *
 * <p>Within the same tier, letters are sorted alphabetically for determinism.
 * Letters not present in the gesture library are silently skipped.
 */
public class DifficultyProgression extends AbstractLetterProgression {

  /**
   * Constructs a {@code DifficultyProgression}, filtering to letters
   * present in the given library.
   *
   * @param library the loaded gesture library
   */
  public DifficultyProgression(GestureLibrary library) {
    super(library);
  }

  @Override
  protected List<String> buildLetterList() {
    return LetterDifficulty.orderedLetters();
  }
}
