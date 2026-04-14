package aslframework.game.progression;

import aslframework.recognition.GestureLibrary;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for letter progressions that share cursor management.
 *
 * <p>Subclasses provide the ordered letter list via {@link #buildLetterList};
 * this class handles all cursor state ({@code currentIndex}, {@link #advance()},
 * {@link #isExhausted()}, etc.) so subclasses contain only ordering logic.
 */
public abstract class AbstractLetterProgression implements LetterProgression {

  /** The ordered list of letters available in the library, built at construction. */
  protected final List<String> letters;
  private int index = 0;

  /**
   * Constructs the progression by filtering the subclass-supplied ordered list
   * down to only letters that are present in the given library.
   *
   * @param library the loaded gesture library
   */
  protected AbstractLetterProgression(GestureLibrary library) {
    this.letters = Collections.unmodifiableList(
        buildLetterList().stream()
            .filter(l -> library.getGestureVariants(l) != null)
            .collect(Collectors.toList())
    );
  }

  /**
   * Supplies the desired letter ordering. Subclasses return the full A–Z list
   * in their preferred order; the constructor filters to available letters.
   *
   * @return ordered list of letters (A–Z, may include duplicates only if intentional)
   */
  protected abstract List<String> buildLetterList();

  // ── LetterProgression ────────────────────────────────────────────────────────

  @Override
  public String current() {
    return isExhausted() ? null : letters.get(index);
  }

  @Override
  public void advance() {
    if (!isExhausted()) index++;
  }

  @Override
  public boolean hasNext() {
    return index + 1 < letters.size();
  }

  @Override
  public boolean isExhausted() {
    return index >= letters.size();
  }

  @Override
  public int totalLetters() {
    return letters.size();
  }

  @Override
  public int currentIndex() {
    return index;
  }

  @Override
  public List<String> remaining() {
    if (isExhausted()) return List.of();
    return Collections.unmodifiableList(letters.subList(index, letters.size()));
  }
}
