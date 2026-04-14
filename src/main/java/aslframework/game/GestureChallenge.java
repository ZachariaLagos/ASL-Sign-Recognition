package aslframework.game;

import aslframework.model.GestureDefinition;
import java.util.List;

/**
 * Represents a single ASL gesture challenge presented to the user during a learning session.
 * Contains the target gesture variants to match against and the difficulty level of the challenge.
 */
public class GestureChallenge {
  private List<GestureDefinition> target;
  private int difficultyLevel;

  /**
   * Constructs a GestureChallenge with the given target gesture variants and difficulty level.
   *
   * @param target          the list of gesture variants representing the target ASL letter
   * @param difficultyLevel the difficulty level of this challenge
   */
  public GestureChallenge(List<GestureDefinition> target, int difficultyLevel) {
    this.target = target;
    this.difficultyLevel = difficultyLevel;
  }

  /**
   * Returns the list of gesture variants for this challenge.
   *
   * @return list of GestureDefinition variants
   */
  public List<GestureDefinition> getTarget() {
    return target;
  }

  /**
   * Returns the difficulty level of this challenge.
   *
   * @return difficulty level
   */
  public int getDifficultyLevel() {
    return difficultyLevel;
  }
}