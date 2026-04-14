package aslframework.game;

import java.util.List;

/**
 * Defines the difficulty ordering of ASL letters based on hand pose complexity.
 *
 * <p>Difficulty is determined by:
 * <ul>
 *   <li>Number of fingers extended / curled</li>
 *   <li>Finger independence required (e.g. R requires crossing)</li>
 *   <li>Similarity to other letters (e.g. M/N/T are easily confused)</li>
 *   <li>Fine motor precision needed</li>
 * </ul>
 *
 * <p>Used by {@link BattleRound} to advance the challenge letter each round,
 * and by {@link PracticeSession} to optionally gate progression.
 */
public enum LetterDifficulty {

  // ── Tier 1: Open / simple fist shapes ─────────────────────────────────────
  A(1), B(1), C(1), O(1), S(1),

  // ── Tier 2: Straightforward finger extensions ──────────────────────────────
  D(2), E(2), F(2), L(2), W(2),

  // ── Tier 3: Multi-finger coordination ─────────────────────────────────────
  G(3), H(3), I(3), K(3), U(3), V(3),

  // ── Tier 4: Precision positioning ─────────────────────────────────────────
  N(4), M(4), P(4), Q(4), T(4), Y(4),

  // ── Tier 5: Complex / easily confused shapes ──────────────────────────────
  J(5), R(5), X(5), Z(5);

  private final int tier;

  LetterDifficulty(int tier) {
    this.tier = tier;
  }

  public int getTier() {
    return tier;
  }

  /**
   * Returns the ordered list of letters from easiest to hardest.
   * Within the same tier, letters are ordered alphabetically.
   */
  public static List<String> orderedLetters() {
    return java.util.Arrays.stream(values())
        .sorted(java.util.Comparator
            .comparingInt(LetterDifficulty::getTier)
            .thenComparing(Enum::name))
        .map(Enum::name)
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * Returns the difficulty tier for a given letter string.
   *
   * @param letter single uppercase letter A-Z
   * @return tier 1-5, or 3 as default if letter not found
   */
  public static int tierOf(String letter) {
    try {
      return LetterDifficulty.valueOf(letter.toUpperCase()).getTier();
    } catch (IllegalArgumentException e) {
      return 3;
    }
  }
}
