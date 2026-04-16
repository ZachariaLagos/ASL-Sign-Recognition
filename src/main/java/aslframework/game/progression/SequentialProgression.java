package aslframework.game.progression;

import aslframework.recognition.GestureLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential progression through ASL letters A → Z.
 *
 * <p>Used by practice mode to present letters in alphabetical order.
 * Filters to only letters with loaded reference gestures in the library.
 */
public class SequentialProgression extends AbstractLetterProgression {

  /**
   * Constructs a sequential A→Z progression.
   *
   * @param library the loaded gesture library; only letters with variants are included
   */
  public SequentialProgression(GestureLibrary library) {
    super(library);
  }

  /**
   * Provides the A–Z alphabet in order.
   * The parent's constructor filters this to only available letters.
   *
   * @return the letters A through Z
   */
  @Override
  protected List<String> buildLetterList() {
    List<String> result = new ArrayList<>();
    for (char c = 'A'; c <= 'Z'; c++) {
      result.add(String.valueOf(c));
    }
    return result;
  }
}
